package com.zhiketong.dto.voc;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户之声概览（R-205）。
 *
 * <p>面向知识库运营的一屏结论：还有多少问法没被覆盖、聚类覆盖率多高、
 * 哪几个主题最热、最近问法从哪些渠道进来。</p>
 *
 * @author 智客通开发组
 */
@Data
public class VocOverviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 未命中问法总条数 */
    private long unmatchedTotal;

    /** 其中仍待处理的条数 */
    private long unmatchedPending;

    /** 已聚类（有归属主题）的条数 */
    private long clusteredCount;

    /** 聚类覆盖率 = 已聚类 / 总条数，保留四位小数 */
    private BigDecimal clusterRate;

    /** 主题总数 */
    private long topicCount;

    /** 自动聚类主题数 */
    private long autoTopicCount;

    /** 人工创建主题数 */
    private long manualTopicCount;

    /** 热点主题（提问次数 ≥ 阈值的主题数） */
    private long hotTopicCount;

    /** 热点主题榜（按累计提问次数倒序，最多 10 条） */
    private List<VocTopicVO> topTopics = new ArrayList<>();

    /** 渠道分布 */
    private List<ChannelItem> channels = new ArrayList<>();

    /** 近 7 天新增未命中趋势 */
    private List<TrendItem> trend = new ArrayList<>();

    /** 最近一轮聚类时间，未执行过为 null */
    private LocalDateTime lastClusterTime;

    /** 渠道分布项。 */
    @Data
    public static class ChannelItem implements Serializable {

        private static final long serialVersionUID = 1L;

        /** SITE / EMBED */
        private String channel;

        /** 中文名 */
        private String channelName;

        /** 问法条数 */
        private long questionCount;

        /** 累计提问次数 */
        private long askCount;
    }

    /** 趋势项。 */
    @Data
    public static class TrendItem implements Serializable {

        private static final long serialVersionUID = 1L;

        /** yyyy-MM-dd */
        private String date;

        /** 当日首次出现的问法条数 */
        private long count;
    }
}
