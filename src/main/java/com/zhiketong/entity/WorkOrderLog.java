package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工单流转日志实体，对应表 work_order_log。
 * <p>
 * 该表只有 create_time、没有 update_time，因此不继承 BaseEntity。
 * action 取值见 {@link com.zhiketong.enums.OrderAction}，
 * fromStatus / toStatus 取值见 {@link com.zhiketong.enums.OrderStatus}。
 *
 * @author 智客通开发组
 */
@Data
@TableName("work_order_log")
public class WorkOrderLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工单 ID */
    private Long orderId;

    /** 动作：CREATE/ASSIGN/ACCEPT/PROCESS/RESOLVE/CLOSE/REOPEN/COMMENT/OVERDUE */
    private String action;

    /** 原状态 */
    private String fromStatus;

    /** 新状态 */
    private String toStatus;

    /** 操作人 ID */
    private Long operatorId;

    /** 操作人名称（快照） */
    private String operatorName;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
