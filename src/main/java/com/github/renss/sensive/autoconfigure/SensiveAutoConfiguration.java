package com.github.renss.sensive.autoconfigure;

import com.github.renss.sensive.SensiveUtils;
import com.github.renss.sensive.config.SensitiveConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Spring Boot 自动配置，从 Spring {@link Environment} 加载脱敏配置。
 *
 * <p>使用 {@code @Configuration} 而非 {@code @AutoConfiguration} 以保证
 * Spring Boot 2.0 ~ 3.x 全版本兼容。
 *
 * <p>配置的启用/禁用通过 {@code sensitive.enabled} 在运行时判断，
 * 而非通过类级别 {@code @ConditionalOnProperty} 在编译时跳过，
 * 确保配置初始化 Bean 始终被注册。
 *
 * <h3>运行时配置刷新（三者互不冲突，按 classpath 自动激活）</h3>
 * <ul>
 *   <li>Spring Cloud — {@code EnvironmentChangeEvent} 事件驱动</li>
 *   <li>Apollo 原生 SDK — {@code ConfigChangeListener} 回调</li>
 *   <li>Nacos 原生 SDK — {@code Listener} 回调</li>
 * </ul>
 * <p>均通过 {@link ConditionalOnClass} + 反射隔离，零编译依赖。
 *
 * @author renss
 * @version V1.3.0
 * @since 1.0.0 2026/6/2
 */
@Configuration(proxyBeanMethods = false)
public class SensiveAutoConfiguration {

    /**
     * 从 Spring Environment 提取所有 sensitive.* 属性并初始化脱敏配置。
     *
     * <p>Environment 已聚合 Apollo/Nacos/yml/properties 等多源属性，
     * 因此无需单独对接每个配置中心。
     */
    @Bean
    static SensitiveConfigInitializer sensiveConfigInitializer(Environment env) {
        reloadFromEnvironment(env);
        return new SensitiveConfigInitializer();
    }

    /**
     * 从 Spring Environment 提取 sensitive.* 属性并重载脱敏配置。
     */
    static void reloadFromEnvironment(Environment env) {
        SensitiveConfig.reload(extractSensitiveProperties(env));
        SensiveUtils.refreshEngine();
    }

    /**
     * 从 properties 格式的配置文本中提取 sensitive.* 属性并重载。
     * 用于 Nacos 原生 Listener 场景（配置推送时 Environment 尚未更新）。
     */
    static void reloadFromPropertiesContent(String content) {
        if (content == null || content.isEmpty()) return;
        try {
            java.io.StringReader reader = new java.io.StringReader(content);
            Properties props = new Properties();
            props.load(reader);
            reader.close();

            // 仅保留 sensitive.* 前缀的属性
            Properties filtered = new Properties();
            for (String key : props.stringPropertyNames()) {
                if (key.startsWith("sensitive.")) {
                    filtered.setProperty(key, props.getProperty(key));
                }
            }
            if (!filtered.isEmpty()) {
                SensitiveConfig.reload(filtered);
                SensiveUtils.refreshEngine();
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 从 Spring Environment 提取所有 sensitive.* 前缀的属性。
     *
     * <p>遍历所有 PropertySource（含 Apollo/Nacos 适配器、application.yml 等），
     * 按优先级覆盖：低优先级先处理，高优先级后覆盖。
     */
    private static Properties extractSensitiveProperties(Environment env) {
        Properties props = new Properties();

        if (env instanceof AbstractEnvironment) {
            AbstractEnvironment aenv = (AbstractEnvironment) env;
            MutablePropertySources sources = aenv.getPropertySources();

            // 倒序收集：低优先级先处理，高优先级后覆盖
            List<PropertySource<?>> reversed = new ArrayList<PropertySource<?>>();
            for (PropertySource<?> source : sources) {
                reversed.add(source);
            }
            Collections.reverse(reversed);

            for (PropertySource<?> source : reversed) {
                if (source.getName().contains("Bootstrap")) continue;

                if (source instanceof EnumerablePropertySource) {
                    // 主路径：EnumerablePropertySource.getPropertyNames()
                    // 覆盖 OriginTrackedMapPropertySource (application.yml)、
                    // PropertiesPropertySource、Apollo/Nacos 适配器等
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
                    // 回退：暴露原始 Map 的非标准属性源
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

        // 兜底：对非 EnumerablePropertySource 和非 Map 源，走 Environment 全量解析
        tryEnrichFromEnvironment(env, props);

        return props;
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

    // ============================================================
    // 运行时配置刷新（Spring Cloud 配置中心热更新）
    // ============================================================

    /**
     * 监听 Spring Cloud {@code EnvironmentChangeEvent}，在 Apollo / Nacos / Consul
     * 等配置中心推送变更时自动重载脱敏配置。
     *
     * <p>使用 {@link ConditionalOnClass} 隔离，仅在 Spring Cloud Context
     * 在 classpath 上时生效。无 Spring Cloud 的项目不受影响。
     *
     * <p>仅当变更的 key 包含 {@code sensitive.} 前缀时才重建引擎，
     * 避免无关配置变更触发不必要的重建。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.cloud.context.environment.EnvironmentChangeEvent")
    static class CloudConfigRefreshConfiguration {

        private final Environment env;

        CloudConfigRefreshConfiguration(Environment env) {
            this.env = env;
        }

        /**
         * 收到 EnvironmentChangeEvent 时，检查是否涉及 sensitive.* 配置，
         * 如有则重载配置并重建引擎。
         */
        @EventListener
        public void onEnvironmentChange(Object event) {
            // 仅处理 EnvironmentChangeEvent，避免对每个 Spring 事件做反射
            if (event == null || !event.getClass().getName()
                    .equals("org.springframework.cloud.context.environment.EnvironmentChangeEvent")) {
                return;
            }
            try {
                java.util.Set<?> keys = (java.util.Set<?>) event.getClass()
                        .getMethod("getKeys").invoke(event);
                if (keys != null && keys.stream().anyMatch(
                        k -> k != null && k.toString().startsWith("sensitive."))) {
                    reloadFromEnvironment(env);
                }
            } catch (Exception ignored) {
                // 反射失败则静默忽略，不影响应用运行
            }
        }
    }

    // ============================================================
    // 运行时配置刷新（Apollo 原生 SDK，无需 Spring Cloud）
    // ============================================================

    /**
     * 注册 Apollo 原生 {@code ConfigChangeListener}，在 Apollo SDK 推送配置变更时
     * 自动重载脱敏配置。
     *
     * <p>使用 {@link ConditionalOnClass} 隔离，仅在 Apollo SDK 在 classpath 上时生效。
     * 通过反射调用避免编译时依赖。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.ctrip.framework.apollo.ConfigChangeListener")
    static class ApolloConfigRefreshConfiguration {

        ApolloConfigRefreshConfiguration(Environment env) {
            registerListener(env);
        }

        private static void registerListener(Environment env) {
            try {
                Class<?> configServiceClass = Class.forName(
                        "com.ctrip.framework.apollo.ConfigService");
                Object config = configServiceClass.getMethod("getAppConfig").invoke(null);

                Class<?> listenerClass = Class.forName(
                        "com.ctrip.framework.apollo.ConfigChangeListener");
                Object listener = Proxy.newProxyInstance(
                        listenerClass.getClassLoader(),
                        new Class<?>[]{listenerClass},
                        (proxy, method, args) -> {
                            switch (method.getName()) {
                                case "onChange":
                                    if (args != null && args.length == 1 && args[0] != null) {
                                        try {
                                            Object event = args[0];
                                            java.util.Set<?> keys = (java.util.Set<?>) event.getClass()
                                                    .getMethod("changedKeys").invoke(event);
                                            if (keys != null && keys.stream().anyMatch(
                                                    k -> k != null && k.toString().startsWith("sensitive."))) {
                                                reloadFromEnvironment(env);
                                            }
                                        } catch (Exception ignored) { }
                                    }
                                    return null;
                                case "equals":
                                    return proxy == args[0];
                                case "hashCode":
                                    return System.identityHashCode(proxy);
                                case "toString":
                                    return "ApolloConfigRefreshListener[sensive]";
                                default:
                                    return null;
                            }
                        });

                config.getClass()
                        .getMethod("addChangeListener", listenerClass)
                        .invoke(config, listener);
            } catch (Exception ignored) {
                // Apollo SDK 不存在或反射失败，静默忽略
            }
        }
    }

    // ============================================================
    // 运行时配置刷新（Nacos 原生 SDK，无需 Spring Cloud）
    // ============================================================

    /**
     * 注册 Nacos 原生 {@code Listener}，在 Nacos SDK 推送配置变更时
     * 自动重载脱敏配置。
     *
     * <p>使用 {@link ConditionalOnClass} 隔离，仅在 Nacos SDK 在 classpath 上时生效。
     * 通过反射调用避免编译时依赖。dataId 和 group 通过配置指定。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.alibaba.nacos.api.config.listener.Listener")
    static class NacosConfigRefreshConfiguration {

        NacosConfigRefreshConfiguration(Environment env) {
            registerListener(env);
        }

        private static void registerListener(Environment env) {
            try {
                String dataId = env.getProperty("sensitive.refresh.nacos.data-id", "application");
                String group = env.getProperty("sensitive.refresh.nacos.group", "DEFAULT_GROUP");

                String serverAddr = env.getProperty("spring.cloud.nacos.config.server-addr");
                if (serverAddr == null) {
                    serverAddr = env.getProperty("nacos.config.server-addr");
                }
                if (serverAddr == null) {
                    return; // 无 serverAddr，无法连接 Nacos
                }

                java.util.Properties nacosProps = new java.util.Properties();
                nacosProps.setProperty("serverAddr", serverAddr);
                // 同时读取 namespace（如有）
                String namespace = env.getProperty("spring.cloud.nacos.config.namespace");
                if (namespace == null) {
                    namespace = env.getProperty("nacos.config.namespace");
                }
                if (namespace != null && !namespace.isEmpty()) {
                    nacosProps.setProperty("namespace", namespace);
                }

                Class<?> factoryClass = Class.forName(
                        "com.alibaba.nacos.api.NacosFactory");
                Object configService = factoryClass.getMethod(
                        "createConfigService", java.util.Properties.class)
                        .invoke(null, nacosProps);

                Class<?> listenerClass = Class.forName(
                        "com.alibaba.nacos.api.config.listener.Listener");
                Object listener = Proxy.newProxyInstance(
                        listenerClass.getClassLoader(),
                        new Class<?>[]{listenerClass},
                        (proxy, method, args) -> {
                            switch (method.getName()) {
                                case "receiveConfigInfo":
                                    // Nacos 推送原始配置文本，早于 Environment 更新，
                                    // 直接从文本解析 sensitive.* 属性
                                    if (args != null && args.length == 1
                                            && args[0] instanceof String) {
                                        String configInfo = (String) args[0];
                                        if (configInfo != null
                                                && configInfo.contains("sensitive.")) {
                                            reloadFromPropertiesContent(configInfo);
                                        }
                                    }
                                    return null;
                                case "getExecutor":
                                    return null; // 使用 Nacos 默认线程池
                                case "equals":
                                    return proxy == args[0];
                                case "hashCode":
                                    return System.identityHashCode(proxy);
                                case "toString":
                                    return "NacosConfigRefreshListener[sensive]";
                                default:
                                    return null;
                            }
                        });

                // 兼容 Nacos 1.x (3 参) 和 2.x (4 参含 namespace)
                try {
                    configService.getClass()
                            .getMethod("addListener", String.class, String.class,
                                    listenerClass)
                            .invoke(configService, dataId, group, listener);
                } catch (NoSuchMethodException e) {
                    configService.getClass()
                            .getMethod("addListener", String.class, String.class,
                                    String.class, listenerClass)
                            .invoke(configService, dataId, group,
                                    namespace != null ? namespace : "", listener);
                }
            } catch (Exception ignored) {
                // Nacos SDK 不存在或反射失败，静默忽略
            }
        }
    }
}
