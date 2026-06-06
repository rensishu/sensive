package com.github.renss.sensive.engine;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class KvStateMachineTest {

    private KeywordMatcher matcher;

    @Before
    public void setUp() {
        matcher = new KeywordMatcher();
        matcher.addKeyword("phone");
        matcher.addKeyword("name");
        matcher.addKeyword("idcard");
        matcher.addKeyword("email");
        matcher.addKeyword("accountno");
    }

    @Test
    public void testKeyEqualsValue() {
        List<MaskPosition> positions = KvStateMachine.scan("phone=13812345678", matcher);
        assertEquals(1, positions.size());
        MaskPosition pos = positions.get(0);
        assertEquals("13812345678", substring("phone=13812345678", pos));
    }

    @Test
    public void testKeyColonValue() {
        List<MaskPosition> positions = KvStateMachine.scan("phone: 13812345678", matcher);
        assertEquals(1, positions.size());
        assertEquals("13812345678", substring("phone: 13812345678", positions.get(0)));
    }

    @Test
    public void testJsonStyleDoubleQuote() {
        List<MaskPosition> positions = KvStateMachine.scan("\"phone\":\"13812345678\"", matcher);
        assertEquals(1, positions.size());
        assertEquals("13812345678", substring("\"phone\":\"13812345678\"", positions.get(0)));
    }

    @Test
    public void testJsonStyleSingleQuote() {
        List<MaskPosition> positions = KvStateMachine.scan("'phone':'13812345678'", matcher);
        assertEquals(1, positions.size());
        assertEquals("13812345678", substring("'phone':'13812345678'", positions.get(0)));
    }

    @Test
    public void testMultipleKeyValues() {
        String text = "phone=13812345678, name=张三, idcard=310101199001011234";
        List<MaskPosition> positions = KvStateMachine.scan(text, matcher);
        assertEquals(3, positions.size());
        assertEquals("13812345678", substring(text, positions.get(0)));
        assertEquals("张三", substring(text, positions.get(1)));
        assertEquals("310101199001011234", substring(text, positions.get(2)));
    }

    @Test
    public void testCommaSeparated() {
        String text = "phone=13812345678,name=13987654321";
        List<MaskPosition> positions = KvStateMachine.scan(text, matcher);
        assertEquals(2, positions.size());
    }

    @Test
    public void testQuotedValueEndsAtClosingQuote() {
        List<MaskPosition> positions = KvStateMachine.scan("\"name\":\"张三, test\"", matcher);
        assertEquals(1, positions.size());
        assertEquals("张三, test", substring("\"name\":\"张三, test\"", positions.get(0)));
    }

    @Test
    public void testUnquotedValueEndsAtComma() {
        List<MaskPosition> positions = KvStateMachine.scan("phone=13812345678, name=张三", matcher);
        assertEquals(2, positions.size());
    }

    @Test
    public void testUnquotedValueEndsAtSpace() {
        List<MaskPosition> positions = KvStateMachine.scan("phone=13812345678 name=张三", matcher);
        assertEquals(2, positions.size());
    }

    @Test
    public void testValueEndsAtCurlyBrace() {
        List<MaskPosition> positions = KvStateMachine.scan("{\"phone\":\"13812345678\"}", matcher);
        assertEquals(1, positions.size());
        assertEquals("13812345678", substring("{\"phone\":\"13812345678\"}", positions.get(0)));
    }

    @Test
    public void testValueEndsAtBracket() {
        List<MaskPosition> positions = KvStateMachine.scan("[phone=13812345678]", matcher);
        assertEquals(1, positions.size());
    }

    @Test
    public void testCaseInsensitiveKeywords() {
        List<MaskPosition> positions = KvStateMachine.scan("Phone=13812345678, NAME=张三", matcher);
        assertEquals(2, positions.size());
    }

    @Test
    public void testNoMatchReturnsEmpty() {
        List<MaskPosition> positions = KvStateMachine.scan("user=13812345678, addr=beijing", matcher);
        assertTrue(positions.isEmpty());
    }

    @Test
    public void testKeywordAtEndOfText() {
        List<MaskPosition> positions = KvStateMachine.scan("phone=13812345678", matcher);
        assertEquals(1, positions.size());
    }

    @Test
    public void testJdbcPlaceholderNotParsed() {
        // SQL "phone=?" must NOT have ? treated as a value
        List<MaskPosition> positions = KvStateMachine.scan("phone=?", matcher);
        assertTrue("JDBC placeholder ? should not be parsed as a value", positions.isEmpty());
    }

    @Test
    public void testJdbcPlaceholderInSql() {
        // Full SQL with ? placeholders
        List<MaskPosition> positions = KvStateMachine.scan(
                "SELECT * FROM users WHERE phone=? AND name=?", matcher);
        assertTrue("SQL with ? placeholders should not be matched", positions.isEmpty());
    }

    @Test
    public void testJdbcPlaceholderMixedWithRealValue() {
        // phone=? should be skipped, but phone=13812345678 should be matched
        String text = "phone=? OR phone=13812345678";
        List<MaskPosition> positions = KvStateMachine.scan(text, matcher);
        assertEquals("Only the real value should be matched", 1, positions.size());
        assertEquals("13812345678", substring(text, positions.get(0)));
    }

    private static String substring(String text, MaskPosition pos) {
        return text.substring(pos.valueStart, pos.valueEnd);
    }
}
