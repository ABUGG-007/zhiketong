package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 工单 SLA 计时记录实体，对应表 order_sla_record（S3-B）。
 *
 * <p>每个工单一条计时记录，保存所用策略的时限快照与三级截止时刻；停表期间 {@code status=PAUSED}，
 * 扫描任务跳过预警与超时判定，恢复计时时把停表时长顺延到各截止时间，并同步回
 * {@code work_order.sla_deadline}。各状态位（warn/overdue/escalated）保证同一事件只通知一次。</p>
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("order_sla_record")
public class OrderSlaRecord extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID，1 为平台默认 */
    private Long tenantId;

    /** 工单 ID */
    private Long orderId;

    /** 来源策略 ID，为空表示使用配置文件兜底时限 */
    private Long policyId;

    /** 策略名称快照 */
    private String policyName;

    /** 优先级快照，优先级变更时重算时限并更新快照 */
    private String priority;

    /** 首次响应时限快照（分钟） */
    private Integer firstResponseMinutes;

    /** 回复间隔时限快照（分钟） */
    private Integer replyIntervalMinutes;

    /** 解决时限快照（分钟） */
    private Integer resolveMinutes;

    /** 预警阈值快照（百分比） */
    private Integer warnPercent;

    /** 升级延迟快照（分钟） */
    private Integer escalateMinutes;

    /** 计时起点，取工单创建时间；重开后重置为当前时间 */
    private LocalDateTime startTime;

    /** 首次响应截止时间，含停表顺延 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime firstResponseDeadline;

    /** 实际首次响应时间，与 work_order.first_response_time 同步 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime firstResponseTime;

    /** 最近一次坐席回复时间（添加处理进展） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime lastReplyTime;

    /** 下次回复截止时间，含停表顺延；未启用回复间隔时为空 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime replyDueTime;

    /** 解决截止时间，含停表顺延；与 work_order.sla_deadline 同步 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime resolveDeadline;

    /** 累计停表分钟数 */
    private Integer pauseMinutes;

    /** 当前是否停表中：1 是 0 否 */
    private Integer paused;

    /** 本次停表开始时间 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime pauseStart;

    /** 首次响应是否已预警：1 是 0 否 */
    private Integer firstResponseWarn;

    /** 首次响应是否已超时：1 是 0 否 */
    private Integer firstResponseOverdue;

    /** 回复间隔是否已预警：1 是 0 否 */
    private Integer replyWarn;

    /** 回复间隔是否已超时：1 是 0 否 */
    private Integer replyOverdue;

    /** 解决时限是否已预警：1 是 0 否 */
    private Integer resolveWarn;

    /** 解决时限是否已超时：1 是 0 否 */
    private Integer resolveOverdue;

    /** 是否已升级通知上级：1 是 0 否 */
    private Integer escalated;

    /** 计时状态：RUNNING 计时中 / PAUSED 停表中 / FINISHED 已完结 */
    private String status;

    /** SLA 状态：NORMAL/WARN/OVERDUE/PAUSED/FINISHED，与 work_order.sla_status 同步 */
    private String slaStatus;

    /** 逻辑删除：0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;
}
