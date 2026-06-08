package com.github.renss.sensive.config;

import com.github.renss.sensive.RuleType;
import com.github.renss.sensive.config.SensitiveConfig.TextPatternConfig;
import com.github.renss.sensive.config.model.CustomRule;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 从内置默认配置和外部源加载并合并配置。
 * 优先尝试YAML格式（如果SnakeYAML可用），回退到properties格式。
 *
 * @author renss
 * @version V1.0.0
 * @since 1.0.0 2026/6/2
 */
public class ConfigLoader {

    private static final String CONFIG_YML = "sensitive.yml";
    private static final String CONFIG_PROPERTIES = "sensitive.properties";

    /**
     * 加载配置，合并内置默认值、外部YAML或properties文件。
     *
     * @return 包含所有配置信息的ConfigHolder
     */
    public static ConfigHolder load() {
        return loadInternal(null);
    }

    /**
     * 使用外部 Properties 加载配置（Spring Environment / Apollo / Nacos）。
     *
     * <p>优先级：Properties（最高）→ classpath sensitive.yml → 内置默认（最低）
     *
     * @param props 包含 sensitive.* 前缀的 Properties，可为 null
     * @return 包含所有配置信息的ConfigHolder
     */
    public static ConfigHolder loadFromProperties(Properties props) {
        return loadInternal(props);
    }

    /**
     * 合并加载逻辑：内置默认 → classpath文件 → Properties覆写（最高优先级）
     */
    private static ConfigHolder loadInternal(Properties overrideProps) {
        Map<String, RuleType> keywords = new LinkedHashMap<String, RuleType>();
        Map<String, CustomRule> customRules = new LinkedHashMap<String, CustomRule>();
        Set<String> excludes = new HashSet<String>();
        boolean enabled = true;

        // Layer 1: built-in defaults
        keywords.putAll(DefaultConfig.defaultKeywords());

        // Layer 2: external config file (yml preferred, properties as fallback)
        ExternalConfig external = loadExternalConfig();
        if (external != null) {
            enabled = external.enabled;
            if (external.keywords != null) {
                keywords.putAll(external.keywords);
            }
            if (external.customRules != null) {
                for (CustomRule rule : external.customRules) {
                    if (rule.getKeyword() != null) {
                        customRules.put(rule.getKeyword().toLowerCase(), rule);
                    }
                }
            }
            if (external.excludes != null) {
                for (String exclude : external.excludes) {
                    keywords.remove(exclude.toLowerCase());
                }
            }
        }

        // Layer 3: Spring Environment / Apollo / Nacos properties (highest priority)
        ExternalConfig overrideConfig = null;
        if (overrideProps != null) {
            overrideConfig = parsePropertiesFromObject(overrideProps);
        }
        if (overrideConfig != null) {
            if (overrideConfig.keywords != null) {
                keywords.putAll(overrideConfig.keywords);
            }
            if (overrideConfig.customRules != null) {
                for (CustomRule rule : overrideConfig.customRules) {
                    if (rule.getKeyword() != null) {
                        customRules.put(rule.getKeyword().toLowerCase(), rule);
                    }
                }
            }
            if (overrideConfig.excludes != null) {
                for (String exclude : overrideConfig.excludes) {
                    keywords.remove(exclude.toLowerCase());
                }
            }
            enabled = overrideConfig.enabled;
        }

        // textPattern: overrideProps → external → defaults
        TextPatternConfig textPattern;
        if (overrideConfig != null && overrideConfig.textPattern != null) {
            textPattern = overrideConfig.textPattern;
        } else if (external != null) {
            textPattern = external.textPattern;
        } else {
            textPattern = new TextPatternConfig(false, TextPatternConfig.defaultPatterns());
        }

        return new ConfigHolder(keywords, customRules, excludes, enabled, textPattern);
    }

    /**
     * 从外部文件加载配置，优先尝试YAML，其次尝试properties格式。
     *
     * @return 外部配置对象，如果加载失败则返回null
     */
    private static ExternalConfig loadExternalConfig() {
        // Try YAML first
        InputStream is = ConfigLoader.class.getClassLoader().getResourceAsStream(CONFIG_YML);
        if (is != null) {
            try {
                ExternalConfig config = parseYaml(is);
                if (config != null) return config;
            } catch (Exception ignored) {
                // Fall through to properties
            }
        }

        // Try properties format
        is = ConfigLoader.class.getClassLoader().getResourceAsStream(CONFIG_PROPERTIES);
        if (is != null) {
            try {
                return parseProperties(is);
            } catch (Exception ignored) {
                // No valid external config
            }
        }

        return null;
    }

    /**
     * 通过反射调用SnakeYAML解析YAML配置输入流。
     *
     * @param is YAML文件的输入流
     * @return 解析后的外部配置对象
     * @throws Exception 当反射调用或解析失败时抛出
     */
    @SuppressWarnings("unchecked")
    private static ExternalConfig parseYaml(InputStream is) throws Exception {
        // Use reflection to avoid hard dependency on SnakeYAML
        try {
            Class<?> yamlClass = Class.forName("org.yaml.snakeyaml.Yaml");
            Object yaml = yamlClass.newInstance();
            Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
            Object result = yamlClass.getMethod("load", Reader.class).invoke(yaml, reader);
            reader.close();

            if (result instanceof Map) {
                return parseYamlConfig((Map<String, Object>) result);
            }
        } catch (ClassNotFoundException e) {
            // SnakeYAML not available
        }
        return null;
    }

    /**
     * 将YAML解析后的Map结构转换为外部配置对象。
     *
     * @param root YAML解析后的根Map
     * @return 外部配置对象
     */
    @SuppressWarnings("unchecked")
    private static ExternalConfig parseYamlConfig(Map<String, Object> root) {
        ExternalConfig config = new ExternalConfig();

        Object sensitive = root.get("sensitive");
        if (!(sensitive instanceof Map)) return config;

        Map<String, Object> sensitiveMap = (Map<String, Object>) sensitive;

        // enabled
        Object enabledObj = sensitiveMap.get("enabled");
        if (enabledObj instanceof Boolean) {
            config.enabled = (Boolean) enabledObj;
        }

        // keywords
        Object kwObj = sensitiveMap.get("keywords");
        if (kwObj instanceof Map) {
            Map<String, Object> kwMap = (Map<String, Object>) kwObj;
            config.keywords = new LinkedHashMap<String, RuleType>();
            parseKeywords(kwMap, config.keywords);
        }

        // rules
        Object rulesObj = sensitiveMap.get("rules");
        if (rulesObj instanceof List) {
            List<Object> rulesList = (List<Object>) rulesObj;
            config.customRules = new ArrayList<CustomRule>();
            for (Object ruleObj : rulesList) {
                if (ruleObj instanceof Map) {
                    Map<String, Object> ruleMap = (Map<String, Object>) ruleObj;
                    CustomRule rule = new CustomRule();
                    rule.setName(toStringOrNull(ruleMap.get("name")));
                    rule.setKeyword(toStringOrNull(ruleMap.get("keyword")));
                    rule.setType(toStringOrNull(ruleMap.get("type")));
                    rule.setBuiltin(toStringOrNull(ruleMap.get("builtin")));
                    rule.setPattern(toStringOrNull(ruleMap.get("pattern")));
                    rule.setReplacement(toStringOrNull(ruleMap.get("replacement")));
                    config.customRules.add(rule);
                }
            }
        }

        // excludes
        Object excludesObj = sensitiveMap.get("excludes");
        if (excludesObj instanceof List) {
            config.excludes = new HashSet<String>();
            for (Object ex : (List<Object>) excludesObj) {
                config.excludes.add(String.valueOf(ex).toLowerCase());
            }
        }

        // textPattern
        Object tpObj = sensitiveMap.get("textPattern");
        if (tpObj instanceof Map) {
            Map<String, Object> tpMap = (Map<String, Object>) tpObj;
            boolean tpEnabled = Boolean.TRUE.equals(tpMap.get("enabled"));
            Set<String> tpPatterns = new HashSet<String>();
            Object patternsObj = tpMap.get("patterns");
            if (patternsObj instanceof List) {
                for (Object p : (List<Object>) patternsObj) {
                    tpPatterns.add(String.valueOf(p).toLowerCase());
                }
            }
            config.textPattern = new TextPatternConfig(tpEnabled, tpPatterns);
        } else {
            config.textPattern = new TextPatternConfig(false, Collections.<String>emptySet());
        }

        return config;
    }

    /**
     * 解析properties格式的外部配置文件。
     *
     * @param is properties文件的输入流
     * @return 解析后的外部配置对象
     * @throws Exception 当读取或解析失败时抛出
     */
    private static ExternalConfig parseProperties(InputStream is) throws Exception {
        Properties props = new Properties();
        props.load(is);
        is.close();

        ExternalConfig config = new ExternalConfig();

        String enabledStr = props.getProperty("sensitive.enabled");
        if (enabledStr != null) {
            config.enabled = Boolean.parseBoolean(enabledStr);
        }

        config.keywords = new LinkedHashMap<String, RuleType>();
        for (String name : props.stringPropertyNames()) {
            if (name.startsWith("sensitive.keywords.")) {
                String key = name.substring("sensitive.keywords.".length());
                String value = props.getProperty(name);
                parsePropertyKeyword(key, value, config.keywords);
            } else if (name.startsWith("sensitive.excludes.")) {
                String exclude = name.substring("sensitive.excludes.".length());
                if ("true".equalsIgnoreCase(props.getProperty(name))) {
                    if (config.excludes == null) config.excludes = new HashSet<String>();
                    config.excludes.add(exclude.toLowerCase());
                }
            }
        }

        return config;
    }

    /**
     * 解析单个属性键值对中的关键字映射，支持两种格式：
     * 格式1：key=keyword, value=RULE_TYPE（旧格式）
     * 格式2：key=RULE_TYPE, value=keyword1, keyword2, ...（新格式，逗号分隔）
     */
    private static void parsePropertyKeyword(String key, String value, Map<String, RuleType> target) {
        RuleType keyAsRule = RuleType.fromName(key);
        if (keyAsRule != null && RuleType.fromName(value) == null) {
            // Format 2: key IS a valid RuleType → expand comma-separated keywords
            for (String kw : value.split(",")) {
                kw = kw.trim().toLowerCase();
                if (!kw.isEmpty()) {
                    target.put(kw, keyAsRule);
                }
            }
        } else {
            // Format 1: key is a keyword, value is a rule type name
            RuleType type = RuleType.fromName(value);
            if (type != null) {
                target.put(key.toLowerCase(), type);
            }
        }
    }

    /**
     * 从 Properties 对象解析配置（用于 Spring Environment / Apollo / Nacos）。
     *
     * @param props 包含 sensitive.* 前缀属性的 Properties 对象
     * @return 解析后的外部配置对象，如果无有效配置则返回 null
     */
    private static ExternalConfig parsePropertiesFromObject(Properties props) {
        if (props == null || props.isEmpty()) return null;

        ExternalConfig config = new ExternalConfig();

        String enabledStr = props.getProperty("sensitive.enabled");
        if (enabledStr != null) {
            config.enabled = Boolean.parseBoolean(enabledStr);
        }

        config.keywords = new LinkedHashMap<String, RuleType>();
        config.excludes = new HashSet<String>();

        for (String name : props.stringPropertyNames()) {
            if (name.startsWith("sensitive.keywords.")) {
                String key = name.substring("sensitive.keywords.".length());
                String value = props.getProperty(name);
                parsePropertyKeyword(key, value, config.keywords);
            } else if (name.startsWith("sensitive.excludes.")) {
                String exclude = name.substring("sensitive.excludes.".length());
                if ("true".equalsIgnoreCase(props.getProperty(name))) {
                    config.excludes.add(exclude.toLowerCase());
                }
            }
        }

        // textPattern
        String tpEnabled = props.getProperty("sensitive.text-pattern.enabled");
        if (tpEnabled != null) {
            Set<String> patterns = new HashSet<String>();
            String patternsStr = props.getProperty("sensitive.text-pattern.patterns");
            if (patternsStr != null && !patternsStr.trim().isEmpty()) {
                for (String p : patternsStr.split(",")) {
                    patterns.add(p.trim().toLowerCase());
                }
            }
            config.textPattern = new TextPatternConfig(
                    Boolean.parseBoolean(tpEnabled), patterns);
        }

        return config;
    }

    /**
     * 从配置映射中解析关键字，支持两种格式：
     *
     * <pre>
     * # 格式1（旧）：关键字 → 规则类型
     * keywords:
     *   phone: PHONE_MASK
     *   name: NAME_MASK
     *
     * # 格式2（新）：规则类型 → 关键字合集（YAML 列表或逗号分隔字符串）
     * keywords:
     *   PHONE_MASK:
     *     - phone
     *     - phoneno
     *     - mobile
     *   PHONE_MASK: phone, phoneno, mobile, mobileno, telephone
     * </pre>
     */
    @SuppressWarnings("unchecked")
    private static void parseKeywords(Map<String, Object> kwMap, Map<String, RuleType> target) {
        for (Map.Entry<String, Object> entry : kwMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // Format 2: key IS a valid RuleType → expand value into multiple keywords
            RuleType keyAsRule = RuleType.fromName(key);
            if (keyAsRule != null) {
                if (value instanceof List) {
                    for (Object item : (List<Object>) value) {
                        target.put(String.valueOf(item).trim().toLowerCase(), keyAsRule);
                    }
                    continue;
                }
                if (value instanceof String && RuleType.fromName((String) value) == null) {
                    // Comma-separated list, value does NOT look like a RuleType name
                    for (String kw : ((String) value).split(",")) {
                        kw = kw.trim().toLowerCase();
                        if (!kw.isEmpty()) {
                            target.put(kw, keyAsRule);
                        }
                    }
                    continue;
                }
            }

            // Format 1: value is a RuleType name → key → value
            RuleType type = RuleType.fromName(String.valueOf(value));
            if (type != null) {
                target.put(key.toLowerCase(), type);
            }
        }
    }

    private static String toStringOrNull(Object obj) {
        return obj != null ? String.valueOf(obj) : null;
    }

    /**
     * 配置持有者，封装所有加载后的配置信息。
     *
     * @author renss
     * @version V1.0.0
     * @since 1.0.0 2026/6/2
     */
    public static class ConfigHolder {
        /** 关键字到规则类型的映射 */
        public final Map<String, RuleType> keywords;
        /** 自定义规则映射 */
        public final Map<String, CustomRule> customRules;
        /** 排除的关键字集合 */
        public final Set<String> excludes;
        /** 是否启用脱敏 */
        public final boolean enabled;
        /** 文本模式配置 */
        public final TextPatternConfig textPattern;

        /**
         * 构造配置持有者。
         *
         * @param keywords    关键字映射
         * @param customRules 自定义规则映射
         * @param excludes    排除关键字集合
         * @param enabled     是否启用
         * @param textPattern 文本模式配置
         */
        ConfigHolder(Map<String, RuleType> keywords, Map<String, CustomRule> customRules,
                     Set<String> excludes, boolean enabled, TextPatternConfig textPattern) {
            this.keywords = keywords;
            this.customRules = customRules;
            this.excludes = excludes;
            this.enabled = enabled;
            this.textPattern = textPattern;
        }
    }

    /**
     * 外部配置的内部表示，用于合并前暂存解析结果。
     *
     * @author renss
     * @version V1.0.0
     * @since 1.0.0 2026/6/2
     */
    private static class ExternalConfig {
        /** 是否启用 */
        boolean enabled = true;
        /** 关键字映射 */
        Map<String, RuleType> keywords;
        /** 自定义规则列表 */
        List<CustomRule> customRules;
        /** 排除关键字集合 */
        Set<String> excludes;
        /** 文本模式配置 */
        TextPatternConfig textPattern;
    }
}
