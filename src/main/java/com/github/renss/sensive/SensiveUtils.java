package com.github.renss.sensive;

import com.github.renss.sensive.config.SensitiveConfig;
import com.github.renss.sensive.engine.KeywordMatcher;
import com.github.renss.sensive.engine.MaskEngine;
import com.github.renss.sensive.engine.RuleExecutor;

/**
 * 日志脱敏公共 API 入口。
 *
 * <p>提供文本脱敏和单值脱敏的静态方法，所有方法线程安全。
 *
 * <h3>两种脱敏模式</h3>
 * <ul>
 *   <li>{@link #mask(String)} — 仅 KV 模式匹配，适用于有 key=value 结构的通用业务日志</li>
 *   <li>{@link #maskEnhanced(String)} — KV 匹配 + 文本模式扫描，额外识别裸露的手机号、身份证号、银行卡号</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 * // 基础脱敏：仅 KV 模式匹配（通用日志）
 * String safe = SensiveUtils.mask("phone=13812345678, name=张三, idcard=310101199001011234");
 *
 * // 增强脱敏：KV 匹配 + 文本模式扫描（含裸露数字的文本）
 * String safe2 = SensiveUtils.maskEnhanced("==&gt; Parameters: 13812345678(String)");
 *
 * // 脱敏指定关键字
 * String safe3 = SensiveUtils.mask("phone=13812345678", "phone");
 *
 * // 脱敏单个值
 * String safe4 = SensiveUtils.maskValue("13812345678", RuleType.PHONE_MASK);
 *
 * // 运行时注册关键字
 * SensiveUtils.registerKeyword("myphone", RuleType.PHONE_MASK);
 *
 * // 重新加载配置（无需重启）
 * SensiveUtils.reloadConfig();
 * </pre>
 *
 * @author renss
 * @version V1.2.0
 * @since 1.0.0 2026/6/2
 */
public final class SensiveUtils {

    private static volatile MaskEngine engine;

    private SensiveUtils() {}

    private static MaskEngine getEngine() {
        if (engine == null) {
            synchronized (SensiveUtils.class) {
                if (engine == null) {
                    engine = buildEngine();
                }
            }
        }
        return engine;
    }

    private static MaskEngine buildEngine() {
        SensitiveConfig config = SensitiveConfig.getInstance();

        KeywordMatcher matcher = new KeywordMatcher();
        for (String keyword : config.getKeywords().keySet()) {
            matcher.addKeyword(keyword);
        }

        RuleExecutor executor = new RuleExecutor(
            new RuleExecutor.RuleLookup() {
                @Override
                public RuleType lookup(String keyword) {
                    // 关键字已由 RuleExecutor.maskValue() 小写化
                    return config.lookupKeywordLower(keyword);
                }
            },
            config.getCustomRules()
        );

        return new MaskEngine(matcher, executor, config.getTextPattern());
    }

    /**
     * 对文本中的敏感信息执行基础脱敏（仅 KV 模式匹配）。
     *
     * <p>使用所有已配置的关键字扫描文本，匹配 key=value 等键值格式并脱敏。
     * 不启用文本模式扫描，裸露的数字序列（无 key 前缀的手机号等）不会被处理。
     * 如需同时扫描裸露数字，请使用 {@link #maskEnhanced(String)}。
     *
     * @param text 原始文本
     * @return 脱敏后的文本，null 或空字符串原样返回
     */
    public static String mask(String text) {
        if (text == null || text.isEmpty()) return text;
        if (!SensitiveConfig.getInstance().isEnabled()) return text;

        try {
            return getEngine().mask(text);
        } catch (Exception e) {
            // 容错模式：异常时返回原文，不影响正常日志输出
            return text;
        }
    }

    /**
     * 对文本执行增强脱敏（KV 匹配 + 文本模式扫描）。
     *
     * <p>在 KV 模式匹配的基础上，额外扫描未被覆盖区域的数字序列，
     * 识别裸露的手机号（11位 1[3-9] 开头）、身份证号（18位/17位+X）和
     * 银行卡号（16-19位连续数字），无需显式的 key=value 结构即可脱敏。
     *
     * <p>典型适用场景：
     * <ul>
     *   <li>MyBatis SQL 参数行：{@code Parameters: 13812345678(String)}</li>
     *   <li>JSON 数组中的裸值：{@code ["13812345678", "张三"]}</li>
     *   <li>其他无 key=value 结构但包含敏感数字的文本</li>
     * </ul>
     *
     * <p>文本模式扫描需在配置中启用 {@code sensitive.text-pattern.enabled: true}，
     * 默认为关闭状态，仅对本方法生效，{@link #mask(String)} 不受影响。
     *
     * @param text 原始文本
     * @return 脱敏后的文本，null 或空字符串原样返回
     * @since 1.2.0
     */
    public static String maskEnhanced(String text) {
        if (text == null || text.isEmpty()) return text;
        if (!SensitiveConfig.getInstance().isEnabled()) return text;

        try {
            return getEngine().maskEnhanced(text);
        } catch (Exception e) {
            // 容错模式：异常时返回原文，不影响正常日志输出
            return text;
        }
    }

    /**
     * 脱敏文本中指定关键字对应的值。
     *
     * @param text    原始文本
     * @param keyword 关键字
     * @return 脱敏后的文本
     */
    public static String mask(String text, String keyword) {
        if (text == null || keyword == null) return text;

        try {
            return getEngine().mask(text, keyword);
        } catch (Exception e) {
            return text;
        }
    }

    /**
     * 脱敏文本中指定关键字对应的值，使用指定的规则类型覆盖默认规则。
     *
     * @param text     原始文本
     * @param keyword  关键字
     * @param ruleType 规则类型（覆盖关键字默认规则）
     * @return 脱敏后的文本
     */
    public static String mask(String text, String keyword, RuleType ruleType) {
        if (text == null || keyword == null || ruleType == null) return text;

        try {
            return getEngine().mask(text, keyword, ruleType);
        } catch (Exception e) {
            return text;
        }
    }

    /**
     * 对单个值直接执行脱敏。
     *
     * <p>值为 null、空字符串或字面值 "null" 时原样返回，不做脱敏。
     *
     * @param value    待脱敏的原始值
     * @param ruleType 脱敏规则类型
     * @return 脱敏后的值
     */
    public static String maskValue(String value, RuleType ruleType) {
        if (value == null || value.isEmpty() || "null".equalsIgnoreCase(value) || ruleType == null) return value;

        try {
            return getEngine().maskValue(value, ruleType);
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * 重新加载外部配置，无需重启应用。
     *
     * <p>从 classpath 重新读取 sensitive.yml 或 sensitive.properties，合并后重建引擎。
     */
    public static void reloadConfig() {
        SensitiveConfig.reload();
        rebuildEngine();
    }

    /**
     * 不重新加载配置，仅从当前 SensitiveConfig 重建引擎。
     *
     * <p>用于 Spring 自动配置调用 {@code SensitiveConfig.reload(Properties)} 后同步引擎。
     */
    public static void refreshEngine() {
        rebuildEngine();
    }

    private static void rebuildEngine() {
        synchronized (SensiveUtils.class) {
            engine = buildEngine();
        }
    }

    /**
     * 在运行时注册自定义关键字到规则的映射，注册后重建引擎立即生效。
     *
     * @param keyword  关键字
     * @param ruleType 规则类型
     */
    public static void registerKeyword(String keyword, RuleType ruleType) {
        SensitiveConfig.getInstance().registerKeyword(keyword, ruleType);
        rebuildEngine();
    }

    // ============================================================
    // 向后兼容（已弃用）
    // ============================================================

    /**
     * 对 SQL 参数输出执行脱敏（KV 匹配 + 文本模式扫描双引擎）。
     *
     * @deprecated 自 v1.2.0 起更名为 {@link #maskEnhanced(String)}，方法名更准确地反映其功能
     *             （增强脱敏而非 SQL 专用）。本方法保留以兼容旧代码，直接委托给 maskEnhanced。
     * @param text SQL 参数原始文本
     * @return 脱敏后的文本
     */
    @Deprecated
    public static String maskSql(String text) {
        return maskEnhanced(text);
    }
}
