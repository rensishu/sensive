package com.github.renss.sensive.engine;

import java.util.ArrayList;
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
 * @version V1.0.0
 * @since 1.0.0 2026/6/2
 */
public final class TextPatternMatcher {

    private TextPatternMatcher() {}

    /**
     * 在未被existingPositions覆盖的文本区域中扫描数字模式。
     *
     * @param text              待扫描的文本
     * @param existingPositions 已被覆盖的位置列表
     * @param enabledPatterns   启用的模式名称集合
     * @return 匹配到的脱敏位置列表
     */
    public static List<MaskPosition> scan(String text, List<MaskPosition> existingPositions,
                                           Set<String> enabledPatterns) {
        List<MaskPosition> results = new ArrayList<MaskPosition>();
        if (text == null || text.isEmpty() || enabledPatterns == null || enabledPatterns.isEmpty()) {
            return results;
        }

        int len = text.length();
        int i = 0;

        while (i < len) {
            char c = text.charAt(i);

            if (isDigitStart(c, text, i, enabledPatterns)) {
                // Found the start of a potential digit sequence
                int end = scanDigits(text, i);
                int seqLen = end - i;

                String matchedPattern = matchPattern(text, i, end, enabledPatterns);
                if (matchedPattern != null && !isCovered(i, end, existingPositions)) {
                    results.add(new MaskPosition(i, end, matchedPattern));
                }
                i = end;
            } else {
                i++;
            }
        }

        return results;
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
            // Chinese mobile: 1[3-9]xxxxxxxxx
            char first = text.charAt(start);
            char second = text.charAt(start + 1);
            if (first == '1' && second >= '3' && second <= '9') {
                return "phone";
            }
        }

        if (patterns.contains("idcard") && (len == 18 || (len == 17 && isIdcardWithX(text, start, end)))) {
            return "idcard";
        }

        if (patterns.contains("bankcard") && len >= 16 && len <= 19) {
            return "bankcard";
        }

        return null;
    }

    private static boolean isIdcardWithX(String text, int start, int end) {
        return end > start && (text.charAt(end - 1) == 'X' || text.charAt(end - 1) == 'x')
                && end - start == 18;
    }

    private static boolean isCovered(int start, int end, List<MaskPosition> existing) {
        if (existing == null) return false;
        for (MaskPosition pos : existing) {
            if (pos.valueStart <= start && pos.valueEnd >= end) return true;
        }
        return false;
    }
}
