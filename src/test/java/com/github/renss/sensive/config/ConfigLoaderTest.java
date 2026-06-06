package com.github.renss.sensive.config;

import com.github.renss.sensive.RuleType;
import org.junit.Test;

import static org.junit.Assert.*;

public class ConfigLoaderTest {

    @Test
    public void testLoadDefaults() {
        // When no external config exists, defaults should be loaded
        SensitiveConfig config = SensitiveConfig.getInstance();
        assertTrue(config.isEnabled());
        assertNotNull(config.getKeywords());
        assertFalse(config.getKeywords().isEmpty());

        // Verify some default keywords exist
        assertEquals(RuleType.PHONE_MASK, config.lookupKeyword("phone"));
        assertEquals(RuleType.NAME_MASK, config.lookupKeyword("name"));
        assertEquals(RuleType.IDCARD_MASK, config.lookupKeyword("idcard"));
        assertEquals(RuleType.ACCOUNT_MASK, config.lookupKeyword("accountno"));
        assertEquals(RuleType.EMAIL_MASK, config.lookupKeyword("email"));
        assertEquals(RuleType.FULL_MASK, config.lookupKeyword("password"));
    }

    @Test
    public void testKeywordCaseInsensitive() {
        SensitiveConfig config = SensitiveConfig.getInstance();
        assertEquals(RuleType.PHONE_MASK, config.lookupKeyword("Phone"));
        assertEquals(RuleType.PHONE_MASK, config.lookupKeyword("PHONE"));
        assertEquals(RuleType.PHONE_MASK, config.lookupKeyword("pHoNe"));
    }

    @Test
    public void testRegisterKeyword() {
        SensitiveConfig config = SensitiveConfig.getInstance();
        config.registerKeyword("myfield", RuleType.PHONE_MASK);

        // Reload resets to defaults
        SensitiveConfig.reload();
        config = SensitiveConfig.getInstance();
        // After reload, custom registration is gone
    }

    @Test
    public void testDefaultKeywordCount() {
        SensitiveConfig config = SensitiveConfig.getInstance();
        // Should have at least 25 keywords
        assertTrue("Expected 25+ keywords, got " + config.getKeywords().size(),
                config.getKeywords().size() >= 25);
    }
}
