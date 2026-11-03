package com.zhiketong.dto.quality;

import com.zhiketong.entity.QualityRule;
import com.zhiketong.enums.QualityRuleType;
import com.zhiketong.enums.QualitySeverity;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 质检规则视图对象（R-160 / R-161）。
 *
 * @author 智客通开发组
 */
@Data
public class QualityRuleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 规则 ID */
    private Long id;

    /** 规则编码：内置规则固定编码，自定义规则以 CUSTOM_ 开头 */
    private String ruleCode;

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

    /** 命中扣分 */
    private Integer deductScore;

    /** 关键词表 */
    private String keywords;

    /** 匹配方式 */
    private String matchMode;

    /** 阈值（分钟） */
    private Integer thresholdValue;

    /** 规则说明 */
    private String description;

    /** 排序号 */
    private Integer sort;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 是否为内置规则（内置规则编码被质检引擎识别，不允许改编码） */
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
    public static QualityRuleVO of(QualityRule rule) {
        QualityRuleVO vo = new QualityRuleVO();
        vo.setId(rule.getId());
        vo.setRuleCode(rule.getRuleCode());
        vo.setRuleName(rule.getRuleName());
        vo.setRuleType(rule.getRuleType());
        QualityRuleType type = QualityRuleType.fromCode(rule.getRuleType());
        vo.setRuleTypeName(type == null ? rule.getRuleType() : type.getDescription());
        vo.setSeverity(rule.getSeverity());
        QualitySeverity severity = QualitySeverity.fromCode(rule.getSeverity());
        vo.setSeverityName(severity == null ? rule.getSeverity() : severity.getDescription());
        vo.setDeductScore(rule.getDeductScore());
        vo.setKeywords(rule.getKeywords());
        vo.setMatchMode(rule.getMatchMode());
        vo.setThresholdValue(rule.getThresholdValue());
        vo.setDescription(rule.getDescription());
        vo.setSort(rule.getSort());
        vo.setStatus(rule.getStatus());
        vo.setBuiltin(rule.getRuleCode() != null && !rule.getRuleCode().startsWith("CUSTOM_"));
        vo.setCreateTime(rule.getCreateTime());
        vo.setUpdateTime(rule.getUpdateTime());
        return vo;
    }
}
