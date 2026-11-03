package com.zhiketong.dto.sla;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工单 SLA 停表流水视图对象（R-133 详情内嵌）。
 *
 * @author 智客通开发组
 */
@Data
public class OrderSlaPauseVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 停表流水 ID */
    private Long id;

    /** 停表类型编码：WAITING_CUSTOMER / OTHER */
    private String pauseType;

    /** 停表类型名称 */
    private String pauseTypeLabel;

    /** 停表原因 */
    private String reason;

    /** 停表开始时间 */
    private LocalDateTime pauseTime;

    /** 恢复计时时间，为空表示仍在停表中 */
    private LocalDateTime resumeTime;

    /** 本次停表时长（分钟），仍在停表中时为空 */
    private Integer durationMinutes;

    /** 操作人 ID */
    private Long operatorId;

    /** 操作人名称 */
    private String operatorName;
}
