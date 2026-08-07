package com.github.renss.sensive.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 基于状态机的 KV 解析器，识别关键字匹配后值的边界。
 *
 * <p>支持 6 种键值格式：
 * <ol>
 *   <li>key=value</li>
 *   <li>key: value</li>
 *   <li>"key":"value"</li>
 *   <li>'key':'value'</li>
 *   <li>&lt;key&gt;value&lt;/key&gt;</li>
 *   <li>key(value)</li>
 * </ol>
 *
 * @author renss
 * @version V1.2.0
 * @since 1.0.0 2026/6/2
 */
public class KvStateMachine {

    /**
     * 根据关键字匹配结果，在文本中定位紧随其后的值。
     *
     * @param text  文本
     * @param match 关键字匹配结果
     * @return 值的位置区间，无法识别分隔符则返回 null
     */
    public static MaskPosition locateValue(String text, KeywordMatcher.MatchResult match) {
        int pos = match.keyEnd + 1;
        if (pos >= text.length()) return null;

        // Skip whitespace between key and separator
        pos = skipWhitespace(text, pos);
        if (pos >= text.length()) return null;

        char c = text.charAt(pos);

        // Handle escaped quote \" or \' (JSON escape sequence in nested JSON strings)
        if (c == '\\' && pos + 1 < text.length()) {
            char next = text.charAt(pos + 1);
            if (next == '"' || next == '\'') {
                pos++;        // skip backslash, now pos points to the quote
                c = next;     // treat escaped quote as regular quote
            }
        }

        // Check for trailing quote after key, e.g. "key": or 'key':
        if (c == '"' || c == '\'') {
            if (pos + 1 < text.length()) {
                char next = text.charAt(pos + 1);
                if (next == ':' || next == '=') {
                    // "key":value or "key"=value
                    return parseValue(text, pos + 1, match.keyword);
                }
            }
            // key="value" or key:'value'
            if (pos > match.keyEnd + 1) {
                return parseQuotedValue(text, pos, match.keyword);
            }
        }

        if (c == '=') {
            return parseValue(text, pos, match.keyword);
        }
        if (c == ':') {
            return parseValue(text, pos, match.keyword);
        }
        if (c == '(') {
            return parseParenValue(text, pos, match.keyword);
        }

        return null;
    }

    /**
     * 全量扫描：使用关键字匹配器查找文本中所有需要脱敏的位置。
     *
     * <p>ArrayList 延迟分配 — 无匹配时不产生对象分配。
     *
     * @param text    文本
     * @param matcher 关键字匹配器
     * @return 脱敏位置列表（可能为空不可变列表）
     */
    public static List<MaskPosition> scan(String text, KeywordMatcher matcher) {
        if (text == null || text.isEmpty() || matcher.isEmpty()) {
            return Collections.emptyList();
        }

        // Deferred allocation: only create ArrayList when a match is found
        List<MaskPosition> positions = null;
        int len = text.length();
        int i = 0;

        // Reusable match result — avoids per-match object allocation
        KeywordMatcher.MatchResult matchResult = new KeywordMatcher.MatchResult();

        while (i < len) {
            if (!matcher.matchAt(text, i, matchResult)) {
                i++;
                continue;
            }

            MaskPosition pos = locateValue(text, matchResult);
            if (pos != null) {
                if (positions == null) {
                    positions = new ArrayList<MaskPosition>(4);
                }
                positions.add(pos);
                i = pos.valueEnd;
            } else {
                i = matchResult.keyEnd + 1;
            }
        }

        return positions == null ? Collections.<MaskPosition>emptyList() : mergeOverlapping(positions);
    }

    // --- internal parsers ---

    private static MaskPosition parseValue(String text, int sepPos, String keyword) {
        int pos = sepPos + 1; // skip separator
        pos = skipWhitespace(text, pos);
        if (pos >= text.length()) return null;

        char c = text.charAt(pos);

        // Handle escaped quote \" or \' (JSON escape sequence)
        if (c == '\\' && pos + 1 < text.length()) {
            char next = text.charAt(pos + 1);
            if (next == '"' || next == '\'') {
                pos++;        // skip backslash, now pos points to the quote
                c = next;
            }
        }

        if (c == '"' || c == '\'') {
            return parseQuotedValue(text, pos, keyword);
        }
        return parseUnquotedValue(text, pos, keyword);
    }

    private static MaskPosition parseQuotedValue(String text, int quotePos, String keyword) {
        char quote = text.charAt(quotePos);
        int valStart = quotePos + 1;
        if (valStart >= text.length()) return null;

        // Scan for closing quote, handling escaped quotes (\")
        for (int i = valStart; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length() && text.charAt(i + 1) == quote) {
                // Found escaped closing quote \", value ends at backslash position
                return new MaskPosition(valStart, i, keyword);
            }
            if (c == quote) {
                // Found unescaped closing quote
                return new MaskPosition(valStart, i, keyword);
            }
        }
        return null;
    }

    private static MaskPosition parseUnquotedValue(String text, int valStart, String keyword) {
        // Reject JDBC placeholder '?' — SQL like "phone=?" must not be altered
        if (text.charAt(valStart) == '?') return null;

        int valEnd = valStart;
        int len = text.length();

        while (valEnd < len) {
            char c = text.charAt(valEnd);
            if (c == ',' || c == ';' || c == ' ' || c == '\t' || c == '\n' || c == '\r'
                    || c == '}' || c == ']' || c == ')' || c == '>' || c == '&') {
                break;
            }
            valEnd++;
        }

        if (valEnd == valStart) return null;
        return new MaskPosition(valStart, valEnd, keyword);
    }

    private static MaskPosition parseParenValue(String text, int parenPos, String keyword) {
        int valStart = parenPos + 1;
        int depth = 1;
        int valEnd = valStart;

        while (valEnd < text.length() && depth > 0) {
            char c = text.charAt(valEnd);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            if (depth > 0) valEnd++;
        }

        if (valEnd == valStart) return null;
        return new MaskPosition(valStart, valEnd, keyword);
    }

    private static int skipWhitespace(String text, int pos) {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c != ' ' && c != '\t') break;
            pos++;
        }
        return pos;
    }

    /**
     * 合并重叠的脱敏位置区间。
     */
    private static List<MaskPosition> mergeOverlapping(List<MaskPosition> positions) {
        if (positions.size() <= 1) return positions;

        List<MaskPosition> merged = new ArrayList<MaskPosition>(positions.size());
        MaskPosition current = positions.get(0);

        for (int i = 1; i < positions.size(); i++) {
            MaskPosition next = positions.get(i);
            if (next.valueStart <= current.valueEnd) {
                // overlapping: extend current to cover both, keep first keyword
                int newEnd = Math.max(current.valueEnd, next.valueEnd);
                current = new MaskPosition(current.valueStart, newEnd, current.keyword);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }
}
