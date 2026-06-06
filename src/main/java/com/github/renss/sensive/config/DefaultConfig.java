package com.github.renss.sensive.config;

import com.github.renss.sensive.RuleType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内置的默认关键字到规则的映射。
 * 包含35+个常用敏感数据关键字的预定义映射。
 *
 * @author renss
 * @version V1.0.0
 * @since 1.0.0 2026/6/2
 */
public final class DefaultConfig {

    private DefaultConfig() {}

    /**
     * 获取默认的关键字到规则类型的映射表。
     *
     * @return 包含所有内置关键字映射的LinkedHashMap
     */
    public static Map<String, RuleType> defaultKeywords() {
        Map<String, RuleType> map = new LinkedHashMap<String, RuleType>();

        // Phone numbers
        map.put("phone", RuleType.PHONE_MASK);
        map.put("phoneno", RuleType.PHONE_MASK);
        map.put("phone_no", RuleType.PHONE_MASK);
        map.put("phonenum", RuleType.PHONE_MASK);
        map.put("phone_num", RuleType.PHONE_MASK);
        map.put("mobile", RuleType.PHONE_MASK);
        map.put("mobileno", RuleType.PHONE_MASK);
        map.put("mobile_no", RuleType.PHONE_MASK);
        map.put("usermobile", RuleType.PHONE_MASK);
        map.put("mobilephone", RuleType.PHONE_MASK);
        map.put("tel", RuleType.PHONE_MASK);
        map.put("telephone", RuleType.PHONE_MASK);

        // Names
        map.put("name", RuleType.NAME_MASK);
        map.put("username", RuleType.NAME_MASK);
        map.put("realname", RuleType.NAME_MASK);
        map.put("real_name", RuleType.NAME_MASK);
        map.put("nickname", RuleType.NAME_MASK);
        map.put("nick_name", RuleType.NAME_MASK);
        map.put("customername", RuleType.NAME_MASK);
        map.put("customer_name", RuleType.NAME_MASK);

        // ID card
        map.put("idcard", RuleType.IDCARD_MASK);
        map.put("idcardno", RuleType.IDCARD_MASK);
        map.put("id_card", RuleType.IDCARD_MASK);
        map.put("id_number", RuleType.IDCARD_MASK);
        map.put("idno", RuleType.IDCARD_MASK);
        map.put("identitycard", RuleType.IDCARD_MASK);
        map.put("identity_card", RuleType.IDCARD_MASK);
        map.put("identityno", RuleType.IDCARD_MASK);
        map.put("certificateno", RuleType.IDCARD_MASK);

        // Account / bank card
        map.put("accountno", RuleType.ACCOUNT_MASK);
        map.put("account_number", RuleType.ACCOUNT_MASK);
        map.put("bankcardno", RuleType.ACCOUNT_MASK);
        map.put("bankcard", RuleType.ACCOUNT_MASK);
        map.put("bank_card_no", RuleType.ACCOUNT_MASK);
        map.put("cardno", RuleType.ACCOUNT_MASK);
        map.put("cardnumber", RuleType.ACCOUNT_MASK);
        map.put("card_number", RuleType.ACCOUNT_MASK);

        // Email
        map.put("email", RuleType.EMAIL_MASK);
        map.put("mail", RuleType.EMAIL_MASK);

        // Address
        map.put("address", RuleType.ADDRESS_MASK);
        map.put("addr", RuleType.ADDRESS_MASK);

        // Credentials
        map.put("password", RuleType.FULL_MASK);
        map.put("passwd", RuleType.FULL_MASK);
        map.put("pwd", RuleType.FULL_MASK);
        map.put("secret", RuleType.FULL_MASK);
        map.put("token", RuleType.FULL_MASK);
        map.put("accesstoken", RuleType.FULL_MASK);
        map.put("access_token", RuleType.FULL_MASK);

        return map;
    }
}
