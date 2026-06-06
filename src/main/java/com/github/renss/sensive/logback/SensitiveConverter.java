package com.github.renss.sensive.logback;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.github.renss.sensive.SensiveUtils;

/**
 * Logback 脱敏 ClassicConverter，作为独立的 %sensitive 关键字使用，
 * 无需替换原有的 %msg 转换器。
 *
 * <h3>MyBatis SQL 识别</h3>
 * <ul>
 *   <li>含 {@code Preparing:} 的 SQL 占位符行放行不处理</li>
 *   <li>含 {@code Parameters:} 的参数行使用 {@link SensiveUtils#maskSql(String)} 双引擎脱敏</li>
 *   <li>其余普通日志使用 {@link SensiveUtils#mask(String)} KV 模式脱敏</li>
 * </ul>
 *
 * <pre>
 * logback.xml 配置：
 *
 * &lt;conversionRule conversionWord="sensitive"
 *     converterClass="com.github.renss.sensive.logback.SensitiveConverter" /&gt;
 *
 * &lt;pattern&gt;%d %level [%thread] %sensitive%n&lt;/pattern&gt;
 * </pre>
 *
 * @author renss
 * @version V1.0.0
 * @since 1.0.0 2026/6/2
 */
public class SensitiveConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        if (message == null) return null;

        // MyBatis SQL statement — leave untouched
        if (message.contains("Preparing:")) {
            return message;
        }

        // MyBatis parameter values — use SQL mode (KV + text patterns)
        if (message.contains("Parameters:")) {
            return SensiveUtils.maskSql(message);
        }

        return SensiveUtils.mask(message);
    }
}
