package com.github.renss.sensive.config;

import com.github.renss.sensive.RuleType;
import com.github.renss.sensive.config.model.CustomRule;

import java.util.*;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 线程安全的单例，持有当前的脱敏配置。
 * 使用双重检查锁定（DCL）进行初始化，读写锁保护运行时配置更新。
 *
 * @author renss
 * @version V1.0.0
 * @since 1.0.0 2026/6/2
 */
public class SensitiveConfig {

    private static volatile SensitiveConfig INSTANCE;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile Map<String, RuleType> keywords;
    private volatile Map<String, CustomRule> customRules;
    private volatile Set<String> excludes;
    private volatile boolean enabled;
    private volatile TextPatternConfig textPattern;

    private SensitiveConfig(ConfigLoader.ConfigHolder holder) {
        this.keywords = holder.keywords;
        this.customRules = holder.customRules;
        this.excludes = holder.excludes;
        this.enabled = holder.enabled;
        this.textPattern = holder.textPattern;
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
     * 根据关键字查找对应的内置规则类型。
     *
     * @param keyword 关键字
     * @return 对应的RuleType，如果未找到则返回null
     */
    public RuleType lookupKeyword(String keyword) {
        if (keyword == null) return null;
        lock.readLock().lock();
        try {
            return keywords.get(keyword.toLowerCase());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 根据关键字查找对应的自定义规则。
     *
     * @param keyword 关键字
     * @return 对应的CustomRule，如果未找到则返回null
     */
    public CustomRule lookupCustomRule(String keyword) {
        if (keyword == null) return null;
        lock.readLock().lock();
        try {
            return customRules.get(keyword.toLowerCase());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 在运行时注册或覆盖关键字到规则的映射。
     *
     * @param keyword  关键字
     * @param ruleType 规则类型
     */
    public void registerKeyword(String keyword, RuleType ruleType) {
        if (keyword == null || ruleType == null) return;
        lock.writeLock().lock();
        try {
            keywords.put(keyword.toLowerCase(), ruleType);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取当前所有的关键字映射。
     *
     * @return 关键字到RuleType的映射表
     */
    public Map<String, RuleType> getKeywords() {
        lock.readLock().lock();
        try {
            return keywords;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取当前所有的自定义规则映射。
     *
     * @return 关键字到CustomRule的映射表
     */
    public Map<String, CustomRule> getCustomRules() {
        lock.readLock().lock();
        try {
            return customRules;
        } finally {
            lock.readLock().unlock();
        }
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
     * 全文数字模式匹配的配置（方案B）。
     * 仅在使用maskSql()时生效，即MyBatis SQL参数输出场景。
     *
     * @author renss
     * @version V1.0.0
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

        /** 是否启用文本模式匹配 */
        private final boolean enabled;
        /** 启用的模式名称集合 */
        private final Set<String> patterns;

        /**
         * 构造文本模式配置。
         *
         * @param enabled  是否启用
         * @param patterns 启用的模式集合
         */
        public TextPatternConfig(boolean enabled, Set<String> patterns) {
            this.enabled = enabled;
            this.patterns = patterns != null ? patterns : Collections.<String>emptySet();
        }

        /**
         * 检查文本模式匹配是否已启用。
         *
         * @return 如果启用则返回true
         */
        public boolean isEnabled() { return enabled; }

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
