package com.github.renss.sensive.engine;

/**
 * 脱敏位置区间模型，表示原文中需要被脱敏的一个值区间。
 *
 * @author renss
 * @version V1.0.0
 * @since 1.0.0 2026/6/2
 */
public class MaskPosition {
    /** 值起始位置（包含） */
    public final int valueStart;
    /** 值结束位置（不包含） */
    public final int valueEnd;
    /** 匹配到的关键字 */
    public final String keyword;

    /**
     * 构造一个脱敏位置区间。
     *
     * @param valueStart 值起始位置（包含）
     * @param valueEnd   值结束位置（不包含）
     * @param keyword    匹配到的关键字
     */
    public MaskPosition(int valueStart, int valueEnd, String keyword) {
        this.valueStart = valueStart;
        this.valueEnd = valueEnd;
        this.keyword = keyword;
    }
}
