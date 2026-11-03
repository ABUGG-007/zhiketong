package com.zhiketong.dto.orderrule;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工单规则执行流水视图对象（R-143 / R-145），承载待执行队列与历史执行结果。
 *
 * @author 智客通开发组
 */
@Data
public class OrderRuleRunVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 执行记录 ID */
    private Long id;

    /** 规则 ID */
    private Long ruleId;

    /** 规则名称（快照关联查询） */
    private String ruleName;

    /** 工单 ID */
    private Long orderId;

    /** 触发事件编码 */
    private String event;

    /** 触发事件标签 */
    private String eventLabel;

    /** 动作类型编码 */
    private String actionType;

    /** 动作类型标签 */
    private String actionTypeLabel;

    /** 执行状态：PENDING / DONE / FAILED / SKIPPED */
    private String status;

    /** 执行状态标签 */
    private String statusLabel;

    /** 计划执行时间 */
    private LocalDateTime plannedTime;

    /** 实际执行时间 */
    private LocalDateTime executedTime;

    /** 执行结果说明 */
    private String result;

    /** 创建时间 */
    private LocalDateTime createTime;
}
