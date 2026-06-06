package com.github.renss.sensive.engine;

import org.junit.Test;

import static org.junit.Assert.*;

public class KeywordMatcherTest {

    @Test
    public void testSingleKeywordMatch() {
        KeywordMatcher matcher = new KeywordMatcher();
        matcher.addKeyword("phone");

        KeywordMatcher.MatchResult result = matcher.matchAt("phone=13812345678", 0);
        assertNotNull(result);
        assertEquals("phone", result.keyword);
        assertEquals(0, result.keyStart);
        assertEquals(4, result.keyEnd);
    }

    @Test
    public void testCaseInsensitiveMatch() {
        KeywordMatcher matcher = new KeywordMatcher();
        matcher.addKeyword("phone");

        assertNotNull(matcher.matchAt("Phone=13812345678", 0));
        assertNotNull(matcher.matchAt("PHONE=13812345678", 0));
        assertNotNull(matcher.matchAt("pHoNe=13812345678", 0));
    }

    @Test
    public void testLongestMatchPreferred() {
        KeywordMatcher matcher = new KeywordMatcher();
        matcher.addKeyword("id");
        matcher.addKeyword("idcard");

        KeywordMatcher.MatchResult result = matcher.matchAt("idcard=310101199001011234", 0);
        assertNotNull(result);
        assertEquals("idcard", result.keyword);
    }

    @Test
    public void testNoMatch() {
        KeywordMatcher matcher = new KeywordMatcher();
        matcher.addKeyword("phone");

        assertNull(matcher.matchAt("user=13812345678", 0));
        assertNull(matcher.matchAt("phone=13812345678", 1));
    }

    @Test
    public void testMultipleKeywords() {
        KeywordMatcher matcher = new KeywordMatcher();
        matcher.addKeyword("phone");
        matcher.addKeyword("name");
        matcher.addKeyword("idcard");
        matcher.addKeyword("email");

        assertNotNull(matcher.matchAt("phone=138", 0));
        assertNotNull(matcher.matchAt("name=张三", 0));
        assertNotNull(matcher.matchAt("idcard=310101", 0));
        assertNotNull(matcher.matchAt("email=test@mail.com", 0));
    }

    @Test
    public void testKeywordWithUnderscore() {
        KeywordMatcher matcher = new KeywordMatcher();
        matcher.addKeyword("real_name");

        KeywordMatcher.MatchResult result = matcher.matchAt("real_name=张三", 0);
        assertNotNull(result);
        assertEquals("real_name", result.keyword);
    }

    @Test
    public void testEmptyMatcher() {
        KeywordMatcher matcher = new KeywordMatcher();
        assertTrue(matcher.isEmpty());
        assertNull(matcher.matchAt("anything", 0));
    }

    @Test
    public void testMatchInMiddleOfText() {
        KeywordMatcher matcher = new KeywordMatcher();
        matcher.addKeyword("phone");

        // "user=test, phone=138..." -> 'p' is at index 11
        KeywordMatcher.MatchResult result = matcher.matchAt("user=test, phone=13812345678, addr=beijing", 11);
        assertNotNull(result);
        assertEquals("phone", result.keyword);
    }

    // --- word boundary tests ---

    @Test
    public void testKeywordInsideCamelCase() {
        // "name" inside "productName" should NOT match
        KeywordMatcher matcher = new KeywordMatcher();
        matcher.addKeyword("name");

        // productName -> 'n' at index 7 is preceded by 't' (letter)
        assertNull(matcher.matchAt("productName=某产品", 7));
    }

    @Test
    public void testKeywordAfterUnderscore() {
        // "name" after underscore should NOT match — underscore is an identifier char
        KeywordMatcher matcher = new KeywordMatcher();
        matcher.addKeyword("name");

        // product_name -> 'n' at index 8 is preceded by '_' (identifier char)
        assertNull(matcher.matchAt("product_name=某产品", 8));
    }

    @Test
    public void testKeywordAfterDigit() {
        // "name" after a digit should NOT match (digit is alphanumeric)
        KeywordMatcher matcher = new KeywordMatcher();
        matcher.addKeyword("name");

        // product1name -> 'n' at index 8 is preceded by '1' (digit)
        assertNull(matcher.matchAt("product1name=某产品", 8));
    }

    @Test
    public void testKeywordAtStartOfText() {
        // keyword at position 0 should always match
        KeywordMatcher matcher = new KeywordMatcher();
        matcher.addKeyword("name");

        KeywordMatcher.MatchResult result = matcher.matchAt("name=张三", 0);
        assertNotNull("keyword at start should match", result);
        assertEquals("name", result.keyword);
    }

    @Test
    public void testRealnameKeywordStillMatches() {
        // "name" is a substring inside "realname" — should not match
        // But "realname" keyword itself should match when starting at position 0
        KeywordMatcher matcher = new KeywordMatcher();
        matcher.addKeyword("name");
        matcher.addKeyword("realname");

        // realname=张三 -> 'r' at 0 → matches "realname"
        KeywordMatcher.MatchResult result = matcher.matchAt("realname=张三", 0);
        assertNotNull(result);
        assertEquals("realname", result.keyword);

        // But 'n' at position 4 should not match "name" (preceded by 'l')
        assertNull(matcher.matchAt("realname=张三", 4));
    }
}
