package com.github.renss.sensive.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * 基于 Trie 前缀树的关键字匹配器，支持大小写不敏感匹配。
 *
 * <p>一次扫描文本即可找到所有关键字匹配，复杂度 O(n)。
 * 使用标识符边界检测防止词内误匹配（如 "name" 不会匹配 "productName" 中的 "name"）。
 * Trie 节点采用数组+HashMap 混合结构：ASCII 字母数字用数组 O(1) 查找，非 ASCII 字符回退 HashMap。
 *
 * @author renss
 * @version V1.0.0
 * @since 1.0.0 2026/6/2
 */
public class KeywordMatcher {

    private final TrieNode root = new TrieNode();

    public void addKeyword(String keyword) {
        if (keyword == null || keyword.isEmpty()) return;
        TrieNode node = root;
        for (int i = 0; i < keyword.length(); i++) {
            char c = toLowerFast(keyword.charAt(i));
            TrieNode child = node.getChild(c);
            if (child == null) {
                child = new TrieNode();
                node.setChild(c, child);
            }
            node = child;
        }
        node.isEnd = true;
        node.keyword = keyword;
    }

    /**
     * 在文本指定位置查找最长匹配的关键字。
     *
     * <p>仅匹配标识符边界 — 关键字前面的字符不能是字母、数字或下划线，
     * 防止词内误匹配（如 "name" 匹配到 "productName"）。
     *
     * @param text  文本
     * @param start 起始位置
     * @return 匹配结果，未匹配到返回 null
     */
    public MatchResult matchAt(String text, int start) {
        // Word boundary check: char before keyword must not be alphanumeric
        if (start > 0 && isIdentifierChar(text.charAt(start - 1))) {
            return null;
        }

        TrieNode node = root;
        int longestMatch = -1;
        String matchedKeyword = null;

        for (int i = start; i < text.length(); i++) {
            char c = toLowerFast(text.charAt(i));
            TrieNode child = node.getChild(c);
            if (child == null) break;
            node = child;
            if (node.isEnd) {
                longestMatch = i;
                matchedKeyword = node.keyword;
            }
        }

        if (matchedKeyword == null) return null;
        return new MatchResult(start, longestMatch, matchedKeyword);
    }

    /**
     * ASCII 字符快速小写转换，避免 Character.toLowerCase() 的方法调用开销。
     */
    private static char toLowerFast(char c) {
        if (c >= 'A' && c <= 'Z') {
            return (char) (c + 32);
        }
        return c;
    }

    /**
     * 检查字符是否为标识符字符（字母、数字、下划线）。
     *
     * <p>用于关键字词边界检测：关键字前面的字符不能是标识符字符，
     * 否则说明该关键字是更长标识符的一部分。
     * 下划线视为标识符字符——"product_name" 是一个整体，不是一个词。
     */
    private static boolean isIdentifierChar(char c) {
        return (c >= 'a' && c <= 'z') ||
               (c >= 'A' && c <= 'Z') ||
               (c >= '0' && c <= '9') ||
               c == '_';
    }

    public boolean isEmpty() {
        return root.isEmpty();
    }

    public static class MatchResult {
        public final int keyStart;
        public final int keyEnd;
        public final String keyword;

        MatchResult(int keyStart, int keyEnd, String keyword) {
            this.keyStart = keyStart;
            this.keyEnd = keyEnd;
            this.keyword = keyword;
        }
    }

    /**
     * 优化的 Trie 节点，ASCII 字符使用数组实现 O(1) 查找，非 ASCII 字符回退 HashMap。
     * 数组和 Map 均延迟初始化以降低内存占用。
     *
     * <p>索引映射：'a'-'z' → 0-25，'0'-'9' → 26-35。
     *
     * @author renss
     * @version V1.0.0
     * @since 1.0.0 2026/6/2
     */
    private static class TrieNode {
        // Direct array for ASCII letters and digits (a-z, 0-9)
        // Index mapping: 'a'-'z' -> 0-25, '0'-'9' -> 26-35
        private static final int ARRAY_SIZE = 36;
        
        TrieNode[] arrayChildren;  // Lazy initialized for memory efficiency
        Map<Character, TrieNode> mapChildren;  // Fallback for non-ASCII chars
        boolean isEnd;
        String keyword;

        TrieNode getChild(char c) {
            int idx = charToIndex(c);
            if (idx >= 0) {
                return arrayChildren != null ? arrayChildren[idx] : null;
            }
            return mapChildren != null ? mapChildren.get(c) : null;
        }

        void setChild(char c, TrieNode child) {
            int idx = charToIndex(c);
            if (idx >= 0) {
                if (arrayChildren == null) {
                    arrayChildren = new TrieNode[ARRAY_SIZE];
                }
                arrayChildren[idx] = child;
            } else {
                if (mapChildren == null) {
                    mapChildren = new HashMap<Character, TrieNode>();
                }
                mapChildren.put(c, child);
            }
        }

        /**
         * 将字符转换为数组索引：'a'-'z' → 0-25，'0'-'9' → 26-35，其他 → -1。
         */
        private static int charToIndex(char c) {
            if (c >= 'a' && c <= 'z') {
                return c - 'a';
            }
            if (c >= '0' && c <= '9') {
                return 26 + (c - '0');
            }
            return -1;
        }

        boolean isEmpty() {
            if (arrayChildren != null) {
                for (TrieNode child : arrayChildren) {
                    if (child != null) return false;
                }
            }
            return mapChildren == null || mapChildren.isEmpty();
        }
    }
}
