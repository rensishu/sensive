package com.github.renss.sensive.engine;

import com.github.renss.sensive.RuleType;
import com.github.renss.sensive.config.model.CustomRule;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class RuleExecutorTest {

    private RuleExecutor executor;
    private Map<String, RuleType> keywordRules;

    @Before
    public void setUp() {
        keywordRules = new HashMap<String, RuleType>();
        keywordRules.put("phone", RuleType.PHONE_MASK);
        keywordRules.put("name", RuleType.NAME_MASK);
        keywordRules.put("idcard", RuleType.IDCARD_MASK);
        keywordRules.put("accountno", RuleType.ACCOUNT_MASK);
        keywordRules.put("email", RuleType.EMAIL_MASK);
        keywordRules.put("password", RuleType.FULL_MASK);

        RuleExecutor.RuleLookup lookup = new RuleExecutor.RuleLookup() {
            @Override
            public RuleType lookup(String keyword) {
                return keywordRules.get(keyword.toLowerCase());
            }
        };

        executor = new RuleExecutor(lookup, new HashMap<String, CustomRule>());
    }

    @Test
    public void testPhoneMask() {
        String result = executor.maskValue("13812345678", "phone");
        assertEquals("138****5678", result);
    }

    @Test
    public void testNameMask_TwoChars() {
        String result = executor.maskValue("张三", "name");
        assertEquals("张*", result);
    }

    @Test
    public void testNameMask_ThreeChars() {
        String result = executor.maskValue("张三丰", "name");
        assertEquals("张*丰", result);
    }

    @Test
    public void testIdcardMask() {
        String result = executor.maskValue("310101199001011234", "idcard");
        assertEquals("310101********1234", result);
    }

    @Test
    public void testAccountMask() {
        String result = executor.maskValue("6222021234567890", "accountno");
        assertEquals("************7890", result);
    }

    @Test
    public void testEmailMask() {
        String result = executor.maskValue("zhangsan@mail.com", "email");
        assertEquals("zh***n@mail.com", result);
    }

    @Test
    public void testFullMask() {
        String result = executor.maskValue("mypassword123", "password");
        assertEquals("****", result);
    }

    @Test
    public void testNullValue() {
        assertNull(executor.maskValue(null, "phone"));
    }

    @Test
    public void testUnknownKeyword() {
        String result = executor.maskValue("13812345678", "unknown_key");
        assertEquals("13812345678", result);
    }

    @Test
    public void testMaskTextWithPositions() {
        String text = "phone=13812345678, name=张三";
        List<MaskPosition> positions = new ArrayList<MaskPosition>();
        positions.add(new MaskPosition(6, 17, "phone"));
        positions.add(new MaskPosition(24, 26, "name"));

        String result = executor.mask(text, positions);
        assertEquals("phone=138****5678, name=张*", result);
    }

    @Test
    public void testCustomRegexRule() {
        Map<String, CustomRule> customRules = new HashMap<String, CustomRule>();
        CustomRule rule = new CustomRule();
        rule.setKeyword("custom");
        rule.setType("regex");
        rule.setPattern("^(\\d{4})\\d+(\\d{4})$");
        rule.setReplacement("$1****$2");
        customRules.put("custom", rule);

        RuleExecutor customExecutor = new RuleExecutor(new RuleExecutor.RuleLookup() {
            @Override
            public RuleType lookup(String keyword) {
                return keywordRules.get(keyword.toLowerCase());
            }
        }, customRules);

        String result = customExecutor.maskValue("6222021234567890", "custom");
        assertEquals("6222****7890", result);
    }
}
