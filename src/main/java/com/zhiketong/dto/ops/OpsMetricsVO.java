package com.zhiketong.dto.ops;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 运营指标区间报表（R-177，DB-01 / DB-04 / DB-05）。
 *
 * <p>口径以 docs/产品推进路线图.md 第 3 章为准：首响取「访客首条有效消息 → 首次有效回复」，
 * 3 分钟回复率按轮次统计且只统计人工接待期，AI 自主解决率只统计已关闭会话；
 * 一次性解决率按「已人工接待并关闭的会话，同一访客 72 小时内是否再次进线」回溯计算。</p>
 *
 * @author 智客通开发组
 */
@Data
public class OpsMetricsVO {

    /** 统计区间起（含当天） */
    private String startDate;

    /** 统计区间止（含当天） */
    private String endDate;

    /** 区间内新建会话数 */
    private long sessionTotal;

    /** 区间内已关闭会话数 */
    private long closedCount;

    /** 区间内进入人工接待的会话数（accept_time 非空） */
    private long humanSessionCount;

    /** 区间内触发转人工的会话数 */
    private long transferredCount;

    /** 转人工率 */
    private BigDecimal transferRate;

    /** 平均会话时长（秒，接入到结束） */
    private long avgSessionDurationSeconds;

    /** 首响样本数（首响起止时间齐备的会话） */
    private long firstResponseSample;

    /** 首响均值（秒） */
    private long avgFirstResponseSeconds;

    /** 首响 P90（秒） */
    private long p90FirstResponseSeconds;

    /** 人工接待轮次总数 */
    private long roundCount;

    /** 3 分钟内回复轮次 */
    private long withinCount;

    /** 3 分钟回复率（按轮次） */
    private BigDecimal threeMinuteReplyRate;

    /** 已计算轮次指标的会话数 */
    private long metricSessionCount;

    /** 尚未计算轮次指标的会话数（需执行回填） */
    private long pendingMetricSessionCount;

    /** 轮次指标覆盖率（已计算 / 人工接待） */
    private BigDecimal metricCoverageRate;

    /** 机器人独立解决会话数 */
    private long aiOnlyCount;

    /** AI 自主解决率（机器人独立解决 / 已关闭） */
    private BigDecimal aiResolveRate;

    /** 评价数 */
    private long ratingCount;

    /** 平均满意度 */
    private BigDecimal avgRating;

    /** 满意度回收率（评价数 / 已关闭会话） */
    private BigDecimal satisfactionRecoveryRate;

    /** 一次性解决率统计口径：观察窗口小时数（默认 72） */
    private int oneTimeResolveWindowHours;

    /** 一次性解决率分母：已人工接待且观察期已满的关闭会话数 */
    private long oneTimeResolveBase;

    /** 一次性解决率分子：观察窗口内同一访客未再次进线的会话数 */
    private long oneTimeResolveCount;

    /** 二次进线会话数（分母 - 分子） */
    private long revisitCount;

    /** 一次性解决率（分子 / 分母） */
    private BigDecimal oneTimeResolveRate;

    /** 区间内新建工单数 */
    private long orderTotal;

    /** 超时工单数（未关闭且 overdue_flag=1） */
    private long orderOverdueCount;

    /** 工单超时率 */
    private BigDecimal orderOverdueRate;
}
