package com.github.renss.sensive.autoconfigure;

import com.github.renss.sensive.SensiveUtils;
import com.github.renss.sensive.config.SensitiveConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Spring Boot 自动配置，从 Spring {@link Environment} 加载脱敏配置并注册 MyBatis 拦截器。
 *
 * <p>使用 {@code @Configuration} 而非 {@code @AutoConfiguration} 以保证
 * Spring Boot 2.0 ~ 3.x 全版本兼容。
 *
 * <p>配置的启用/禁用通过 {@code sensitive.enabled} 在运行时判断，
 * 而非通过类级别 {@code @ConditionalOnProperty} 在编译时跳过，
 * 确保配置初始化 Bean 始终被注册。
 */
@Configuration(proxyBeanMethods = false)
public class SensiveAutoConfiguration {

    /**
     * 从 Spring Environment 提取所有 sensitive.* 开头的属性并初始化脱敏配置。
     *
     * <p>Environment 已聚合 Apollo/Nacos/yml/properties 等多源属性，
     * 因此无需单独对接每个配置中心。
     * 首次调用在 {@link SensiveUtils} 静态初始化之前执行。
     */
    @Bean
    static SensitiveConfigInitializer sensiveConfigInitializer(Environment env) {
        Properties props = new Properties();

        if (env instanceof AbstractEnvironment) {
            AbstractEnvironment aenv = (AbstractEnvironment) env;
            MutablePropertySources sources = aenv.getPropertySources();

            // Collect sources into a reversed list so that lower-priority sources
            // are processed first, allowing higher-priority sources to overwrite.
            List<PropertySource<?>> reversed = new ArrayList<PropertySource<?>>();
            for (PropertySource<?> source : sources) {
                reversed.add(source);
            }
            Collections.reverse(reversed);

            for (PropertySource<?> source : reversed) {
                if (source.getName().contains("Bootstrap")) continue;

                if (source instanceof EnumerablePropertySource) {
                    // Primary path: use EnumerablePropertySource.getPropertyNames()
                    // Covers OriginTrackedMapPropertySource (application.yml),
                    // PropertiesPropertySource, Apollo/Nacos adapters, etc.
                    EnumerablePropertySource<?> eps = (EnumerablePropertySource<?>) source;
                    for (String key : eps.getPropertyNames()) {
                        if (key.startsWith("sensitive.")) {
                            Object value = eps.getProperty(key);
                            if (value != null) {
                                props.setProperty(key, value.toString());
                            }
                        }
                    }
                } else if (source.getSource() instanceof Map) {
                    // Fallback: legacy or custom sources exposing a raw Map
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) source.getSource();
                    for (Map.Entry<String, Object> entry : map.entrySet()) {
                        if (entry.getKey().startsWith("sensitive.") && entry.getValue() != null) {
                            props.setProperty(entry.getKey(), entry.getValue().toString());
                        }
                    }
                }
            }
        }

        // Always try Environment directly for any remaining keys that may have
        // been missed (e.g., from non-enumerable, non-Map property sources).
        // Use env.getProperty which goes through the full resolution chain.
        tryEnrichFromEnvironment(env, props);

        SensitiveConfig.reload(props);
        SensiveUtils.refreshEngine(); // rebuild engine with new config
        return new SensitiveConfigInitializer();
    }

    /**
     * 兜底补充：从 Environment 逐个尝试获取所有可能的 sensitive.* 键，
     * 覆盖非 EnumerablePropertySource 和非 Map 类型的属性源。
     */
    private static void tryEnrichFromEnvironment(Environment env, Properties props) {
        try {
            String[] prefixKeys = {"sensitive.enabled", "sensitive.keywords.", "sensitive.text-pattern.",
                    "sensitive.excludes."};
            for (String prefix : prefixKeys) {
                // Attempt to collect any key starting with this prefix
                if (env instanceof AbstractEnvironment) {
                    AbstractEnvironment aenv = (AbstractEnvironment) env;
                    for (PropertySource<?> source : aenv.getPropertySources()) {
                        if (source instanceof EnumerablePropertySource) {
                            EnumerablePropertySource<?> eps = (EnumerablePropertySource<?>) source;
                            for (String key : eps.getPropertyNames()) {
                                if (key.startsWith(prefix) && !props.containsKey(key)) {
                                    String value = env.getProperty(key);
                                    if (value != null) {
                                        props.setProperty(key, value);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 配置初始化标记类，确保 Spring 容器管理初始化生命周期。
     */
    static class SensitiveConfigInitializer {
    }
}
