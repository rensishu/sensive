package com.github.renss.sensive.engine;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class TextPatternMatcherTest {

    private static Set<String> set(String... values) {
        return new HashSet<String>(Arrays.asList(values));
    }

    @Test
    public void testPhonePattern() {
        List<MaskPosition> positions = TextPatternMatcher.scan(
                "phone=13812345678", Collections.<MaskPosition>emptyList(), set("phone"));

        assertEquals(1, positions.size());
        MaskPosition pos = positions.get(0);
        assertEquals(6, pos.valueStart);
        assertEquals(17, pos.valueEnd);
        assertEquals("phone", pos.keyword);
    }

    @Test
    public void testPhonePatternInvalidStart() {
        // Phone numbers starting with 2 are not valid Chinese mobile numbers
        List<MaskPosition> positions = TextPatternMatcher.scan(
                "23812345678", Collections.<MaskPosition>emptyList(), set("phone"));

        assertTrue(positions.isEmpty());
    }

    @Test
    public void testIdcardPattern() {
        List<MaskPosition> positions = TextPatternMatcher.scan(
                "id=310101199001011234", Collections.<MaskPosition>emptyList(), set("idcard"));

        assertEquals(1, positions.size());
        assertEquals("idcard", positions.get(0).keyword);
    }

    @Test
    public void testIdcardWithX() {
        List<MaskPosition> positions = TextPatternMatcher.scan(
                "id=31010119900101123X", Collections.<MaskPosition>emptyList(), set("idcard"));

        assertEquals(1, positions.size());
        assertEquals("idcard", positions.get(0).keyword);
    }

    @Test
    public void testBankcardPattern() {
        List<MaskPosition> positions = TextPatternMatcher.scan(
                "card=6222021234567890", Collections.<MaskPosition>emptyList(), set("bankcard"));

        assertEquals(1, positions.size());
        assertEquals("bankcard", positions.get(0).keyword);
    }

    @Test
    public void testMultiplePatternsInText() {
        List<MaskPosition> positions = TextPatternMatcher.scan(
                "data: 13812345678, 310101199001011234, 6222021234567890",
                Collections.<MaskPosition>emptyList(), set("phone", "idcard", "bankcard"));

        assertEquals(3, positions.size());
    }

    @Test
    public void testSkipsCoveredRegions() {
        // "phone=13812345678, other=13987654321"
        // First phone at [6,17], second at [25,36]
        List<MaskPosition> existing = new ArrayList<MaskPosition>();
        existing.add(new MaskPosition(6, 17, "phone")); // KV already covers "13812345678"

        List<MaskPosition> positions = TextPatternMatcher.scan(
                "phone=13812345678, other=13987654321",
                existing, set("phone"));

        // Only the second phone number should be matched
        assertEquals(1, positions.size());
        assertEquals(25, positions.get(0).valueStart);
    }

    @Test
    public void testDisabledWhenEmptyPatterns() {
        List<MaskPosition> positions = TextPatternMatcher.scan(
                "13812345678", Collections.<MaskPosition>emptyList(),
                Collections.<String>emptySet());

        assertTrue(positions.isEmpty());
    }

    @Test
    public void testMyBatisParameterLine() {
        // Simulates MyBatis parameter log: 13812345678(String), 张三(String), 310101199001011234(String)
        String text = "==> Parameters: 13812345678(String), 310101199001011234(String)";

        List<MaskPosition> positions = TextPatternMatcher.scan(
                text, Collections.<MaskPosition>emptyList(), set("phone", "idcard"));

        assertEquals(2, positions.size());
        assertEquals("phone", positions.get(0).keyword);
        assertEquals("idcard", positions.get(1).keyword);
    }

    @Test
    public void testNoFalsePositiveOnTimestamp() {
        // "20240601103045" is 14 digits - should not match anything
        List<MaskPosition> positions = TextPatternMatcher.scan(
                "time=20240601103045", Collections.<MaskPosition>emptyList(),
                set("phone", "idcard", "bankcard"));

        assertTrue(positions.isEmpty());
    }
}
