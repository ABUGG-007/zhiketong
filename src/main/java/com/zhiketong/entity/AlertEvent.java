package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 告警事件实体，对应表 alert_event（S6-B）。
 *
 * <p>同一「规则 + 目标」在冷却期内只保留一条事件并累加 {@code occurCount}；
 * 事件关闭后再次触发会新建事件，历史事件不做物理删除。</p>
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("alert_event")
public class AlertEvent extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID */
    private Long tenantId;

    /** 规则 ID，规则删除后保留快照 */
    private Long ruleId;

    /** 规则编码快照 */
    private String ruleCode;

    /** 指标编码快照，用于回溯判定依据 */
    private String metricCode;

    /** 规则名称快照 */
    private String ruleName;

    /** 规则类型快照 */
    private String ruleType;

    /** 严重程度快照 */
    private String severity;

    /** 目标类型：QUEUE/SESSION/AGENT/ORDER */
    private String targetType;

    /** 目标 ID：队列与工单为租户 ID，会话为会话 ID，坐席为坐席 ID */
    private Long targetId;

    /** 关联会话 ID */
    private Long sessionId;

    /** 关联坐席 ID */
    private Long agentId;

    /** 告警标题 */
    private String title;

    /** 告警详情，写入触发时的指标快照 */
    private String detail;

    /** 触发时指标实际值 */
    private Long metricValue;

    /** 触发时阈值快照 */
    private Integer thresholdValue;

    /** 处理状态：OPEN/ACKED/CLOSED */
    private String status;

    /** 同一规则同一目标的累计触发次数 */
    private Integer occurCount;

    /** 首次触发时间 */
    private LocalDateTime firstTime;

    /** 最近触发时间 */
    private LocalDateTime lastTime;

    /** 确认人 ID */
    private Long ackBy;

    /** 确认人名称快照 */
    private String ackName;

    /** 确认时间 */
    private LocalDateTime ackTime;

    /** 确认说明 */
    private String ackNote;

    /** 关闭人 ID，为空表示系统自动关闭 */
    private Long closeBy;

    /** 关闭时间 */
    private LocalDateTime closeTime;

    /** 处理耗时（秒） */
    private Long handleSeconds;

    /** 逻辑删除：0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;
}
