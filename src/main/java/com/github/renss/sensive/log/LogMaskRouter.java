package com.github.renss.sensive.log;

import com.github.renss.sensive.SensiveUtils;

/**
 * 内部消息路由工具，供所有日志框架适配器共用。
 * 对消息进行一次扫描以确定脱敏模式，避免重复扫描。
 *
 * @author renss
 * @version V1.2.0
 * @since 1.2.0
 */
public final class LogMaskRouter {

    private static final String PREPARING_MARKER = "Preparing:";
    private static final String PARAMETERS_MARKER = "Parameters:";

    private LogMaskRouter() {}

    /**
     * 根据消息内容路由到对应的脱敏方法。
     *
     * <p>扫描消息确定路由模式：Preparing:（跳过）、Parameters:（增强模式）、其他（KV 模式）。
     *
     * @param message 原始日志消息
     * @return 脱敏后的消息（无需脱敏则返回原文本）
     */
    public static String maskByContent(String message) {
        if (message == null || message.isEmpty()) return message;

        // Single scan: find first occurrence of either MyBatis marker
        int preIdx = message.indexOf(PREPARING_MARKER);
        int parIdx = message.indexOf(PARAMETERS_MARKER);

        if (preIdx >= 0 && (parIdx < 0 || preIdx < parIdx)) {
            // "Preparing:" found first (or only) — SQL statement with ? placeholders, skip
            return message;
        }
        if (parIdx >= 0) {
            // "Parameters:" — 使用增强脱敏（KV 匹配 + 文本模式扫描）
            return SensiveUtils.maskEnhanced(message);
        }

        // General application log — KV-only matching
        return SensiveUtils.mask(message);
    }
}
