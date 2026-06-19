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
 * @version V1.2.0
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

        // Lowercase once for both custom rule and built-in lookups
        String lowerKeyword = keyword.toLowerCase();

        // Check custom rules first
        CustomRule custom = customRules.get(lowerKeyword);
        if (custom != null) {
            return custom.apply(value);
        }

        // Look up built-in rule type (keyword is already lowercased)
        RuleType ruleType = ruleLookup.lookup(lowerKeyword);
        if (ruleType == null) return value;

        return ruleType.apply(value);
    }

    /**
     * 根据位置和关联的关键字对文本进行脱敏处理。
     *
     * <p>对内置规则使用快速路径 {@link RuleType#applyFast(CharSequence, int, int)}，
     * 避免对每个位置调用 {@link String#substring(int, int)}。
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

            // Apply masking — use fast path for built-in rules
            String masked = maskPosition(text, pos);
            sb.append(masked);

            cursor = pos.valueEnd;
        }

        // Append remaining text
        if (cursor < text.length()) {
            sb.append(text, cursor, text.length());
        }

        return sb.toString();
    }

    /**
     * 对单个位置应用脱敏。为内置规则使用快速路径以避免 substring 分配。
     */
    private String maskPosition(String text, MaskPosition pos) {
        String lowerKeyword = pos.keyword.toLowerCase();

        // Check custom rules first (require substring for CustomRule.apply(String))
        CustomRule custom = customRules.get(lowerKeyword);
        if (custom != null) {
            String originalValue = text.substring(pos.valueStart, pos.valueEnd);
            String masked = maskValue(originalValue, pos.keyword);
            return masked != null ? masked : originalValue;
        }

        // Built-in rule: use fast path that avoids substring allocation
        RuleType ruleType = ruleLookup.lookup(lowerKeyword);
        if (ruleType == null) {
            // No rule found — return original text segment unchanged
            return text.substring(pos.valueStart, pos.valueEnd);
        }

        return ruleType.applyFast(text, pos.valueStart, pos.valueEnd);
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
         * 调用方应将关键字预小写化以避免重复分配。
         *
         * @param keyword 关键字（应已小写化）
         * @return 对应的RuleType，如果未找到则返回null
         */
        RuleType lookup(String keyword);
    }
}
