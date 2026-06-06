package com.github.renss.sensive.config.model;

import com.github.renss.sensive.RuleType;

/**
 * 关键字到规则类型的映射模型。
 *
 * @author renss
 * @version V1.0.0
 * @since 1.0.0 2026/6/2
 */
public class KeywordRule {
    /** 关键字 */
    private String keyword;
    /** 规则类型 */
    private RuleType ruleType;

    public KeywordRule() {}

    /**
     * 构造关键字规则映射。
     *
     * @param keyword  关键字
     * @param ruleType 规则类型
     */
    public KeywordRule(String keyword, RuleType ruleType) {
        this.keyword = keyword;
        this.ruleType = ruleType;
    }

    /** @return 关键字 */
    public String getKeyword() { return keyword; }
    /** @param keyword 关键字 */
    public void setKeyword(String keyword) { this.keyword = keyword; }
    /** @return 规则类型 */
    public RuleType getRuleType() { return ruleType; }
    /** @param ruleType 规则类型 */
    public void setRuleType(RuleType ruleType) { this.ruleType = ruleType; }
}
