package com.zhiketong.dto.orderrule;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工单规则视图对象（R-127/R-128/R-137/R-138/R-139/R-140/R-141），附枚举可读标签与技能组名。
 *
 * @author 智客通开发组
 */
@Data
public class OrderRuleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 规则 ID */
    private Long id;

    /** 规则名称 */
    private String ruleName;

    /** 规则类型编码 */
    private String ruleType;

    /** 规则类型标签 */
    private String ruleTypeLabel;

    /** 触发事件编码 */
    private String triggerEvent;

    /** 触发事件标签 */
    private String triggerEventLabel;

    /** 延迟执行分钟数 */
    private Integer delayMinutes;

    /** 匹配优先级 */
    private String matchPriority;

    /** 匹配分类 */
    private String matchCategory;

    /** 匹配来源 */
    private String matchSource;

    /** 动作类型编码 */
    private String actionType;

    /** 动作类型标签 */
    private String actionTypeLabel;

    /** 动作目标值 */
    private String actionValue;

    /** 自动分配技能组 ID */
    private Long assignGroupId;

    /** 自动分配技能组名称 */
    private String assignGroupName;

    /** 规则说明 */
    private String description;

    /** 排序号 */
    private Integer sort;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
