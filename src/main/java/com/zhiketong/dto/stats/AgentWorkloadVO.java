package com.zhiketong.dto.stats;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 坐席工作量（R-66）。
 *
 * @author 智客通开发组
 */
@Data
public class AgentWorkloadVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 坐席用户 ID */
    private Long agentId;

    /** 坐席名称 */
    private String agentName;

    /** 接入会话数 */
    private long servedCount;

    /** 已关闭会话数 */
    private long closedCount;

    /** 平均满意度 */
    private BigDecimal avgRating;

    /** 平均会话时长（秒） */
    private long avgDurationSeconds;

    /** 平均接入等待时长（秒，排队至接入） */
    private long avgAcceptSeconds;

    /** 完成解决的工单数 */
    private long workOrderResolved;
}
