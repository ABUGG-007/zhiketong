package com.zhiketong.dto.workorder;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工单流转日志视图对象（R-63）。
 *
 * @author 智客通开发组
 */
@Data
public class WorkOrderLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 日志 ID */
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
