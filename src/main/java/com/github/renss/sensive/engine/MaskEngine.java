package com.github.renss.sensive.engine;

import com.github.renss.sensive.RuleType;
import com.github.renss.sensive.config.SensitiveConfig.TextPatternConfig;

import java.util.*;

/**
 * 核心脱敏引擎，串联关键字匹配、KV 解析和规则执行。
 *
 * <p>无状态且线程安全。提供两种脱敏模式：
 * <ul>
 *   <li>{@link #mask(String)} — 仅 KV 模式匹配，适用于通用业务日志</li>
 *   <li>{@link #maskEnhanced(String)} — KV 匹配 + 文本模式扫描，额外识别裸露数字序列</li>
 * </ul>
 *
 * @author renss
 * @version V1.2.0
 * @since 1.0.0 2026/6/2
 */
public class MaskEngine {

    /** Reusable comparator — avoids per-call anonymous Comparator allocation */
    private static final Comparator<MaskPosition> POSITION_COMPARATOR = new Comparator<MaskPosition>() {
        @Override
        public int compare(MaskPosition o1, MaskPosition o2) {
            return Integer.compare(o1.valueStart, o2.valueStart);
        }
    };

    private final KeywordMatcher matcher;
    private final RuleExecutor executor;
    private final TextPatternConfig textPatternConfig;

    public MaskEngine(KeywordMatcher matcher, RuleExecutor executor, TextPatternConfig textPatternConfig) {
        this.matcher = matcher;
        this.executor = executor;
        this.textPatternConfig = textPatternConfig != null ? textPatternConfig
                : new TextPatternConfig(false, Collections.<String>emptySet());
    }

    /**
     * 对文本执行脱敏（KV 模式匹配，可选文本模式扫描）。
     *
     * <p>当 {@code textPattern.enabled=true} 时，额外扫描未覆盖区域的数字序列
     * （手机号、身份证号、银行卡号）。
     *
     * @param text 原始文本
     * @return 脱敏后的文本
     */
    public String mask(String text) {
        if (text == null || text.isEmpty()) return text;

        List<MaskPosition> positions = KvStateMachine.scan(text, matcher);

        // 全局开关：textPattern.enabled=true 时对所有日志启用文本模式扫描
        if (textPatternConfig.isEnabled()) {
            List<MaskPosition> textPositions = TextPatternMatcher.scan(
                    text, positions, textPatternConfig.getPatterns());
            positions = mergePositions(positions, textPositions);
        }

        if (positions.isEmpty()) return text;
        return executor.mask(text, positions);
    }

    /**
     * 对文本执行增强脱敏（KV 匹配 + 文本模式扫描）。
     *
     * <p>在 KV 匹配的基础上，额外扫描未覆盖区域的数字序列（手机号、身份证号、银行卡号）。
     * 适用于含裸露敏感数字的文本，如 MyBatis SQL 参数行、JSON 数组等。
     *
     * <p>文本模式扫描由 {@code textPattern.sql} 独立控制（默认 true），
     * 与全局 {@code textPattern.enabled} 解耦。
     *
     * @param text 原始文本
     * @return 脱敏后的文本
     * @since 1.2.0
     */
    public String maskEnhanced(String text) {
        if (text == null || text.isEmpty()) return text;

        List<MaskPosition> positions = KvStateMachine.scan(text, matcher);

        // SQL 独立开关：textPattern.sql 控制 maskEnhanced() 的文本模式扫描
        if (textPatternConfig.isSqlEnabled()) {
            List<MaskPosition> textPositions = TextPatternMatcher.scan(
                    text, positions, textPatternConfig.getPatterns());
            positions = mergePositions(positions, textPositions);
        }

        if (positions.isEmpty()) return text;
        return executor.mask(text, positions);
    }

    /**
     * 对 SQL 参数输出执行双引擎脱敏（KV 匹配 + 文本模式扫描）。
     *
     * @deprecated 自 v1.2.0 起更名为 {@link #maskEnhanced(String)}。
     *             本方法保留以兼容旧代码，直接委托给 maskEnhanced。
     * @param text SQL 参数原始文本
     * @return 脱敏后的文本
     */
    @Deprecated
    public String maskSql(String text) {
        return maskEnhanced(text);
    }

    private static List<MaskPosition> mergePositions(List<MaskPosition> a, List<MaskPosition> b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        List<MaskPosition> merged = new ArrayList<MaskPosition>(a.size() + b.size());
        merged.addAll(a);
        merged.addAll(b);
        // Sort by valueStart — uses pre-allocated static comparator
        Collections.sort(merged, POSITION_COMPARATOR);
        // Deduplicate overlaps (KV positions take precedence since they're added first)
        return mergeOverlapping(merged);
    }

    private static List<MaskPosition> mergeOverlapping(List<MaskPosition> positions) {
        if (positions.size() <= 1) return positions;
        List<MaskPosition> result = new ArrayList<MaskPosition>(positions.size());
        MaskPosition current = positions.get(0);
        for (int i = 1; i < positions.size(); i++) {
            MaskPosition next = positions.get(i);
            if (next.valueStart <= current.valueEnd) {
                current = new MaskPosition(current.valueStart,
                        Math.max(current.valueEnd, next.valueEnd), current.keyword);
            } else {
                result.add(current);
                current = next;
            }
        }
        result.add(current);
        return result;
    }

    /**
     * 脱敏文本中指定关键字对应的值。
     *
     * @param text    原始文本
     * @param keyword 关键字
     * @return 脱敏后的文本
     */
    public String mask(String text, String keyword) {
        if (text == null || text.isEmpty() || keyword == null) return text;

        // Build a single-keyword matcher
        KeywordMatcher singleMatcher = new KeywordMatcher();
        singleMatcher.addKeyword(keyword);

        List<MaskPosition> positions = KvStateMachine.scan(text, singleMatcher);
        if (positions.isEmpty()) return text;

        return executor.mask(text, positions);
    }

    /**
     * 脱敏文本中指定关键字对应的值，使用指定的规则类型覆盖默认规则。
     *
     * @param text     原始文本
     * @param keyword  关键字
     * @param ruleType 规则类型
     * @return 脱敏后的文本
     */
    public String mask(String text, String keyword, final RuleType ruleType) {
        if (text == null || text.isEmpty() || keyword == null || ruleType == null) return text;

        KeywordMatcher singleMatcher = new KeywordMatcher();
        singleMatcher.addKeyword(keyword);

        List<MaskPosition> positions = KvStateMachine.scan(text, singleMatcher);
        if (positions.isEmpty()) return text;

        // Create a temporary executor with the specified rule override
        RuleExecutor tempExecutor = new RuleExecutor(new RuleExecutor.RuleLookup() {
            @Override
            public RuleType lookup(String kw) {
                return ruleType;
            }
        }, null);

        return tempExecutor.mask(text, positions);
    }

    /**
     * 对单个值直接执行脱敏。
     *
     * <p>值为 null、空字符串或字面值 "null" 时原样返回。
     *
     * @param value    待脱敏的原始值
     * @param ruleType 脱敏规则类型
     * @return 脱敏后的值
     */
    public String maskValue(String value, RuleType ruleType) {
        if (value == null || value.isEmpty() || "null".equalsIgnoreCase(value) || ruleType == null) return value;
        return ruleType.apply(value);
    }
}
