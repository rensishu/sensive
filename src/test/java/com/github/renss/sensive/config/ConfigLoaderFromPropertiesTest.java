package com.github.renss.sensive.config;

import com.github.renss.sensive.RuleType;
import com.github.renss.sensive.SensiveUtils;
import org.junit.After;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.*;

/**
 * 验证从 Properties 加载配置的完整链路：
 * ConfigLoader.loadFromProperties → SensitiveConfig.reload → SensiveUtils.refreshEngine → 脱敏生效
 */
public class ConfigLoaderFromPropertiesTest {

    @After
    public void tearDown() {
        // Restore defaults after each test
        SensitiveConfig.reload();
        SensiveUtils.reloadConfig();
    }

    @Test
    public void testLoadFromProperties_KeywordsOverride() {
        Properties props = new Properties();
        // Add a new keyword that doesn't exist in defaults
        props.setProperty("sensitive.keywords.my_custom_field", "PHONE_MASK");

        ConfigLoader.ConfigHolder holder = ConfigLoader.loadFromProperties(props);
        assertNotNull(holder);
        assertTrue(holder.keywords.containsKey("my_custom_field"));
        assertEquals(RuleType.PHONE_MASK, holder.keywords.get("my_custom_field"));
    }

    @Test
    public void testLoadFromProperties_KeywordsOverrideDefaults() {
        Properties props = new Properties();
        // Override default mapping — phone normally maps to PHONE_MASK
        props.setProperty("sensitive.keywords.phone", "FULL_MASK");

        ConfigLoader.ConfigHolder holder = ConfigLoader.loadFromProperties(props);
        assertEquals(RuleType.FULL_MASK, holder.keywords.get("phone"));
    }

    @Test
    public void testLoadFromProperties_EmptyPropsReturnsDefaults() {
        ConfigLoader.ConfigHolder holder = ConfigLoader.loadFromProperties(new Properties());
        assertNotNull(holder);
        assertTrue(holder.keywords.containsKey("phone"));
        assertEquals(RuleType.PHONE_MASK, holder.keywords.get("phone"));
    }

    @Test
    public void testLoadFromProperties_NullPropsReturnsDefaults() {
        ConfigLoader.ConfigHolder holder = ConfigLoader.loadFromProperties(null);
        assertNotNull(holder);
        assertTrue(holder.keywords.containsKey("phone"));
    }

    @Test
    public void testLoadFromProperties_TextPatternEnabled() {
        Properties props = new Properties();
        props.setProperty("sensitive.text-pattern.enabled", "true");
        props.setProperty("sensitive.text-pattern.patterns", "phone,idcard");

        ConfigLoader.ConfigHolder holder = ConfigLoader.loadFromProperties(props);
        assertNotNull(holder.textPattern);
        assertTrue(holder.textPattern.isEnabled());
        assertTrue(holder.textPattern.getPatterns().contains("phone"));
        assertTrue(holder.textPattern.getPatterns().contains("idcard"));
    }

    // ========================
    // End-to-end: reload → engine → mask
    // ========================

    @Test
    public void testReloadFromProperties_TakesEffect() {
        // Given: default config masks phone with PHONE_MASK
        String result1 = SensiveUtils.mask("phone=13812345678");
        assertEquals("phone=138****5678", result1);

        // When: reload with custom config that maps phone to FULL_MASK
        Properties props = new Properties();
        props.setProperty("sensitive.keywords.phone", "FULL_MASK");
        SensitiveConfig.reload(props);
        SensiveUtils.refreshEngine();

        // Then: phone should now be fully masked
        String result2 = SensiveUtils.mask("phone=13812345678");
        assertEquals("phone=****", result2);
    }

    @Test
    public void testReloadFromProperties_NewKeywordTakesEffect() {
        // Given: "myphone" is not a default keyword
        String result1 = SensiveUtils.mask("myphone=13812345678");
        assertEquals("myphone=13812345678", result1); // not masked

        // When: add it via Properties
        Properties props = new Properties();
        props.setProperty("sensitive.keywords.myphone", "PHONE_MASK");
        SensitiveConfig.reload(props);
        SensiveUtils.refreshEngine();

        // Then: it should be masked
        String result2 = SensiveUtils.mask("myphone=13812345678");
        assertEquals("myphone=138****5678", result2);
    }

    @Test
    public void testReloadFromProperties_TextPatternTakesEffect() {
        // When: enable text pattern scanning via Properties
        Properties props = new Properties();
        props.setProperty("sensitive.text-pattern.enabled", "true");
        props.setProperty("sensitive.text-pattern.patterns", "phone,idcard,bankcard");
        SensitiveConfig.reload(props);
        SensiveUtils.refreshEngine();

        // Then: maskSql should detect bare phone numbers
        String result = SensiveUtils.maskSql("Parameters: 13812345678(String)");
        assertTrue(result, result.contains("138****5678"));
    }

    @Test
    public void testReloadFromProperties_MultipleKeys() {
        Properties props = new Properties();
        props.setProperty("sensitive.keywords.field_a", "PHONE_MASK");
        props.setProperty("sensitive.keywords.field_b", "FULL_MASK");
        props.setProperty("sensitive.keywords.field_c", "NAME_MASK");
        SensitiveConfig.reload(props);
        SensiveUtils.refreshEngine();

        assertEquals("field_a=138****5678", SensiveUtils.mask("field_a=13812345678"));
        assertEquals("field_b=****", SensiveUtils.mask("field_b=secret123"));
        assertEquals("field_c=张*", SensiveUtils.mask("field_c=张三"));
    }

    @Test
    public void testReloadFromProperties_Disabled() {
        Properties props = new Properties();
        props.setProperty("sensitive.enabled", "false");
        SensitiveConfig.reload(props);
        SensiveUtils.refreshEngine();

        // When disabled, no masking should occur
        String result = SensiveUtils.mask("phone=13812345678");
        assertEquals("phone=13812345678", result);
    }

    @Test
    public void testReloadFromProperties_OverridesAndDefaultsMerge() {
        Properties props = new Properties();
        // Only add one custom keyword
        props.setProperty("sensitive.keywords.custom_field", "PHONE_MASK");
        SensitiveConfig.reload(props);
        SensiveUtils.refreshEngine();

        // Custom keyword should be masked
        assertEquals("custom_field=138****5678",
                SensiveUtils.mask("custom_field=13812345678"));

        // Default keywords should still work
        assertEquals("name=张*", SensiveUtils.mask("name=张三"));
        assertEquals("idcard=310101********1234",
                SensiveUtils.mask("idcard=310101199001011234"));
    }
}
