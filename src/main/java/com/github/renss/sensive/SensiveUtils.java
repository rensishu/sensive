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
 * <h3>使用示例</h3>
 * <pre>
 * // 自动脱敏所有敏感信息（仅 KV 模式匹配）
 * String safe = SensiveUtils.mask("phone=13812345678, name=张三, idcard=310101199001011234");
 *
 * // 脱敏 SQL 输出（KV 匹配 + 文本模式扫描）
 * String safeSql = SensiveUtils.maskSql("==&gt; Parameters: 13812345678(String)");
 *
 * // 脱敏指定关键字
 * String safe2 = SensiveUtils.mask("phone=13812345678", "phone");
 *
 * // 脱敏单个值
 * String safe3 = SensiveUtils.maskValue("13812345678", RuleType.PHONE_MASK);
 *
 * // 运行时注册关键字
 * SensiveUtils.registerKeyword("myphone", RuleType.PHONE_MASK);
 *
 * // 重新加载配置（无需重启）
 * SensiveUtils.reloadConfig();
 * </pre>
 *
 * @author renss
 * @version V1.0.0
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
                    return config.lookupKeyword(keyword);
                }
            },
            config.getCustomRules()
        );

        return new MaskEngine(matcher, executor, config.getTextPattern());
    }

    /**
     * 对文本中的敏感信息执行自动脱敏（仅 KV 模式匹配，不启用文本模式扫描）。
     *
     * <p>使用所有已配置的关键字扫描文本，匹配 key=value 等格式并脱敏。
     * 如需同时启用数字文本模式扫描（如 MyBatis SQL 参数），请使用 {@link #maskSql(String)}。
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
            // fail-safe: return original text on error
            return text;
        }
    }

    /**
     * 对 SQL 参数输出执行脱敏（KV 匹配 + 文本模式扫描双引擎）。
     *
     * <p>除 KV 模式匹配外，额外扫描未覆盖区域的数字序列（手机号、身份证号、银行卡号）。
     * 适用于 MyBatis SQL 参数日志，如 "Parameters: 13812345678(String)"。
     *
     * @param text SQL 参数原始文本
     * @return 脱敏后的文本
     */
    public static String maskSql(String text) {
        if (text == null || text.isEmpty()) return text;
        if (!SensitiveConfig.getInstance().isEnabled()) return text;

        try {
            return getEngine().maskSql(text);
        } catch (Exception e) {
            // fail-safe: return original text on error
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
}
