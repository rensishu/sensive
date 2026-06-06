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
 * @version V1.0.0
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
    },

    NAME_MASK {
        @Override
        public String apply(String value) {
            if (value == null || value.isEmpty()) return value;
            if (value.length() == 1) return "*";
            if (value.length() == 2) return value.charAt(0) + "*";
            return value.charAt(0) + "*" + value.charAt(value.length() - 1);
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
    },

    ACCOUNT_MASK {
        @Override
        public String apply(String value) {
            if (value == null || value.length() < 5) {
                return value == null ? null : MASK_FULL;
            }
            int stars = value.length() - 4;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < stars; i++) sb.append('*');
            sb.append(value.substring(value.length() - 4));
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
            if (local.length() == 2) return local.charAt(0) + "*" + domain;
            return local.substring(0, 2) + "***" + local.charAt(local.length() - 1) + domain;
        }
    },

    ADDRESS_MASK {
        @Override
        public String apply(String value) {
            if (value == null || value.isEmpty()) return value;
            if (value.length() <= 6) return value + "***";
            return value.substring(0, 6) + "***";
        }
    },

    FULL_MASK {
        @Override
        public String apply(String value) {
            if (value == null || value.isEmpty()) return value;
            return MASK_FULL;
        }
    },

    CUSTOM_REGEX {
        @Override
        public String apply(String value) {
            // CUSTOM_REGEX is handled by RuleExecutor with custom pattern/replacement
            return value;
        }
    };

    static final String MASK_FULL = "****";

    /**
     * 对指定值执行脱敏。
     *
     * @param value 待脱敏的原始值
     * @return 脱敏后的字符串，null 或空字符串原样返回
     */
    public abstract String apply(String value);

    /**
     * 根据名称查找对应的规则类型，忽略大小写。
     *
     * @param name 规则类型名称（如 "PHONE_MASK"）
     * @return 匹配的规则类型，未找到返回 null
     */
    public static RuleType fromName(String name) {
        for (RuleType type : values()) {
            if (type.name().equalsIgnoreCase(name)) return type;
        }
        return null;
    }
}
