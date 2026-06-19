package com.github.renss.sensive;

import com.github.renss.sensive.config.SensitiveConfig;
import org.junit.Test;

import static org.junit.Assert.*;

public class SensiveUtilsTest {

    @Test
    public void testMask_KeyEqualsValue() {
        String result = SensiveUtils.mask("phone=13812345678");
        assertEquals("phone=138****5678", result);
    }

    @Test
    public void testMask_JsonFormat() {
        String result = SensiveUtils.mask("\"phone\":\"13812345678\"");
        assertEquals("\"phone\":\"138****5678\"", result);
    }

    @Test
    public void testMask_MultipleFields() {
        String result = SensiveUtils.mask(
                "phone=13812345678, name=张三, idcard=310101199001011234");
        assertEquals("phone=138****5678, name=张*, idcard=310101********1234", result);
    }

    @Test
    public void testMask_NameMasking() {
        assertEquals("name=张*", SensiveUtils.mask("name=张三"));
        assertEquals("name=张*丰", SensiveUtils.mask("name=张三丰"));
    }

    @Test
    public void testMask_EmailMasking() {
        String result = SensiveUtils.mask("email=zhangsan@mail.com");
        assertEquals("email=zh***n@mail.com", result);
    }

    @Test
    public void testMask_PasswordMasking() {
        String result = SensiveUtils.mask("password=myPass123");
        assertEquals("password=****", result);
    }

    @Test
    public void testMask_CaseInsensitiveKeywords() {
        String result = SensiveUtils.mask("Phone=13812345678, NAME=张三");
        assertEquals("Phone=138****5678, NAME=张*", result);
    }

    @Test
    public void testMask_NoSensitiveData() {
        String text = "user=test, action=login, status=ok";
        assertEquals(text, SensiveUtils.mask(text));
    }

    @Test
    public void testMask_NullAndEmpty() {
        assertNull(SensiveUtils.mask(null));
        assertEquals("", SensiveUtils.mask(""));
    }

    @Test
    public void testMask_SpecificKeyword() {
        String result = SensiveUtils.mask("phone=13812345678, idcard=310101199001011234", "phone");
        // Only phone should be masked
        assertEquals("phone=138****5678, idcard=310101199001011234", result);
    }

    @Test
    public void testMask_RuleTypeOverride() {
        // Use FULL_MASK on phone instead of PHONE_MASK
        String result = SensiveUtils.mask("phone=13812345678", "phone", RuleType.FULL_MASK);
        assertEquals("phone=****", result);
    }

    @Test
    public void testMaskValue() {
        assertEquals("138****5678", SensiveUtils.maskValue("13812345678", RuleType.PHONE_MASK));
        assertEquals("张*", SensiveUtils.maskValue("张三", RuleType.NAME_MASK));
        assertEquals("310101********1234", SensiveUtils.maskValue("310101199001011234", RuleType.IDCARD_MASK));
        assertEquals("****", SensiveUtils.maskValue("anything", RuleType.FULL_MASK));
    }

    @Test
    public void testMaskValue_Null() {
        assertNull(SensiveUtils.maskValue(null, RuleType.PHONE_MASK));
        assertEquals("123", SensiveUtils.maskValue("123", null));
        assertEquals("", SensiveUtils.maskValue("", RuleType.PHONE_MASK));
        assertEquals("null", SensiveUtils.maskValue("null", RuleType.PHONE_MASK));
        assertEquals("NULL", SensiveUtils.maskValue("NULL", RuleType.FULL_MASK));
        assertEquals("Null", SensiveUtils.maskValue("Null", RuleType.NAME_MASK));
    }

    @Test
    public void testRegisterKeyword() {
        // Register a custom keyword
        SensiveUtils.registerKeyword("myphone", RuleType.PHONE_MASK);

        String result = SensiveUtils.mask("myphone=13812345678");
        assertEquals("myphone=138****5678", result);

        // Reload to restore defaults
        SensiveUtils.reloadConfig();
    }

    @Test
    public void testLogLikeFormat() {
        String logLine = "INFO 2024-01-01 10:00:00 [main] com.example.Service - " +
                "user login, phone=13812345678, email=test@example.com, token=abc123def456";
        String result = SensiveUtils.mask(logLine);
        assertTrue(result.contains("138****5678"));
        assertTrue(result.contains("te***t@example.com"));
        assertTrue(result.contains("token=****"));
        // Non-sensitive parts preserved
        assertTrue(result.contains("INFO 2024-01-01"));
        assertTrue(result.contains("com.example.Service"));
        assertTrue(result.contains("user login"));
    }

    @Test
    public void testMask_AccountNumber() {
        String result = SensiveUtils.mask("accountNo=6222021234567890");
        assertEquals("accountNo=************7890", result);
    }

    @Test
    public void testMask_Address() {
        String result = SensiveUtils.mask("address=北京市朝阳区某某街道100号");
        assertEquals("address=北京市朝阳区***", result);
    }

    // --- maskEnhanced() tests ---

    @Test
    public void testMaskSql_KvMasking() {
        // maskEnhanced should also do KV matching (same as mask)
        String result = SensiveUtils.maskEnhanced("phone=13812345678, name=张三");
        assertEquals("phone=138****5678, name=张*", result);
    }

    @Test
    public void testMaskSql_TextPatternPhone() {
        // maskEnhanced should detect bare phone numbers (text pattern scanning)
        String result = SensiveUtils.maskEnhanced("Parameters: 13812345678(String)");
        assertEquals("Parameters: 138****5678(String)", result);
    }

    @Test
    public void testMaskSql_TextPatternIdcard() {
        // maskEnhanced should detect bare ID card numbers
        String result = SensiveUtils.maskEnhanced("id=310101199001011234");
        assertEquals("id=310101********1234", result);
    }

    @Test
    public void testMaskSql_TextPatternBankcard() {
        // maskEnhanced should detect bare bank card numbers
        String result = SensiveUtils.maskEnhanced("card=6222021234567890");
        assertEquals("card=************7890", result);
    }

    @Test
    public void testMask_NoTextPatternWhenDisabled() {
        // mask() 在 textPattern.enabled=false 时不执行文本模式扫描
        // 裸露手机号不应被脱敏（仅 KV 匹配）
        String result = SensiveUtils.mask("Parameters: 13812345678(String)");
        assertEquals("Parameters: 13812345678(String)", result);
    }

    @Test
    public void testMask_TextPatternWhenEnabled() {
        // 验证 textPattern.enabled=true 时 mask() 执行文本模式扫描
        // 需要通过代码临时开启（测试配置默认为 false）
        try {
            // 构建一个启用全局文本扫描的配置
            java.util.Properties props = new java.util.Properties();
            props.setProperty("sensitive.enabled", "true");
            props.setProperty("sensitive.text-pattern.enabled", "true");
            props.setProperty("sensitive.text-pattern.sql", "true");
            props.setProperty("sensitive.text-pattern.patterns", "phone,idcard,bankcard");
            SensitiveConfig.reload(props);
            SensiveUtils.refreshEngine();

            String result = SensiveUtils.mask("Parameters: 13812345678(String)");
            assertEquals("Parameters: 138****5678(String)", result);
        } finally {
            // 恢复默认配置
            SensitiveConfig.reload();
            SensiveUtils.refreshEngine();
        }
    }

    @Test
    public void testMaskSql_MyBatisStyleOutput() {
        // Simulates MyBatis parameter log output
        String log = "==> Parameters: 13812345678(String), 310101199001011234(String), 张三(String)";
        String result = SensiveUtils.maskEnhanced(log);

        // Phone and ID card should be masked by text pattern scanning
        assertTrue(result.contains("138****5678"));
        assertTrue(result.contains("310101********1234"));
        // Name "张三" should be masked via KV matching on "name" keyword
        // (but here it's bare, so text pattern won't find it — KV needs a keyword= format)
    }

    @Test
    public void testMaskSql_NoFalsePositiveOnTimestamp() {
        // Timestamps like 20240601103045 should not be masked
        String result = SensiveUtils.maskEnhanced("time=20240601103045");
        // time prefix is not a keyword, timestamp is not a phone/idcard/bankcard pattern
        assertEquals("time=20240601103045", result);
    }

    // --- MyBatis SQL awareness ---

    @Test
    public void testMask_JdbcPlaceholderNotCorrupted() {
        // SQL with ? placeholder must not be corrupted
        String result = SensiveUtils.mask("SELECT * FROM users WHERE phone=? AND name=?");
        assertEquals("SELECT * FROM users WHERE phone=? AND name=?", result);
    }

    @Test
    public void testMaskSql_JdbcPlaceholderNotCorrupted() {
        // maskEnhanced also must not corrupt ? placeholders
        String result = SensiveUtils.maskEnhanced("SELECT * FROM users WHERE phone=? AND name=?");
        assertEquals("SELECT * FROM users WHERE phone=? AND name=?", result);
    }

    @Test
    public void testMyBatisParametersLine_Masking() {
        // MyBatis Parameters: line with bare values should be masked by maskEnhanced
        String paramsLine = "==> Parameters: 13812345678(String), 310101199001011234(String), 张三(String)";
        String result = SensiveUtils.maskEnhanced(paramsLine);
        assertTrue("Phone should be masked", result.contains("138****5678"));
        assertTrue("ID card should be masked", result.contains("310101********1234"));
        // ? should NOT appear in result (it's not a placeholder line)
        assertFalse("Parameters line should not have ?", result.contains("?"));
    }

    @Test
    public void testMyBatisPreparingLine_Untouched() {
        // SQL Preparing line must be left completely untouched
        String sql = "==>  Preparing: SELECT id, phone, name, idcard FROM users WHERE phone=? AND name=?";
        String result = SensiveUtils.mask(sql);
        assertEquals("Preparing line must not be altered", sql, result);
    }

    // --- word boundary: keyword should not match inside camelCase/snake_case words ---

    @Test
    public void testMask_KeywordInsideCompoundWord() {
        // "name" inside "productName" must NOT be matched
        String result = SensiveUtils.mask("productName=某产品");
        assertEquals("productName=某产品", result);
    }

    @Test
    public void testMask_KeywordInsideCamelCase() {
        // "phone" inside "somePhone" must NOT be matched (somePhone is not a registered keyword)
        String result = SensiveUtils.mask("somePhone=13812345678");
        assertEquals("somePhone=13812345678", result);
    }

    @Test
    public void testMask_RegisteredKeywordInCamelCase() {
        // "mobilePhone" IS a registered keyword, so it should be matched and masked
        String result = SensiveUtils.mask("mobilePhone=13812345678");
        assertEquals("mobilePhone=138****5678", result);
    }

    @Test
    public void testMask_KeywordAfterUnderscore() {
        // "name" after underscore is NOT a boundary — product_name is one identifier
        String result = SensiveUtils.mask("product_name=某产品");
        assertEquals("product_name=某产品", result);
    }

    @Test
    public void testMask_RealKeywordStillWorks() {
        // "name" as standalone keyword should still work
        String result = SensiveUtils.mask("name=张三");
        assertEquals("name=张*", result);
    }

    @Test
    public void testMask_EmailKeywordNotSubstring() {
        // "mail" inside "email=test@mail.com" — wait, "email" is a keyword, not "mail"
        // "mail" IS a keyword, but in "email=test@mail.com", does "mail" match inside "email"?
        // 'm' in "email" is at position 1, preceded by 'e' (letter) → no match for "mail"
        // 'mail' in "test@mail.com" at position 6, preceded by '@' → should match
        String result = SensiveUtils.mask("email=test@mail.com, mail=user@mail.com");
        // "email" keyword matches at position 0 for the first key
        // "mail" keyword at position 20 (after comma space), preceded by ' ' → matches
        assertTrue(result.contains("email=te***t@mail.com"));
    }
}
