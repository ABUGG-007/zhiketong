package com.zhiketong.dto.alert;

import com.zhiketong.entity.AlertRule;
import com.zhiketong.enums.AlertMetric;
import com.zhiketong.enums.AlertRuleType;
import com.zhiketong.enums.AlertSeverity;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 告警规则视图对象（R-180 / R-190）。
 *
 * @author 智客通开发组
 */
@Data
public class AlertRuleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 规则 ID */
    private Long id;

    /** 规则编码 */
    private String ruleCode;

    /** 指标编码 */
    private String metricCode;

    /** 指标名称 */
    private String metricName;

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

    /** 触发阈值 */
    private Integer thresholdValue;

    /** 阈值单位 */
    private String thresholdUnit;

    /** 冷却分钟数 */
    private Integer cooldownMinutes;

    /** 通知对象 */
    private String notifyRole;

    /** 归属坐席 ID，0 表示租户级 */
    private Long ownerId;

    /** 是否租户级规则 */
    private Boolean tenantLevel;

    /** 敏感词表 */
    private String keywords;

    /** 规则说明 */
    private String description;

    /** 排序号 */
    private Integer sort;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 是否内置规则 */
    private Boolean builtin;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 实体转视图对象。
     *
     * @param rule 规则实体
     * @return 视图对象
     */
    public static AlertRuleVO of(AlertRule rule) {
        AlertRuleVO vo = new AlertRuleVO();
        vo.setId(rule.getId());
        vo.setRuleCode(rule.getRuleCode());
        vo.setMetricCode(rule.getMetricCode());
        AlertMetric metric = AlertMetric.fromCode(rule.getMetricCode());
        vo.setMetricName(metric == null ? rule.getMetricCode() : metric.getDescription());
        vo.setRuleName(rule.getRuleName());
        vo.setRuleType(rule.getRuleType());
        AlertRuleType type = AlertRuleType.fromCode(rule.getRuleType());
        vo.setRuleTypeName(type == null ? rule.getRuleType() : type.getDescription());
        vo.setSeverity(rule.getSeverity());
        AlertSeverity severity = AlertSeverity.fromCode(rule.getSeverity());
        vo.setSeverityName(severity == null ? rule.getSeverity() : severity.getDescription());
        vo.setThresholdValue(rule.getThresholdValue());
        vo.setThresholdUnit(rule.getThresholdUnit());
        vo.setCooldownMinutes(rule.getCooldownMinutes());
        vo.setNotifyRole(rule.getNotifyRole());
        vo.setOwnerId(rule.getOwnerId());
        vo.setTenantLevel(rule.getOwnerId() == null || rule.getOwnerId() == 0L);
        vo.setKeywords(rule.getKeywords());
        vo.setDescription(rule.getDescription());
        vo.setSort(rule.getSort());
        vo.setStatus(rule.getStatus());
        vo.setBuiltin(Integer.valueOf(1).equals(rule.getBuiltIn()));
        vo.setCreateTime(rule.getCreateTime());
        vo.setUpdateTime(rule.getUpdateTime());
        return vo;
    }
}
