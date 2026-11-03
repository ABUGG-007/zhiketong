package com.zhiketong.dto.stats;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话量趋势（R-65）。
 *
 * @author 智客通开发组
 */
@Data
public class SessionTrendVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 粒度：day / hour */
    private String granularity;

    /** 趋势点，无数据的区间补 0 */
    private List<Point> points = new ArrayList<>();

    /**
     * 单个趋势点。
     */
    @Data
    public static class Point implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 时间标签：day 为 yyyy-MM-dd，hour 为 yyyy-MM-dd HH:00 */
        private String time;

        /** 会话数 */
        private long sessionCount;

        /** 人工会话数 */
        private long humanSessionCount;

        /** 已关闭会话数 */
        private long closedCount;

        /** 平均会话时长（秒） */
        private long avgDurationSeconds;

        /** 平均满意度 */
        private BigDecimal avgRating;
    }
}
