package com.zhiketong.dto.agent;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 坐席工作台统计（R-48）。
 *
 * @author 智客通开发组
 */
@Data
public class AgentWorkbenchStatsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 在线状态：ONLINE/BUSY/OFFLINE */
    private String onlineStatus;

    /** 当前接待数 */
    private Integer currentCount;

    /** 最大并发接待数 */
    private Integer maxConcurrent;

    /** 全局排队数 */
    private Integer queueSize;

    /** 今日接入会话数 */
    private Integer todayServed;

    /** 今日结束会话数 */
    private Integer todayClosed;

    /** 今日平均评分 */
    private BigDecimal todayAvgRating;

    /** 累计服务量 */
    private Integer totalServed;

    /** 今日平均接入耗时（秒） */
    private Integer avgAcceptSeconds;

    /** 今日平均会话时长（秒） */
    private Integer avgDurationSeconds;
}
