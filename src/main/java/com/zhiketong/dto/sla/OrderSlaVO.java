package com.zhiketong.dto.sla;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 工单 SLA 计时详情视图对象（R-133）。
 *
 * <p>返回计时记录的策略快照、三级截止时刻、停表累计与状态位，并附带 {@code serverTime}
 * 供前端计算剩余时长与进度条，避免依赖客户端时钟。停表流水按时间倒序返回。</p>
 *
 * @author 智客通开发组
 */
@Data
public class OrderSlaVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工单 ID */
    private Long orderId;

    /** SLA 计时记录 ID */
    private Long slaId;

    /** 策略名称快照 */
    private String policyName;

    /** 优先级快照 */
    private String priority;

    /** 首次响应时限快照（分钟），0 表示未启用 */
    private Integer firstResponseMinutes;

    /** 回复间隔时限快照（分钟），0 表示未启用 */
    private Integer replyIntervalMinutes;

    /** 解决时限快照（分钟），0 表示未启用 */
    private Integer resolveMinutes;

    /** 预警阈值快照（百分比） */
    private Integer warnPercent;

    /** 超时后升级延迟快照（分钟） */
    private Integer escalateMinutes;

    /** 计时起点 */
    private LocalDateTime startTime;

    /** 首次响应截止时间（含停表顺延） */
    private LocalDateTime firstResponseDeadline;

    /** 实际首次响应时间，为空表示尚未首响 */
    private LocalDateTime firstResponseTime;

    /** 最近一次坐席回复时间 */
    private LocalDateTime lastReplyTime;

    /** 下次回复截止时间（含停表顺延），为空表示未启用回复间隔 */
    private LocalDateTime replyDueTime;

    /** 解决截止时间（含停表顺延），与 work_order.sla_deadline 同步 */
    private LocalDateTime resolveDeadline;

    /** 累计停表分钟数 */
    private Integer pauseMinutes;

    /** 当前是否停表中：1 是 0 否 */
    private Integer paused;

    /** 本次停表开始时间 */
    private LocalDateTime pauseStart;

    /** 计时状态：RUNNING / PAUSED / FINISHED */
    private String status;

    /** SLA 状态：NORMAL / WARN / OVERDUE / PAUSED / FINISHED */
    private String slaStatus;

    /** SLA 状态名称 */
    private String slaStatusLabel;

    /** 工单是否已标记超时：0 否 1 是 */
    private Integer overdueFlag;

    /** 服务端当前时间，供前端计算剩余时长与进度 */
    private LocalDateTime serverTime;

    /** 停表流水，按停表开始时间倒序 */
    private List<OrderSlaPauseVO> pauses = new ArrayList<>();
}
