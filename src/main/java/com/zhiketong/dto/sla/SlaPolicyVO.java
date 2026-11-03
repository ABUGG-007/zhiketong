package com.zhiketong.dto.sla;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工单 SLA 策略视图对象（R-127 ~ R-132）。
 *
 * @author 智客通开发组
 */
@Data
public class SlaPolicyVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 策略 ID */
    private Long id;

    /** 策略名称 */
    private String policyName;

    /** 适用优先级：P0/P1/P2/P3 */
    private String priority;

    /** 首次响应时限（分钟），0 表示不启用 */
    private Integer firstResponseMinutes;

    /** 回复间隔时限（分钟），0 表示不启用 */
    private Integer replyIntervalMinutes;

    /** 解决时限（分钟），0 表示不启用 */
    private Integer resolveMinutes;

    /** 预警阈值（百分比） */
    private Integer warnPercent;

    /** 超时后升级延迟（分钟），0 表示超时即升级 */
    private Integer escalateMinutes;

    /** 升级通知对象角色 */
    private String escalateRole;

    /** 是否为该优先级的默认策略：1 是 0 否 */
    private Integer defaultFlag;

    /** 策略说明 */
    private String description;

    /** 排序号 */
    private Integer sort;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
