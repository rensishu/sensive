package com.github.renss.sensive.log4j;

import com.github.renss.sensive.log.LogMaskRouter;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.spi.LoggingEvent;

/**
 * Log4j 1.x 脱敏 PatternLayout，对格式化后的日志文本进行脱敏。
 *
 * <p>工作原理：先调用父类 {@link PatternLayout#format(LoggingEvent)} 完成格式化，
 * 再对格式化后的字符串执行脱敏。由于脱敏引擎仅识别 key=value 等键值格式，
 * 时间戳、日志级别、线程名等元数据不会被误脱敏。
 *
 * <h3>MyBatis SQL 识别</h3>
 * <ul>
 *   <li>含 {@code Preparing:} 的 SQL 占位符行放行不处理</li>
 *   <li>含 {@code Parameters:} 的参数行使用双引擎脱敏</li>
 *   <li>其余普通日志使用 KV 模式脱敏</li>
 * </ul>
 *
 * <h3>log4j.properties 配置</h3>
 * <pre>
 * log4j.appender.CONSOLE=org.apache.log4j.ConsoleAppender
 * log4j.appender.CONSOLE.layout=com.github.renss.sensive.log4j.SensitivePatternLayout
 * log4j.appender.CONSOLE.layout.ConversionPattern=%d %p [%t] %m%n
 * </pre>
 *
 * <h3>log4j.xml 配置</h3>
 * <pre>
 * &lt;appender name="CONSOLE" class="org.apache.log4j.ConsoleAppender"&gt;
 *     &lt;layout class="com.github.renss.sensive.log4j.SensitivePatternLayout"&gt;
 *         &lt;param name="ConversionPattern" value="%d %p [%t] %m%n" /&gt;
 *     &lt;/layout&gt;
 * &lt;/appender&gt;
 * </pre>
 *
 * @author renss
 * @version V1.2.0
 * @since 1.0.0 2026/6/2
 */
public class SensitivePatternLayout extends PatternLayout {

    @Override
    public String format(LoggingEvent event) {
        String formatted = super.format(event);
        if (formatted == null || formatted.isEmpty()) return formatted;
        return LogMaskRouter.maskByContent(formatted);
    }
}
