package com.zhiketong.dto.workorder;

import com.zhiketong.dto.orderform.OrderFieldValueVO;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 工单视图对象（R-49 ~ R-54 等工单接口通用）。
 *
 * @author 智客通开发组
 */
@Data
public class WorkOrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工单 ID */
    private Long id;

    /** 工单号 */
    private String orderNo;

    /** 工单标题 */
    private String title;

    /** 问题描述 */
    private String content;

    /** 问题分类 */
    private String category;

    /** 优先级：P0/P1/P2/P3 */
    private String priority;

    /** 状态：PENDING/ASSIGNED/PROCESSING/WAITING_CUSTOMER/RESOLVED/CLOSED/REOPENED */
    private String status;

    /** SLA 计时状态：NORMAL/WARN/OVERDUE/PAUSED/FINISHED，未启用 SLA 时为空 */
    private String slaStatus;

    /** 来源：CHAT/MANUAL/PHONE */
    private String source;

    /** 关联会话 ID */
    private Long sessionId;

    /** 来源工单模板 ID */
    private Long templateId;

    /** 来源工单模板名称 */
    private String templateName;

    /** 自定义字段值：仅详情接口返回 */
    private List<OrderFieldValueVO> customFields = new ArrayList<>();

    /** 创建人 ID */
    private Long creatorId;

    /** 创建人名称 */
    private String creatorName;

    /** 受理坐席 ID */
    private Long assigneeId;

    /** 受理坐席名称 */
    private String assigneeName;

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

    /** 备注 / 满意度评价内容 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
