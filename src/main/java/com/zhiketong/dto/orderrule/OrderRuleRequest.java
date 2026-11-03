package com.zhiketong.dto.orderrule;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 工单规则新增/修改请求（R-139 / R-140）。
 *
 * <p>三类规则统一建模：{@code ruleType} 决定语义，{@code triggerEvent} 指定驱动事件，
 * {@code matchPriority}/{@code matchCategory}/{@code matchSource} 为命中条件（为空即不限），
 * {@code actionType} + {@code actionValue} 描述命中动作，{@code delayMinutes>0} 表示延迟执行。</p>
 *
 * @author 智客通开发组
 */
@Data
public class OrderRuleRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 规则名称，同租户内唯一 */
    @NotBlank(message = "规则名称不能为空")
    @Size(min = 2, max = 64, message = "规则名称长度须为 2-64 个字符")
    private String ruleName;

    /** 规则类型：AUTO_ASSIGN / TRIGGER / AUTOMATION */
    @NotBlank(message = "规则类型不能为空")
    @Pattern(regexp = "AUTO_ASSIGN|TRIGGER|AUTOMATION", message = "规则类型只能为 AUTO_ASSIGN/TRIGGER/AUTOMATION")
    private String ruleType;

    /** 触发事件编码，纯时间自动化可为空 */
    @Pattern(regexp = "CREATED|ASSIGNED|PROCESSING|RESOLVED|CLOSED|REOPENED|PRIORITY_CHANGED"
            + "|SLA_WARN|SLA_OVERDUE|SLA_ESCALATE", message = "触发事件编码非法")
    private String triggerEvent;

    /** 事件后延迟执行分钟数，0 表示立即执行 */
    @Min(value = 0, message = "延迟分钟数不能为负数")
    @Max(value = 525600, message = "延迟分钟数不能超过一年（525600 分钟）")
    private Integer delayMinutes;

    /** 匹配条件：工单优先级 P0-P3，为空表示不限 */
    @Pattern(regexp = "P0|P1|P2|P3|", message = "匹配优先级只能为 P0/P1/P2/P3 或留空")
    private String matchPriority;

    /** 匹配条件：问题分类，为空表示不限 */
    @Size(max = 32, message = "匹配分类不能超过 32 个字符")
    private String matchCategory;

    /** 匹配条件：来源渠道 CHAT/MANUAL/PHONE，为空表示不限 */
    @Pattern(regexp = "CHAT|MANUAL|PHONE|", message = "匹配来源只能为 CHAT/MANUAL/PHONE 或留空")
    private String matchSource;

    /** 动作类型 */
    @NotBlank(message = "动作类型不能为空")
    @Pattern(regexp = "AUTO_ASSIGN|ASSIGN_AGENT|CHANGE_PRIORITY|CHANGE_STATUS|NOTIFY_ROLE",
            message = "动作类型非法")
    private String actionType;

    /** 动作目标值 */
    @Size(max = 255, message = "动作目标值不能超过 255 个字符")
    private String actionValue;

    /** 自动分配取该技能组在岗坐席，为空表示全体在岗 */
    private Long assignGroupId;

    /** WEBHOOK 回调地址（本期预留，暂不生效） */
    @Size(max = 255, message = "回调地址不能超过 255 个字符")
    private String webhookUrl;

    /** 规则说明 */
    @Size(max = 255, message = "规则说明不能超过 255 个字符")
    private String description;

    /** 排序号，越小越靠前 */
    private Integer sort;

    /** 状态：1 启用 0 停用，默认 1 */
    @Min(value = 0, message = "状态只能为 0 或 1")
    @Max(value = 1, message = "状态只能为 0 或 1")
    private Integer status;
}
