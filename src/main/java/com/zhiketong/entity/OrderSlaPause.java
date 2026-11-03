package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 工单 SLA 停表流水实体，对应表 order_sla_pause（S3-B）。
 *
 * <p>记录每次挂起与恢复，用于停表时长核对与工单详情展示；{@code resumeTime} 为空表示仍在停表中，
 * 恢复计时时写入 {@code resumeTime} 与 {@code durationMinutes}，并累加到计时记录的 {@code pauseMinutes}。</p>
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("order_sla_pause")
public class OrderSlaPause extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID，1 为平台默认 */
    private Long tenantId;

    /** 工单 ID */
    private Long orderId;

    /** SLA 计时记录 ID */
    private Long recordId;

    /** 停表类型：WAITING_CUSTOMER 等待客户反馈 / OTHER 其他原因 */
    private String pauseType;

    /** 停表原因 */
    private String reason;

    /** 停表开始时间 */
    private LocalDateTime pauseTime;

    /** 恢复计时时间，为空表示仍在停表中 */
    private LocalDateTime resumeTime;

    /** 本次停表时长（分钟），恢复时写入 */
    private Integer durationMinutes;

    /** 操作人 ID */
    private Long operatorId;

    /** 操作人名称（快照） */
    private String operatorName;

    /** 逻辑删除：0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;
}
