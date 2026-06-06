package com.github.renss.sensive.engine;

import com.github.renss.sensive.RuleType;
import com.github.renss.sensive.config.model.CustomRule;

import java.util.HashMap;
import java.util.Map;

/**
 * 将脱敏规则应用于匹配到的值。
 * 支持内置的RuleType规则和自定义正则表达式规则。
 *
 * @author renss
 * @version V1.0.0
 * @since 1.0.0 2026/6/2
 */
public class RuleExecutor {

    private final RuleLookup ruleLookup;
    private final Map<String, CustomRule> customRules;

    public RuleExecutor(RuleLookup ruleLookup, Map<String, CustomRule> customRules) {
        this.ruleLookup = ruleLookup;
        this.customRules = customRules != null ? customRules : new HashMap<String, CustomRule>();
    }

    /**
     * 使用与给定关键字关联的规则对单个值进行脱敏。
     *
     * @param value   需要脱敏的值
     * @param keyword 关联的关键字
     * @return 脱敏后的值，如果无需处理则返回原值
     */
    public String maskValue(String value, String keyword) {
        if (value == null || value.isEmpty() || "null".equalsIgnoreCase(value)) return value;

        // Check custom rules first
        CustomRule custom = customRules.get(keyword.toLowerCase());
        if (custom != null) {
            return custom.apply(value);
        }

        // Look up built-in rule type
        RuleType ruleType = ruleLookup.lookup(keyword);
        if (ruleType == null) return value;

        return ruleType.apply(value);
    }

    /**
     * 根据位置和关联的关键字对文本进行脱敏处理。
     *
     * @param text      原始文本
     * @param positions 脱敏位置列表
     * @return 脱敏后的文本
     */
    public String mask(String text, java.util.List<MaskPosition> positions) {
        if (text == null || positions == null || positions.isEmpty()) return text;

        StringBuilder sb = new StringBuilder(text.length());
        int cursor = 0;

        for (MaskPosition pos : positions) {
            // Append text before this value
            sb.append(text, cursor, pos.valueStart);

            // Apply masking
            String originalValue = text.substring(pos.valueStart, pos.valueEnd);
            String masked = maskValue(originalValue, pos.keyword);
            sb.append(masked != null ? masked : originalValue);

            cursor = pos.valueEnd;
        }

        // Append remaining text
        if (cursor < text.length()) {
            sb.append(text, cursor, text.length());
        }

        return sb.toString();
    }

    /**
     * 通过关键字查找规则类型的接口。
     *
     * @author renss
     * @version V1.0.0
     * @since 1.0.0 2026/6/2
     */
    public interface RuleLookup {
        /**
         * 根据关键字查找对应的规则类型。
         *
         * @param keyword 关键字
         * @return 对应的RuleType，如果未找到则返回null
         */
        RuleType lookup(String keyword);
    }
}
