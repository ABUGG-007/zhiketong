package com.zhiketong.dto.kb;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 知识健康度看板数据（R-83）。
 *
 * @author 智客通开发组
 */
@Data
public class KbHealthVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 词条总数 */
    private long entryTotal;

    /** 启用词条数 */
    private long entryEnabled;

    /** 相似问法总数 */
    private long similarTotal;

    /** 未命中问题总数 */
    private long unmatchedTotal;

    /** 待处理未命中数 */
    private long unmatchedPending;

    /** 零命中文词条数 */
    private long zeroHitEntries;

    /** 疑似重复/冲突对数 */
    private int duplicatePairs;

    /** 命中排行（Top 10） */
    private List<HitItem> topHits;

    /** 零命中文词条（最多 20 条） */
    private List<HitItem> zeroHitList;

    /** 未命中高频问题（Top 10） */
    private List<UnmatchedItem> topUnmatched;

    /** 分类覆盖情况 */
    private List<CategoryCoverage> categories;

    /** 近 14 天未命中趋势 */
    private List<TrendItem> trend;

    /**
     * 词条命中信息。
     */
    @Data
    public static class HitItem implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long id;

        private String question;

        private String categoryName;

        private Integer hitCount;
    }

    /**
     * 未命中问题摘要。
     */
    @Data
    public static class UnmatchedItem implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long id;

        private String question;

        private Integer askCount;

        private String channel;
    }

    /**
     * 分类覆盖度。
     */
    @Data
    public static class CategoryCoverage implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long categoryId;

        private String categoryName;

        private long entryCount;

        private long zeroHitCount;
    }

    /**
     * 未命中趋势。
     */
    @Data
    public static class TrendItem implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 日期 yyyy-MM-dd */
        private String date;

        /** 当日新增未命中问题数 */
        private long count;
    }
}
