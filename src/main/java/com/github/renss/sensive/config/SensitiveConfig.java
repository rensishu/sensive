package com.github.renss.sensive.config;

import com.github.renss.sensive.RuleType;
import com.github.renss.sensive.config.model.CustomRule;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 线程安全的单例，持有当前的脱敏配置。
 * 使用双重检查锁定（DCL）进行初始化，
 * ConcurrentHashMap 提供无锁读取并发。
 *
 * @author renss
 * @version V1.2.0
 * @since 1.0.0 2026/6/2
 */
public class SensitiveConfig {

    private static volatile SensitiveConfig INSTANCE;

    // ConcurrentHashMap for lock-free reads at extreme concurrency.
    // Writes only happen during reload() (full replacement) or
    // registerKeyword() (single-key insertion).
    private final ConcurrentHashMap<String, RuleType> keywords;
    private final ConcurrentHashMap<String, CustomRule> customRules;
    private volatile Set<String> excludes;
    private volatile boolean enabled;
    private volatile TextPatternConfig textPattern;

    private SensitiveConfig(ConfigLoader.ConfigHolder holder) {
        // Pre-size CHM with known capacity from holder
        int kwSize = holder.keywords.size();
        int crSize = holder.customRules != null ? holder.customRules.size() : 0;
        this.keywords = new ConcurrentHashMap<String, RuleType>(
                kwSize > 0 ? kwSize : 64, 0.75f, 1);
        this.keywords.putAll(holder.keywords);
        this.customRules = new ConcurrentHashMap<String, CustomRule>(
                crSize > 0 ? crSize : 16, 0.75f, 1);
        if (holder.customRules != null) {
            this.customRules.putAll(holder.customRules);
        }
        this.excludes = holder.excludes != null ? holder.excludes : Collections.<String>emptySet();
        this.enabled = holder.enabled;
        this.textPattern = holder.textPattern != null ? holder.textPattern
                : new TextPatternConfig(false, TextPatternConfig.defaultPatterns());
    }

    /**
     * 获取SensitiveConfig的单例实例，首次调用时自动加载配置。
     *
     * @return 单例实例
     */
    public static SensitiveConfig getInstance() {
        if (INSTANCE == null) {
            synchronized (SensitiveConfig.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SensitiveConfig(ConfigLoader.load());
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 从外部源重新加载配置（classpath sensitive.yml）。
     */
    public static void reload() {
        synchronized (SensitiveConfig.class) {
            INSTANCE = new SensitiveConfig(ConfigLoader.load());
        }
    }

    /**
     * 使用外部 Properties 重新加载配置，覆盖 classpath 文件。
     *
     * <p>用于 Spring Environment 或 Apollo/Nacos 等配置中心场景。
     * 先合并内置默认值，再应用 Properties 中的 sensitive.* 配置。
     *
     * @param props 包含 sensitive.* 前缀的 Properties
     */
    public static void reload(Properties props) {
        synchronized (SensitiveConfig.class) {
            INSTANCE = new SensitiveConfig(ConfigLoader.loadFromProperties(props));
        }
    }

    /**
     * 检查脱敏功能是否已启用。
     *
     * @return 如果启用则返回true
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 根据关键字查找对应的内置规则类型（公共 API，大小写不敏感）。
     *
     * <p>在引擎热路径中，优先使用 {@link #lookupKeywordLower(String)}
     * 并传入预小写化的关键字，以避免重复的字符串分配。
     *
     * @param keyword 关键字
     * @return 对应的RuleType，如果未找到则返回null
     */
    public RuleType lookupKeyword(String keyword) {
        if (keyword == null) return null;
        // CHM.get() is lock-free — no CAS contention on the read path
        return keywords.get(keyword.toLowerCase());
    }

    /**
     * 使用预小写化的关键字查找内置规则类型（引擎内部使用，零分配）。
     *
     * @param lowerKeyword 已小写化的关键字
     * @return 对应的RuleType，如果未找到则返回null
     */
    public RuleType lookupKeywordLower(String lowerKeyword) {
        if (lowerKeyword == null) return null;
        return keywords.get(lowerKeyword);
    }

    /**
     * 根据关键字查找对应的自定义规则（公共 API，大小写不敏感）。
     *
     * @param keyword 关键字
     * @return 对应的CustomRule，如果未找到则返回null
     */
    public CustomRule lookupCustomRule(String keyword) {
        if (keyword == null) return null;
        return customRules.get(keyword.toLowerCase());
    }

    /**
     * 使用预小写化的关键字查找自定义规则（引擎内部使用，零分配）。
     *
     * @param lowerKeyword 已小写化的关键字
     * @return 对应的CustomRule，如果未找到则返回null
     */
    public CustomRule lookupCustomRuleLower(String lowerKeyword) {
        if (lowerKeyword == null) return null;
        return customRules.get(lowerKeyword);
    }

    /**
     * 在运行时注册或覆盖关键字到规则的映射。
     *
     * <p>线程安全：CHM.put()是原子的，无需外部锁。
     *
     * @param keyword  关键字
     * @param ruleType 规则类型
     */
    public void registerKeyword(String keyword, RuleType ruleType) {
        if (keyword == null || ruleType == null) return;
        keywords.put(keyword.toLowerCase(), ruleType);
    }

    /**
     * 获取当前所有的关键字映射。
     *
     * @return 关键字到RuleType的映射表（快照）
     */
    public Map<String, RuleType> getKeywords() {
        // CHM always reflects latest state; volatile field ref ensures
        // reload() visibility. Return a snapshot for safety.
        return new LinkedHashMap<String, RuleType>(keywords);
    }

    /**
     * 获取当前所有的自定义规则映射。
     *
     * @return 关键字到CustomRule的映射表（快照）
     */
    public Map<String, CustomRule> getCustomRules() {
        return new LinkedHashMap<String, CustomRule>(customRules);
    }

    /**
     * 获取文本模式配置。
     *
     * @return 文本模式配置对象
     */
    public TextPatternConfig getTextPattern() {
        return textPattern;
    }

    /**
     * 文本模式匹配配置。
     *
     * <p>两级开关：
     * <ul>
     *   <li>{@link #isEnabled()} — 全局开关，控制 {@code mask()} 是否启用文本模式扫描</li>
     *   <li>{@link #isSqlEnabled()} — SQL 参数日志独立开关，控制 {@code maskEnhanced()} 是否启用</li>
     * </ul>
     *
     * @author renss
     * @version V1.3.0
     * @since 1.0.0 2026/6/2
     */
    public static class TextPatternConfig {
        /** 默认启用的模式：手机号、身份证号、银行卡号 */
        private static final Set<String> DEFAULT_PATTERNS;
        static {
            Set<String> p = new HashSet<String>();
            p.add("phone");
            p.add("idcard");
            p.add("bankcard");
            DEFAULT_PATTERNS = Collections.unmodifiableSet(p);
        }

        /** 全局开关：是否对所有日志（mask）启用文本模式扫描 */
        private final boolean enabled;
        /** SQL 参数日志独立开关：是否对 maskEnhanced() 启用（默认 true） */
        private final boolean sql;
        /** 启用的模式名称集合 */
        private final Set<String> patterns;

        /**
         * 构造文本模式配置。
         *
         * @param enabled  全局开关
         * @param patterns 启用的模式集合
         */
        public TextPatternConfig(boolean enabled, Set<String> patterns) {
            this(enabled, true, patterns);
        }

        /**
         * 构造文本模式配置（含 SQL 独立开关）。
         *
         * @param enabled  全局开关
         * @param sql      SQL 参数日志独立开关
         * @param patterns 启用的模式集合
         */
        public TextPatternConfig(boolean enabled, boolean sql, Set<String> patterns) {
            this.enabled = enabled;
            this.sql = sql;
            this.patterns = patterns != null ? patterns : Collections.<String>emptySet();
        }

        /** @return 全局开关：是否对所有日志启用文本模式扫描 */
        public boolean isEnabled() { return enabled; }

        /** @return SQL 独立开关：是否对 maskEnhanced() 启用文本模式扫描 */
        public boolean isSqlEnabled() { return sql; }

        /**
         * 获取启用的模式名称集合。
         *
         * @return 模式名称集合
         */
        public Set<String> getPatterns() { return patterns; }

        /**
         * 获取默认的文本模式集合，包含手机号、身份证号和银行卡号。
         *
         * @return 默认模式集合
         */
        public static Set<String> defaultPatterns() {
            return DEFAULT_PATTERNS;
        }
    }
}
