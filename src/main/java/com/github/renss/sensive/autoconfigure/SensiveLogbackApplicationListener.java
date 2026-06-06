package com.github.renss.sensive.autoconfigure;

import com.github.renss.sensive.logback.SensiveLogbackInitializer;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ApplicationListener;

/**
 * Spring Boot {@link ApplicationListener} that installs the Logback
 * SensitiveMessageConverter before Logback initializes.
 *
 * <p>Discovered automatically via {@code spring.factories}:
 * <pre>
 * org.springframework.context.ApplicationListener=\
 *   com.github.renss.sensive.autoconfigure.SensiveLogbackApplicationListener
 * </pre>
 *
 * <p>This listener fires on {@link ApplicationStartingEvent}, which is the
 * earliest Spring Boot lifecycle event — before any logging system
 * initialization. This ensures {@code %msg} is redirected to our
 * desensitizing converter before Logback parses pattern layouts.
 */
public class SensiveLogbackApplicationListener
        implements ApplicationListener<ApplicationStartingEvent> {

    @Override
    public void onApplicationEvent(ApplicationStartingEvent event) {
        SensiveLogbackInitializer.install();
    }
}
