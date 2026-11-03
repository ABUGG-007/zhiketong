package com.zhiketong.dto.agent;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 坐席状态视图对象（R-40 ~ R-42）。
 */
@Data
public class AgentStatusVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long agentId;
    private String agentName;
    private String onlineStatus;
    private Integer maxConcurrent;
    private Integer currentCount;
    private Integer totalServed;
    private LocalDateTime lastHeartbeat;

    /** 今日接入会话数 */
    private Integer todayServed;

    /** 今日平均评分 */
    private BigDecimal avgRating;
}
