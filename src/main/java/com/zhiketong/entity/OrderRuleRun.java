package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 工单规则待执行队列与执行流水实体，对应表 order_rule_run（S3-C）。
 *
 * <p>一表两用：立即动作执行后就地落 DONE/FAILED/SKIPPED 留痕，延迟与时间驱动动作先落 PENDING，
 * 由扫描任务按 {@code plannedTime} 到期取出执行并回写终态。{@code uk_dedup(order_id, rule_id, planned_time)}
 * 保证同一规则对同一工单在同一到期时刻只生成一条待执行记录。</p>
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("order_rule_run")
public class OrderRuleRun extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID，1 为平台默认 */
    private Long tenantId;

    /** 规则 ID */
    private Long ruleId;

    /** 工单 ID */
    private Long orderId;

    /** 触发该次执行的事件编码 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String event;

    /** 执行的动作类型快照 */
    private String actionType;

    /** 执行状态：PENDING / DONE / FAILED / SKIPPED */
    private String status;

    /** 计划执行时间 */
    private LocalDateTime plannedTime;

    /** 实际执行时间，执行后写入 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime executedTime;

    /** 执行结果说明 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String result;

    /** 逻辑删除：0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;
}
