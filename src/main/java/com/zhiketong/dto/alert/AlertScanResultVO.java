package com.zhiketong.dto.alert;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 告警扫描结果（R-187）：一轮扫描的触发与冷却统计。
 *
 * @author 智客通开发组
 */
@Data
public class AlertScanResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否成功获取扫描锁 */
    private boolean locked;

    /** 本轮评估的规则数 */
    private int ruleCount;

    /** 新产生的告警数 */
    private int createdCount;

    /** 复发累加的告警数（冷却期外再次触发） */
    private int repeatedCount;

    /** 冷却期内被抑制的告警数 */
    private int suppressedCount;

    /** 超时自动关闭的告警数 */
    private int autoClosedCount;

    /** 处理耗时（毫秒） */
    private long costMillis;

    /** 执行说明 */
    private String message;

    /** 执行时间 */
    private LocalDateTime scanTime;
}
