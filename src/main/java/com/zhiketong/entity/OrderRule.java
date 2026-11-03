package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工单规则与自动化配置实体，对应表 order_rule（S3-C）。
 *
 * <p>自动分配、事件触发器、时间自动化共用本表，用 {@code ruleType} 区分语义，用 {@code triggerEvent}
 * 指定驱动事件、{@code match*} 三列做命中条件（为空即不限），用 {@code actionType} + {@code actionValue}
 * 描述命中后的动作。{@code delayMinutes>0} 时动作延迟到扫描任务到期执行。停用后不再被匹配。</p>
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("order_rule")
public class OrderRule extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID，1 为平台默认 */
    private Long tenantId;

    /** 规则名称，同租户内唯一 */
    private String ruleName;

    /** 规则类型：AUTO_ASSIGN / TRIGGER / AUTOMATION */
    private String ruleType;

    /** 触发事件编码，纯时间自动化可为空 */
    private String triggerEvent;

    /** 事件后延迟执行分钟数，0 表示立即执行 */
    private Integer delayMinutes;

    /** 匹配条件：工单优先级 P0-P3，为空表示不限 */
    private String matchPriority;

    /** 匹配条件：问题分类，为空表示不限 */
    private String matchCategory;

    /** 匹配条件：来源渠道，为空表示不限 */
    private String matchSource;

    /** 动作类型 */
    private String actionType;

    /** 动作目标值：新优先级 / 新状态 / 技能组 ID / 通知角色 / 指定坐席 ID */
    private String actionValue;

    /** 自动分配取该技能组在岗坐席，为空表示全体在岗 */
    private Long assignGroupId;

    /** WEBHOOK 回调地址（本期预留，暂不生效） */
    private String webhookUrl;

    /** 规则说明 */
    private String description;

    /** 排序号，越小越靠前 */
    private Integer sort;

    /** 状态：1 启用 0 停用（停用后不再匹配执行） */
    private Integer status;

    /** 创建人 ID */
    private Long createBy;

    /** 更新人 ID */
    private Long updateBy;

    /** 逻辑删除：0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;
}
