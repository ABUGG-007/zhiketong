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

    /** 状态：PENDING/ASSIGNED/PROCESSING/RESOLVED/CLOSED/REOPENED */
    private String status;

    /** 来源：CHAT/MANUAL/PHONE */
    private String source;

    /** 关联会话 ID */
    private Long sessionId;

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

    /** 备注 */
    private String remark;

    /** 逻辑删除：0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;
}
