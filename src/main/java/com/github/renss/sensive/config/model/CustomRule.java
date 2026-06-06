package com.github.renss.sensive.config.model;

import com.github.renss.sensive.RuleType;

/**
 * 自定义脱敏规则定义。
 * 支持内置类型引用（builtin）和自定义正则表达式模式（regex）两种方式。
 *
 * @author renss
 * @version V1.0.0
 * @since 1.0.0 2026/6/2
 */
public class CustomRule {
    /** 规则名称 */
    private String name;
    /** 关联的关键字 */
    private String keyword;
    /** 规则类型：builtin（内置）或regex（正则） */
    private String type;
    /** 内置规则类型名称（当type=builtin时使用） */
    private String builtin;
    /** 正则表达式模式（当type=regex时使用） */
    private String pattern;
    /** 正则替换字符串（当type=regex时使用） */
    private String replacement;

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
     * 将此规则应用于某个值，根据规则类型执行对应的脱敏操作。
     *
     * @param value 需要脱敏的值
     * @return 脱敏后的值
     */
    public String apply(String value) {
        if (value == null) return null;
        if ("regex".equalsIgnoreCase(type) && pattern != null) {
            return value.replaceAll(pattern, replacement != null ? replacement : "****");
        }
        if ("builtin".equalsIgnoreCase(type) && builtin != null) {
            RuleType ruleType = RuleType.fromName(builtin);
            if (ruleType != null) return ruleType.apply(value);
        }
        return value;
    }
}
