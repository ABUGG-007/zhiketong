package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 工单实体，对应表 work_order。
 * <p>
 * status 取值见 {@link com.zhiketong.enums.OrderStatus}，
 * priority 取值见 {@link com.zhiketong.enums.OrderPriority}，
 * source 取值见 {@link com.zhiketong.enums.OrderSource}。
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("work_order")
public class WorkOrder extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID，由多租户拦截器在写入时按当前上下文填充 */
    private Long tenantId;


    /** 工单号：WO + yyyyMMddHHmmss + 4 位随机 */
    private String orderNo;

    /** 工单标题 */
    private String title;

    /** 问题描述 */
    private String content;

    /** 问题分类：技术问题/账号问题/订单问题/售后问题/其他 */
    private String category;

    /** 优先级：P0/P1/P2/P3 */
    private String priority;

    /** 状态：PENDING/ASSIGNED/PROCESSING/WAITING_CUSTOMER/RESOLVED/CLOSED/REOPENED */
    private String status;

    /** 来源：CHAT/MANUAL/PHONE */
    private String source;

    /** 关联会话 ID */
    private Long sessionId;

    /** 工单模板 ID：按模板创建时记录，普通创建为空 */
    private Long templateId;

    /** SLA 计时记录 ID，指向 order_sla_record.id；为空表示未启用新 SLA 引擎（历史工单） */
    private Long slaId;

    /** 创建人 ID */
    private Long creatorId;

    /** 处理人（坐席）ID */
    private Long assigneeId;

    /** SLA 解决截止时间 */
    private LocalDateTime slaDeadline;

    /** 首次响应时间 */
    private LocalDateTime firstResponseTime;

    /** 解决时间 */
    private LocalDateTime resolveTime;

    /** 关闭时间 */
    private LocalDateTime closeTime;

    /** 满意度 1-5 */
    private Integer rating;

    /** 是否超时：0 否 1 是 */
    private Integer overdueFlag;

    /** SLA 状态：NORMAL 正常 / WARN 预警 / OVERDUE 超时 / PAUSED 停表中 / FINISHED 已完结 */
    private String slaStatus;

    /** 累计停表分钟数，解决时限按此时长顺延 */
    private Integer pauseMinutes;

    /** 下次回复截止时间（回复间隔 SLA），为空表示未启用该级时限 */
    private LocalDateTime replyDueTime;

    /** 备注 */
    private String remark;

    /** 逻辑删除：0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;
}
