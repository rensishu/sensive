package com.github.renss.sensive.log4j2;

import com.github.renss.sensive.log.LogMaskRouter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.rewrite.RewritePolicy;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;

/**
 * Log4j2 脱敏 RewritePolicy，在 Appender 前改写 LogEvent 中的消息。
 *
 * <h3>MyBatis SQL 识别</h3>
 * <ul>
 *   <li>含 {@code Preparing:} 的 SQL 占位符行放行不处理</li>
 *   <li>含 {@code Parameters:} 的参数行使用双引擎脱敏</li>
 *   <li>其余普通日志使用 KV 模式脱敏</li>
 * </ul>
 *
 * <pre>
 * log4j2.xml 配置：
 *
 * &lt;Rewrite name="rewrite"&gt;
 *     &lt;SensitiveRewritePolicy /&gt;
 *     &lt;AppenderRef ref="Console" /&gt;
 * &lt;/Rewrite&gt;
 *
 * &lt;Root level="info"&gt;
 *     &lt;AppenderRef ref="rewrite" /&gt;
 * &lt;/Root&gt;
 * </pre>
 *
 * @author renss
 * @version V1.2.0
 * @since 1.0.0 2026/6/2
 */
@Plugin(name = "SensitiveRewritePolicy", category = "Core",
        elementType = "rewritePolicy", printObject = true)
public class SensitiveRewritePolicy implements RewritePolicy {

    @PluginFactory
    public static SensitiveRewritePolicy createPolicy() {
        return new SensitiveRewritePolicy();
    }

    @Override
    public LogEvent rewrite(LogEvent source) {
        String message = source.getMessage().getFormattedMessage();
        if (message == null) return source;

        String masked = LogMaskRouter.maskByContent(message);
        if (masked == null || masked.equals(message)) return source;

        return new Log4jLogEvent.Builder(source)
                .setMessage(new SimpleMessage(masked))
                .build();
    }
}
