package com.zhiketong.dto.alert;

import com.zhiketong.entity.AlertEvent;
import com.zhiketong.enums.AlertRuleType;
import com.zhiketong.enums.AlertSeverity;
import com.zhiketong.enums.AlertStatus;
import com.zhiketong.enums.AlertTargetType;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 告警事件视图对象（R-185 管理端 / R-195 坐席端）。
 *
 * @author 智客通开发组
 */
@Data
public class AlertEventVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 事件 ID */
    private Long id;

    /** 规则 ID */
    private Long ruleId;

    /** 规则编码 */
    private String ruleCode;

    /** 指标编码 */
    private String metricCode;

    /** 规则名称 */
    private String ruleName;

    /** 规则类型 */
    private String ruleType;

    /** 规则类型名称 */
    private String ruleTypeName;

    /** 严重程度 */
    private String severity;

    /** 严重程度名称 */
    private String severityName;

    /** 目标类型 */
    private String targetType;

    /** 目标类型名称 */
    private String targetTypeName;

    /** 目标 ID */
    private Long targetId;

    /** 关联会话 ID */
    private Long sessionId;

    /** 关联坐席 ID */
    private Long agentId;

    /** 告警标题 */
    private String title;

    /** 告警详情 */
    private String detail;

    /** 触发时指标值 */
    private Long metricValue;

    /** 触发时阈值 */
    private Integer thresholdValue;

    /** 处理状态 */
    private String status;

    /** 处理状态名称 */
    private String statusName;

    /** 累计触发次数 */
    private Integer occurCount;

    /** 首次触发时间 */
    private LocalDateTime firstTime;

    /** 最近触发时间 */
    private LocalDateTime lastTime;

    /** 确认人 ID */
    private Long ackBy;

    /** 确认人名称 */
    private String ackName;

    /** 确认时间 */
    private LocalDateTime ackTime;

    /** 确认说明 */
    private String ackNote;

    /** 关闭时间 */
    private LocalDateTime closeTime;

    /** 处理耗时（秒） */
    private Long handleSeconds;

    /**
     * 实体转视图对象。
     *
     * @param event 事件实体
     * @return 视图对象
     */
    public static AlertEventVO of(AlertEvent event) {
        AlertEventVO vo = new AlertEventVO();
        vo.setId(event.getId());
        vo.setRuleId(event.getRuleId());
        vo.setRuleCode(event.getRuleCode());
        vo.setMetricCode(event.getMetricCode());
        vo.setRuleName(event.getRuleName());
        vo.setRuleType(event.getRuleType());
        AlertRuleType ruleType = AlertRuleType.fromCode(event.getRuleType());
        vo.setRuleTypeName(ruleType == null ? event.getRuleType() : ruleType.getDescription());
        vo.setSeverity(event.getSeverity());
        AlertSeverity severity = AlertSeverity.fromCode(event.getSeverity());
        vo.setSeverityName(severity == null ? event.getSeverity() : severity.getDescription());
        vo.setTargetType(event.getTargetType());
        AlertTargetType targetType = AlertTargetType.fromCode(event.getTargetType());
        vo.setTargetTypeName(targetType == null ? event.getTargetType() : targetType.getDescription());
        vo.setTargetId(event.getTargetId());
        vo.setSessionId(event.getSessionId());
        vo.setAgentId(event.getAgentId());
        vo.setTitle(event.getTitle());
        vo.setDetail(event.getDetail());
        vo.setMetricValue(event.getMetricValue());
        vo.setThresholdValue(event.getThresholdValue());
        vo.setStatus(event.getStatus());
        AlertStatus status = AlertStatus.fromCode(event.getStatus());
        vo.setStatusName(status == null ? event.getStatus() : status.getDescription());
        vo.setOccurCount(event.getOccurCount());
        vo.setFirstTime(event.getFirstTime());
        vo.setLastTime(event.getLastTime());
        vo.setAckBy(event.getAckBy());
        vo.setAckName(event.getAckName());
        vo.setAckTime(event.getAckTime());
        vo.setAckNote(event.getAckNote());
        vo.setCloseTime(event.getCloseTime());
        vo.setHandleSeconds(event.getHandleSeconds());
        return vo;
    }
}
