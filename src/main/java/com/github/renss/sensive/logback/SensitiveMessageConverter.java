package com.github.renss.sensive.logback;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.github.renss.sensive.SensiveUtils;

/**
 * Logback 脱敏 MessageConverter，替代默认的 %msg 转换器。
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
 * &lt;conversionRule conversionWord="msg"
 *     converterClass="com.github.renss.sensive.logback.SensitiveMessageConverter" /&gt;
 * </pre>
 *
 * @author renss
 * @version V1.0.0
 * @since 1.0.0 2026/6/2
 */
public class SensitiveMessageConverter extends MessageConverter {

    @Override
    public String convert(ILoggingEvent event) {
        String message = super.convert(event);
        if (message == null) return null;
        return maskByContent(message);
    }

    /**
     * 根据消息内容路由到对应的脱敏方法。
     *
     * @param message 原始日志消息
     * @return 脱敏后的消息
     */
    static String maskByContent(String message) {
        if (message == null || message.isEmpty()) return message;

        // MyBatis SQL statement with ? placeholders — leave untouched
        if (message.contains("Preparing:")) {
            return message;
        }

        // MyBatis parameter values — use SQL mode (KV + text patterns)
        if (message.contains("Parameters:")) {
            return SensiveUtils.maskSql(message);
        }

        // General application log — KV-only matching
        return SensiveUtils.mask(message);
    }
}
