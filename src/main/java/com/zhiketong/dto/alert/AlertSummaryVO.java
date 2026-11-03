package com.zhiketong.dto.alert;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 告警概览（R-186）：面向告警中心的顶部指标与分布。
 *
 * @author 智客通开发组
 */
@Data
public class AlertSummaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 待处理（OPEN）告警数 */
    private long openCount;

    /** 已确认未关闭（ACKED）告警数 */
    private long ackedCount;

    /** 近 24 小时新增告警数 */
    private long last24hCount;

    /** 近 24 小时严重（CRITICAL）告警数 */
    private long criticalCount;

    /** 平均处理耗时（秒，近 24 小时已关闭事件） */
    private long avgHandleSeconds;

    /** 启用规则数（租户级） */
    private long enabledRuleCount;

    /** 最近一条 OPEN 告警标题，无告警时为空 */
    private String latestTitle;

    /** 最近一条 OPEN 告警时间 */
    private LocalDateTime latestTime;

    /** 统计时间 */
    private LocalDateTime serverTime;
}
