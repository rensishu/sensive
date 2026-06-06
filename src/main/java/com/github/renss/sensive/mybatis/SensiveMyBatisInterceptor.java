package com.github.renss.sensive.mybatis;

import com.github.renss.sensive.SensiveUtils;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.plugin.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.PreparedStatement;
import java.util.*;

/**
 * MyBatis 拦截器，在日志输出中对敏感参数值进行脱敏。
 *
 * <p>拦截 {@code ParameterHandler.setParameters()} 方法，在参数绑定到 JDBC PreparedStatement
 * 之前读取参数对象并创建快照，然后通过 SLF4J 输出脱敏后的参数日志。
 * 发送到数据库的原始值不会被修改。
 *
 * <h3>Spring Boot 注册方式（推荐）</h3>
 * <pre>
 * &#064;Configuration
 * public class MyBatisSensitiveConfig {
 *     &#064;Bean
 *     public ConfigurationCustomizer sensiveConfigurationCustomizer() {
 *         return configuration -&gt; {
 *             SensiveMyBatisInterceptor interceptor = new SensiveMyBatisInterceptor();
 *             configuration.addInterceptor(interceptor);
 *         };
 *     }
 * }
 * </pre>
 *
 * <h3>MyBatis XML 注册方式</h3>
 * <pre>
 * &lt;plugins&gt;
 *     &lt;plugin interceptor="com.github.renss.sensive.mybatis.SensiveMyBatisInterceptor"&gt;
 *         &lt;property name="enabled" value="true"/&gt;
 *     &lt;/plugin&gt;
 * &lt;/plugins&gt;
 * </pre>
 *
 * @author renss
 * @version V1.0.0
 * @since 1.0.0 2026/6/2
 */
@Intercepts({
    @Signature(type = ParameterHandler.class, method = "setParameters", args = {PreparedStatement.class})
})
public class SensiveMyBatisInterceptor implements Interceptor {

    private static final Logger LOG = LoggerFactory.getLogger(SensiveMyBatisInterceptor.class);

    private boolean enabled = true;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!enabled) return invocation.proceed();

        Object rawParams = null;
        try {
            Object target = invocation.getTarget();
            Field paramField = findField(target.getClass(), "parameterObject");
            if (paramField != null) {
                paramField.setAccessible(true);
                Object paramObject = paramField.get(target);
                rawParams = buildRawSnapshot(paramObject);
            }
        } catch (Exception ignored) {
            // fail-safe: proceed normally if reflection fails
        }

        Object result = invocation.proceed();

        if (rawParams != null) {
            // maskSql applies KV matching + text pattern scanning (方案B)
            LOG.debug("[Sensive] Parameters: {}",
                    SensiveUtils.maskSql(String.valueOf(rawParams)));
        }

        return result;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        String enabledStr = properties.getProperty("enabled");
        if (enabledStr != null) {
            this.enabled = Boolean.parseBoolean(enabledStr);
        }
    }

    // --- internal ---

    /**
     * 创建参数值的原始快照，不在此处执行脱敏。
     *
     * <p>脱敏延迟到 {@link SensiveUtils#maskSql(String)} 中执行，
     * 由 maskSql 对序列化后的字符串同时应用 KV 匹配和文本模式扫描。
     *
     * @param paramObject 参数对象
     * @return 原始参数快照
     */
    @SuppressWarnings("unchecked")
    private static Object buildRawSnapshot(Object paramObject) {
        if (paramObject == null) return null;

        if (paramObject instanceof Map) {
            Map<String, Object> raw = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) paramObject).entrySet()) {
                String key = String.valueOf(entry.getKey());
                raw.put(key, entry.getValue());
            }
            return raw;
        }

        if (paramObject instanceof String) {
            return paramObject;
        }

        // Bean: capture field names and String values only
        Map<String, Object> fieldMap = new LinkedHashMap<String, Object>();
        for (Field field : getAllFields(paramObject.getClass())) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            try {
                Object value = field.get(paramObject);
                if (value instanceof String) {
                    fieldMap.put(field.getName(), value);
                } else if (value != null) {
                    fieldMap.put(field.getName(), "{...}");
                }
            } catch (Exception ignored) {
            }
        }
        return fieldMap.isEmpty() ? paramObject : fieldMap;
    }

    private static Field findField(Class<?> clazz, String name) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<Field>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            Collections.addAll(fields, c.getDeclaredFields());
        }
        return fields;
    }
}
