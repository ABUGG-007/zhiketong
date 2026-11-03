package com.zhiketong.dto.ops;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 实时运营大屏快照（R-176）。
 *
 * <p>一次请求给全屏数据：实时负载、今日累计、坐席实时表现、近 12 小时趋势与内置预警。
 * 所有数字直接来自业务库实时 COUNT，不做预聚合缓存，保证大屏与工作台口径一致。</p>
 *
 * @author 智客通开发组
 */
@Data
public class OpsScreenVO {

    /** 服务端时间（yyyy-MM-dd HH:mm:ss） */
    private String serverTime;

    /** 实时负载 */
    private Realtime realtime;

    /** 今日累计 */
    private Today today;

    /** 坐席实时表现（按在岗优先、负载升序，最多 20 行） */
    private List<AgentRow> agents = new ArrayList<>();

    /** 近 12 小时趋势 */
    private List<HourRow> hourly = new ArrayList<>();

    /** 内置阈值预警（0-3 条） */
    private List<AlertRow> alerts = new ArrayList<>();

    /** 实时负载。 */
    @Data
    public static class Realtime {

        /** 排队人数 */
        private long waitingCount;

        /** 当前最长排队时长（秒） */
        private long longestWaitSeconds;

        /** 当前排队平均等待时长（秒） */
        private long avgWaitSeconds;

        /** 在线坐席数（ONLINE） */
        private long onlineAgents;

        /** 忙碌坐席数（BUSY） */
        private long busyAgents;

        /** 人工接待中会话数 */
        private long servingSessions;

        /** 机器人接待中会话数 */
        private long aiSessions;
    }

    /** 今日累计。 */
    @Data
    public static class Today {

        /** 今日新建会话数 */
        private long sessions;

        /** 今日进入人工接待的会话数 */
        private long humanSessions;

        /** 今日已关闭会话数 */
        private long closedSessions;

        /** 今日机器人独立解决会话数（未接人工即关闭） */
        private long aiResolved;

        /** AI 自主解决率 */
        private BigDecimal aiResolveRate;

        /** 今日消息条数 */
        private long messages;

        /** 今日新建工单数 */
        private long orders;

        /** 待处理工单数（未关闭） */
        private long pendingOrders;

        /** 超时工单数 */
        private long overdueOrders;

        /** 今日评价数 */
        private long ratings;

        /** 今日平均满意度 */
        private BigDecimal avgRating;

        /** 满意度回收率（评价数 / 今日关闭会话数） */
        private BigDecimal recoveryRate;

        /** 今日首响均值（秒，仅统计已产生首响的会话） */
        private long avgFirstResponseSeconds;

        /** 今日 3 分钟回复率（按轮次） */
        private BigDecimal threeMinuteReplyRate;

        /** 今日人工接待轮次 */
        private long roundCount;

        /** 今日 3 分钟内回复轮次 */
        private long withinCount;
    }

    /** 坐席实时表现。 */
    @Data
    public static class AgentRow {

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

        /** 今日平均接入等待（秒） */
        private long avgAcceptSeconds;

        /** 今日平均首响（秒） */
        private long avgFirstReplySeconds;

        /** 累计接待数 */
        private Integer totalServed;

        /** 最近心跳时间 */
        private String lastHeartbeat;
    }

    /** 近 12 小时趋势点。 */
    @Data
    public static class HourRow {

        /** 小时标签，如 14:00 */
        private String hour;

        private long sessions;

        private long messages;
    }

    /** 内置阈值预警。 */
    @Data
    public static class AlertRow {

        /** INFO / WARN / CRITICAL */
        private String level;

        private String code;

        private String title;

        private String detail;
    }
}
