package com.zhiketong.dto.stats;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 数据总览（R-64）。
 *
 * @author 智客通开发组
 */
@Data
public class StatisticsOverviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 统计起始日 */
    private String startDate;

    /** 统计结束日 */
    private String endDate;

    /** 会话总数 */
    private long sessionTotal;

    /** 全程 AI 接待且未转人工的会话数 */
    private long sessionAiOnly;

    /** 发生过转人工的会话数 */
    private long sessionTransferred;

    /** 当前排队会话数 */
    private long sessionWaiting;

    /** 当前活跃会话数 */
    private long sessionActive;

    /** 平均会话时长（秒，接入至结束） */
    private long avgSessionDurationSeconds;

    /** 已评分会话平均星级 */
    private BigDecimal avgRating;

    /** 用户总数 */
    private long userTotal;

    /** 当前在线坐席数 */
    private long agentOnline;

    /** 坐席总数 */
    private long agentTotal;

    /** 工单总数 */
    private long workOrderTotal;

    /** 待分配工单数 */
    private long workOrderPending;

    /** 处理中工单数 */
    private long workOrderProcessing;

    /** 已解决工单数 */
    private long workOrderResolved;

    /** 超时工单数 */
    private long workOrderOverdue;

    /** AI 调用次数 */
    private long aiCalls;

    /** AI 调用成功率 */
    private BigDecimal aiSuccessRate;
}
