package com.github.renss.sensive.logback;

import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Auto-installs the Logback SensitiveMessageConverter so that {@code %msg}
 * automatically masks sensitive data without editing logback.xml.
 *
 * <h3>Spring Boot</h3>
 * Automatically discovered via {@code spring.factories} as an
 * {@code ApplicationListener&lt;ApplicationStartingEvent&gt;}, which fires
 * before Logback initializes — no code changes needed.
 *
 * <h3>Non-Spring Boot (manual)</h3>
 * Call {@link #install()} at the very start of your {@code main()} method,
 * before any logging:
 * <pre>
 * public static void main(String[] args) {
 *     SensiveLogbackInitializer.install();
 *     // ... rest of startup
 * }
 * </pre>
 *
 * The converter itself checks {@code SensitiveConfig.isEnabled()}, so setting
 * {@code sensitive.enabled: false} disables masking even when installed.
 */
public final class SensiveLogbackInitializer {

    private static volatile boolean installed = false;

    private SensiveLogbackInitializer() {}

    /**
     * Register {@link SensitiveMessageConverter} as the {@code %msg} converter
     * in Logback's default pattern layout converter map.
     *
     * <p>Safe to call multiple times — subsequent calls are no-ops.
     * Does nothing if Logback is not on the classpath.
     */
    /**
     * 注册 SensitiveMessageConverter。线程安全，可重复调用。
     */
    public static void install() {
        if (installed) return;

        synchronized (SensiveLogbackInitializer.class) {
            if (installed) return; // DCL 二次检查

            try {
                Class.forName("ch.qos.logback.classic.LoggerContext");

                Field mapField = findConverterMapField();
                if (mapField != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> converterMap = (Map<String, String>) mapField.get(null);
                    converterMap.put("m", SensitiveMessageConverter.class.getName());
                    converterMap.put("msg", SensitiveMessageConverter.class.getName());
                    converterMap.put("message", SensitiveMessageConverter.class.getName());
                    installed = true;
                }
            } catch (ClassNotFoundException ignored) {
                // Logback not available — nothing to do
            } catch (Exception ignored) {
                // Reflection failed — Logback version may be incompatible
            }
        }
    }

    private static Field findConverterMapField() {
        // Logback 1.2.x: defaultConverterMap (camelCase)
        // Logback 1.3+:  DEFAULT_CONVERTER_MAP (UPPER_CASE)
        try {
            Field f = PatternLayout.class.getField("defaultConverterMap");
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            try {
                Field f = PatternLayout.class.getField("DEFAULT_CONVERTER_MAP");
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e2) {
                return null;
            }
        }
    }
}
