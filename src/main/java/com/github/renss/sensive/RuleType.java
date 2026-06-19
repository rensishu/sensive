package com.github.renss.sensive;

/**
 * 内置脱敏规则类型枚举。
 *
 * <p>每种规则类型实现不同的脱敏策略：
 * <ul>
 *   <li>{@link #PHONE_MASK} — 手机号：保留前3后4</li>
 *   <li>{@link #NAME_MASK}   — 姓名：2字留姓，3字+留首尾</li>
 *   <li>{@link #IDCARD_MASK} — 身份证：保留前6后4</li>
 *   <li>{@link #ACCOUNT_MASK} — 银行卡/账号：仅保留后4位</li>
 *   <li>{@link #EMAIL_MASK}  — 邮箱：用户名保留前2后1</li>
 *   <li>{@link #ADDRESS_MASK} — 地址：保留前6字</li>
 *   <li>{@link #FULL_MASK}   — 全量隐藏：替换为 ****</li>
 *   <li>{@link #CUSTOM_REGEX} — 自定义正则：由 {@link com.github.renss.sensive.config.model.CustomRule} 处理</li>
 * </ul>
 *
 * @author renss
 * @version V1.2.0
 * @since 1.0.0 2026/6/2
 */
public enum RuleType {

    PHONE_MASK {
        @Override
        public String apply(String value) {
            if (value == null || value.length() < 7) {
                return value == null ? null : MASK_FULL;
            }
            return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
        }

        @Override
        public String applyFast(CharSequence text, int start, int end) {
            int len = end - start;
            if (len < 7) return MASK_FULL;
            return new StringBuilder(11)
                    .append(text, start, start + 3)
                    .append("****")
                    .append(text, end - 4, end)
                    .toString();
        }
    },

    NAME_MASK {
        @Override
        public String apply(String value) {
            if (value == null || value.isEmpty()) return value;
            if (value.length() == 1) return "*";
            if (value.length() == 2) return new StringBuilder(2).append(value.charAt(0)).append('*').toString();
            return new StringBuilder(3).append(value.charAt(0)).append('*')
                    .append(value.charAt(value.length() - 1)).toString();
        }

        @Override
        public String applyFast(CharSequence text, int start, int end) {
            int len = end - start;
            if (len == 0) return "";
            if (len == 1) return "*";
            if (len == 2) return new StringBuilder(2).append(text.charAt(start)).append('*').toString();
            return new StringBuilder(3).append(text.charAt(start)).append('*')
                    .append(text.charAt(end - 1)).toString();
        }
    },

    IDCARD_MASK {
        @Override
        public String apply(String value) {
            if (value == null || value.length() < 10) {
                return value == null ? null : MASK_FULL;
            }
            return value.substring(0, 6) + "********" + value.substring(value.length() - 4);
        }

        @Override
        public String applyFast(CharSequence text, int start, int end) {
            int len = end - start;
            if (len < 10) return MASK_FULL;
            return new StringBuilder(18)
                    .append(text, start, start + 6)
                    .append("********")
                    .append(text, end - 4, end)
                    .toString();
        }
    },

    ACCOUNT_MASK {
        @Override
        public String apply(String value) {
            if (value == null || value.length() < 5) {
                return value == null ? null : MASK_FULL;
            }
            int stars = value.length() - 4;
            // Pre-size to avoid internal StringBuilder resizing
            StringBuilder sb = new StringBuilder(stars + 4);
            for (int i = 0; i < stars; i++) sb.append('*');
            // Use append(charSequence, start, end) to avoid substring allocation
            sb.append(value, value.length() - 4, value.length());
            return sb.toString();
        }

        @Override
        public String applyFast(CharSequence text, int start, int end) {
            int len = end - start;
            if (len < 5) return MASK_FULL;
            int stars = len - 4;
            // Pre-size to avoid internal StringBuilder resizing
            StringBuilder sb = new StringBuilder(stars + 4);
            for (int i = 0; i < stars; i++) sb.append('*');
            sb.append(text, end - 4, end);
            return sb.toString();
        }
    },

    EMAIL_MASK {
        @Override
        public String apply(String value) {
            if (value == null || value.isEmpty()) return value;
            int at = value.indexOf('@');
            if (at <= 0) return MASK_FULL;
            String local = value.substring(0, at);
            String domain = value.substring(at);
            if (local.length() == 1) return "*" + domain;
            if (local.length() == 2) return new StringBuilder(2 + domain.length())
                    .append(local.charAt(0)).append('*').append(domain).toString();
            return new StringBuilder(7 + domain.length())
                    .append(local, 0, 2)
                    .append("***")
                    .append(local.charAt(local.length() - 1))
                    .append(domain)
                    .toString();
        }

        @Override
        public String applyFast(CharSequence text, int start, int end) {
            int len = end - start;
            if (len == 0) return "";
            // Find @ within [start, end)
            int at = -1;
            for (int i = start; i < end; i++) {
                if (text.charAt(i) == '@') {
                    at = i;
                    break;
                }
            }
            if (at <= start) return MASK_FULL;
            int localLen = at - start;
            int domainLen = end - at;
            if (localLen == 1) return new StringBuilder(1 + domainLen)
                    .append('*').append(text, at, end).toString();
            if (localLen == 2) return new StringBuilder(2 + domainLen)
                    .append(text.charAt(start)).append('*').append(text, at, end).toString();
            return new StringBuilder(7 + domainLen)
                    .append(text, start, start + 2)
                    .append("***")
                    .append(text.charAt(at - 1))
                    .append(text, at, end)
                    .toString();
        }
    },

    ADDRESS_MASK {
        @Override
        public String apply(String value) {
            if (value == null || value.isEmpty()) return value;
            if (value.length() <= 6) return value + "***";
            return value.substring(0, 6) + "***";
        }

        @Override
        public String applyFast(CharSequence text, int start, int end) {
            int len = end - start;
            if (len == 0) return "";
            if (len <= 6) return new StringBuilder(len + 3)
                    .append(text, start, end).append("***").toString();
            return new StringBuilder(9)
                    .append(text, start, start + 6).append("***").toString();
        }
    },

    FULL_MASK {
        @Override
        public String apply(String value) {
            if (value == null || value.isEmpty()) return value;
            return MASK_FULL;
        }

        @Override
        public String applyFast(CharSequence text, int start, int end) {
            if (end <= start) return "";
            return MASK_FULL;
        }
    },

    CUSTOM_REGEX {
        @Override
        public String apply(String value) {
            // CUSTOM_REGEX is handled by RuleExecutor with custom pattern/replacement
            return value;
        }

        @Override
        public String applyFast(CharSequence text, int start, int end) {
            // CUSTOM_REGEX must go through CustomRule — delegate to apply() with substring
            return apply(text.subSequence(start, end).toString());
        }
    };

    static final String MASK_FULL = "****";

    /** Cache values() array to avoid per-call clone allocation. */
    private static final RuleType[] VALUES = values();

    /**
     * 对指定值执行脱敏。
     *
     * @param value 待脱敏的原始值
     * @return 脱敏后的字符串，null 或空字符串原样返回
     */
    public abstract String apply(String value);

    /**
     * 对 CharSequence 中指定区间的值执行脱敏（引擎快速路径，避免 substring 分配）。
     *
     * <p>供引擎内部使用。默认委托给 {@link #apply(String)}，
     * 各枚举常量覆写此方法以直接操作 CharSequence。
     *
     * @param text  包含值的文本
     * @param start 值起始位置（含）
     * @param end   值结束位置（不含）
     * @return 脱敏后的字符串
     */
    public String applyFast(CharSequence text, int start, int end) {
        return apply(text.subSequence(start, end).toString());
    }

    /**
     * 根据名称查找对应的规则类型，忽略大小写。
     *
     * @param name 规则类型名称（如 "PHONE_MASK"）
     * @return 匹配的规则类型，未找到返回 null
     */
    public static RuleType fromName(String name) {
        for (RuleType type : VALUES) {
            if (type.name().equalsIgnoreCase(name)) return type;
        }
        return null;
    }
}
