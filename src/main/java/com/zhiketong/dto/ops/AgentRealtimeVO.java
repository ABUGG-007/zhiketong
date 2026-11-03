package com.zhiketong.dto.ops;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 坐席实时表现（R-179，DB-03）。
 *
 * <p>在线状态与并发来自 {@code agent_status}，今日接入量与响应时长按当天会话聚合，
 * 用于运营在监控页判断「谁在忙、谁响应慢」。</p>
 *
 * @author 智客通开发组
 */
@Data
public class AgentRealtimeVO {

    private Long agentId;

    private String agentName;

    /** 在线状态：ONLINE / BUSY / OFFLINE */
    private String onlineStatus;

    /** 当前接待数 */
    private int currentCount;

    /** 最大并发 */
    private int maxConcurrent;

    /** 今日接入会话数 */
    private long todayServed;

    /** 今日已关闭会话数 */
    private long todayClosed;

    /** 今日平均接入等待（秒） */
    private long avgAcceptSeconds;

    /** 今日平均首响（秒） */
    private long avgFirstReplySeconds;

    /** 今日平均会话时长（秒） */
    private long avgSessionSeconds;

    /** 累计接待数 */
    private Integer totalServed;

    /** 最近心跳时间 */
    private LocalDateTime lastHeartbeat;
}
