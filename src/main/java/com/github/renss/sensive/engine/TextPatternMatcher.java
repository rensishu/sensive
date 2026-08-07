package com.github.renss.sensive.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 扫描未被脱敏的文本区域，查找匹配敏感数据格式的数字模式。
 *
 * O(n)单次扫描，不使用正则表达式。仅在未被KV匹配位置覆盖的文本区域中进行扫描。
 *
 * 支持的模式：
 *   - phone:   11位数字，以1[3-9]开头（中国手机号）
 *   - idcard:  18位数字，或17位数字+X/x（身份证号）
 *   - bankcard: 16-19位连续数字（银行卡号）
 *
 * @author renss
 * @version V1.2.0
 * @since 1.0.0 2026/6/2
 */
public final class TextPatternMatcher {

    private TextPatternMatcher() {}

    /**
     * 在未被existingPositions覆盖的文本区域中扫描数字模式。
     *
     * <p>ArrayList 延迟分配 — 无匹配时不产生对象分配。
     * isCovered 检查使用 O(n+m) 游标，而非 O(n*m) 线性扫描。
     *
     * @param text              待扫描的文本
     * @param existingPositions 已被覆盖的位置列表（按 valueStart 排序）
     * @param enabledPatterns   启用的模式名称集合
     * @return 匹配到的脱敏位置列表
     */
    public static List<MaskPosition> scan(String text, List<MaskPosition> existingPositions,
                                           Set<String> enabledPatterns) {
        if (text == null || text.isEmpty() || enabledPatterns == null || enabledPatterns.isEmpty()) {
            return Collections.emptyList();
        }

        // Deferred allocation: only create ArrayList when a match is found
        List<MaskPosition> results = null;
        int len = text.length();
        int i = 0;
        // Cursor into sorted existingPositions — O(n+m) instead of O(n*m)
        int existingIdx = 0;
        int existingSize = existingPositions != null ? existingPositions.size() : 0;

        while (i < len) {
            char c = text.charAt(i);

            if (isDigitStart(c, text, i, enabledPatterns)) {
                int end = scanDigits(text, i);

                // Advance existingIdx past positions that end before our start
                while (existingIdx < existingSize
                        && existingPositions.get(existingIdx).valueEnd <= i) {
                    existingIdx++;
                }

                // Check if [i, end) is covered by current existing position
                boolean covered = false;
                if (existingIdx < existingSize) {
                    MaskPosition existing = existingPositions.get(existingIdx);
                    if (existing.valueStart <= i && existing.valueEnd >= end) {
                        covered = true;
                    }
                }

                if (!covered) {
                    String matchedPattern = matchPattern(text, i, end, enabledPatterns);
                    if (matchedPattern != null && !isLetterBefore(text, i)) {
                        if (results == null) {
                            results = new ArrayList<MaskPosition>(4);
                        }
                        results.add(new MaskPosition(i, end, matchedPattern));
                    }
                }
                i = end;
            } else {
                i++;
            }
        }

        return results == null ? Collections.<MaskPosition>emptyList() : results;
    }

    private static boolean isLetterBefore(String text, int pos) {
        if (pos == 0) return false;
        char prev = text.charAt(pos - 1);
        return (prev >= 'a' && prev <= 'z') ||
                (prev >= 'A' && prev <= 'Z') ||
                prev == '_' ||
                prev == '-' ||
                prev == '.';
    }

    private static boolean isDigitStart(char c, String text, int pos, Set<String> patterns) {
        if (c < '0' || c > '9') return false;
        if (pos == 0) return true;
        char prev = text.charAt(pos - 1);
        return prev < '0' || prev > '9';
    }

    /**
     * 从起始位置向前扫描，找到连续数字序列的结束位置（身份证号可能以X/x结尾）。
     *
     * @param text  待扫描的文本
     * @param start 起始位置
     * @return 数字序列的结束位置
     */
    private static int scanDigits(String text, int start) {
        int i = start;
        int len = text.length();

        while (i < len && text.charAt(i) >= '0' && text.charAt(i) <= '9') {
            i++;
        }
        // Check for trailing X in ID card (18th character after 17 digits)
        if (i - start == 17 && i < len) {
            char trail = text.charAt(i);
            if (trail == 'X' || trail == 'x') {
                i++;
            }
        }
        return i;
    }

    private static String matchPattern(String text, int start, int end, Set<String> patterns) {
        int len = end - start;

        if (patterns.contains("phone") && len == 11) {
            char first = text.charAt(start);
            char second = text.charAt(start + 1);
            if (first == '1' && second >= '3' && second <= '9') {
                return "phone";
            }
        }

        // idcard: 18 digits, or 17 digits + trailing X/x (scanDigits already counts X as part of sequence)
        if (patterns.contains("idcard") && len == 18) {
            return "idcard";
        }

        if (patterns.contains("bankcard") && len >= 16 && len <= 19) {
            return "bankcard";
        }

        return null;
    }
}
