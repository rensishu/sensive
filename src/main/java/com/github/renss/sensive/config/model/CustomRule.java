package com.github.renss.sensive.config.model;

import com.github.renss.sensive.RuleType;

import com.google.re2j.Pattern;

/**
 * 自定义脱敏规则定义。
 * 支持内置类型引用（builtin）和自定义正则表达式模式（regex）两种方式。
 *
 * <p>正则引擎使用 Google RE2J，保证线性时间匹配 O(n)，
 * 无 catastrophic backtracking 风险（ReDoS 免疫）。
 * 模式在首次使用或配置规范化时预编译。
 *
 * @author renss
 * @version V1.3.0
 * @since 1.0.0 2026/6/2
 */
public class CustomRule {

    /** 规范化的规则类型枚举（避免每次 apply 时进行 equalsIgnoreCase 比较） */
    enum RuleKind {
        BUILTIN,
        REGEX
    }

    /** 规则名称 */
    private String name;
    /** 关联的关键字 */
    private String keyword;
    /** 规则类型字符串：builtin 或 regex */
    private String type;
    /** 内置规则类型名称（当type=builtin时使用） */
    private String builtin;
    /** 正则表达式模式（当type=regex时使用） */
    private String pattern;
    /** 正则替换字符串（当type=regex时使用） */
    private String replacement;

    /** 规范化的规则类型（初始化时设置，避免热路径进行比较） */
    private RuleKind kind;
    /** 预编译的正则 Pattern（regex 类型时使用，惰性编译） */
    private volatile Pattern compiledPattern;

    public CustomRule() {}

    /** @return 规则名称 */
    public String getName() { return name; }
    /** @param name 规则名称 */
    public void setName(String name) { this.name = name; }
    /** @return 关联的关键字 */
    public String getKeyword() { return keyword; }
    /** @param keyword 关联的关键字 */
    public void setKeyword(String keyword) { this.keyword = keyword; }
    /** @return 规则类型 */
    public String getType() { return type; }
    /** @param type 规则类型 */
    public void setType(String type) { this.type = type; }
    /** @return 内置规则类型名称 */
    public String getBuiltin() { return builtin; }
    /** @param builtin 内置规则类型名称 */
    public void setBuiltin(String builtin) { this.builtin = builtin; }
    /** @return 正则表达式模式 */
    public String getPattern() { return pattern; }
    /** @param pattern 正则表达式模式 */
    public void setPattern(String pattern) { this.pattern = pattern; }
    /** @return 正则替换字符串 */
    public String getReplacement() { return replacement; }
    /** @param replacement 正则替换字符串 */
    public void setReplacement(String replacement) { this.replacement = replacement; }

    /**
     * 配置加载后调用，规范化规则类型并在可能时预编译正则表达式。
     *
     * <p>由 {@link com.github.renss.sensive.config.ConfigLoader} 在解析完成时调用。
     */
    public void normalize() {
        if (type != null) {
            if ("regex".equalsIgnoreCase(type) && pattern != null) {
                kind = RuleKind.REGEX;
                // Eagerly compile to fail-fast on bad patterns
                try {
                    compiledPattern = Pattern.compile(pattern);
                } catch (Exception ignored) {
                    // Invalid pattern — fall through to no-op in apply()
                }
            } else if ("builtin".equalsIgnoreCase(type) && builtin != null) {
                kind = RuleKind.BUILTIN;
            }
        }
    }

    /**
     * 将此规则应用于某个值，根据规则类型执行对应的脱敏操作。
     *
     * @param value 需要脱敏的值
     * @return 脱敏后的值
     */
    public String apply(String value) {
        if (value == null) return null;

        // Fast path: use normalized kind (set by normalize() in ConfigLoader)
        if (kind != null) {
            if (kind == RuleKind.REGEX) {
                Pattern p = compiledPattern;
                if (p != null) {
                    return p.matcher(value).replaceAll(replacement != null ? replacement : "****");
                }
            } else if (kind == RuleKind.BUILTIN) {
                RuleType ruleType = RuleType.fromName(builtin);
                if (ruleType != null) return ruleType.apply(value);
            }
            return value;
        }

        // Fallback: programmatic CustomRule without normalize() call
        if ("regex".equalsIgnoreCase(type) && pattern != null) {
            try {
                Pattern p = compiledPattern;
                if (p == null) {
                    p = Pattern.compile(pattern);
                    compiledPattern = p;
                }
                return p.matcher(value).replaceAll(replacement != null ? replacement : "****");
            } catch (Exception ignored) {
                // Invalid pattern — return original value (fail-safe)
                return value;
            }
        }
        if ("builtin".equalsIgnoreCase(type) && builtin != null) {
            RuleType ruleType = RuleType.fromName(builtin);
            if (ruleType != null) return ruleType.apply(value);
        }
        return value;
    }
}
