package com.zhiketong.dto.quality;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 人机质检对比报表（R-175，QC-07）。
 *
 * <p>统计口径（与 docs/产品推进路线图.md 第 3 章一致）：</p>
 * <ul>
 *   <li><b>对比样本</b>：区间内复核状态为「已复核 / 已确认」的质检记录，
 *       待复核记录只计入总数，不参与人机差异统计；</li>
 *   <li><b>复核分</b>：复核时确认的得分，若复核未调分则等于自动分；</li>
 *   <li><b>一致</b>：复核分 - 自动分 = 0；<b>上调</b>为正、<b>下调</b>为负，
 *       一致率 = 一致条数 / 对比样本数；</li>
 *   <li><b>明细一致率</b>：未被人工改判的规则行 / 对比样本的规则行总数。</li>
 * </ul>
 *
 * @author 智客通开发组
 */
@Data
public class QualityComparisonVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 区间内已质检会话数 */
    private long recordCount;

    /** 对比样本数（已复核 + 已确认） */
    private long reviewedCount;

    /** 待复核记录数 */
    private long pendingCount;

    /** 自动判定合格率（0-100） */
    private double autoPassRate;

    /** 复核后合格率（0-100，样本内） */
    private double reviewPassRate;

    /** 平均自动分（样本内） */
    private double avgAutoScore;

    /** 平均复核分（样本内） */
    private double avgReviewScore;

    /** 平均差异 = 复核分 - 自动分（样本内，正数表示人工上调） */
    private double avgDiff;

    /** 分数一致的样本数 */
    private long sameCount;

    /** 人工上调的样本数 */
    private long upCount;

    /** 人工下调的样本数 */
    private long downCount;

    /** 一致率（0-100） */
    private double consistencyRate;

    /** 样本内规则明细行数 */
    private long itemTotal;

    /** 被人工改判的规则明细行数 */
    private long itemOverturn;

    /** 明细一致率（0-100） */
    private double itemAgreeRate;

    /** 差异分布 */
    private List<DiffRow> diffs = new ArrayList<>();

    /** 逐规则人机对比（按改判条数倒序，最多 10 条） */
    private List<RuleRow> rules = new ArrayList<>();

    /** 复核状态分布 */
    private List<StatusRow> statuses = new ArrayList<>();

    /**
     * 分差分布行。
     */
    @Data
    public static class DiffRow implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 区间标识，如 SAME / UP_1_5 / DOWN_1_5 */
        private String bucket;

        /** 展示文案，如「基本一致」「小幅上调」 */
        private String label;

        /** 样本数 */
        private long count;
    }

    /**
     * 逐规则人机对比行。
     */
    @Data
    public static class RuleRow implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 规则编码 */
        private String ruleCode;

        /** 规则名称 */
        private String ruleName;

        /** 规则类型 */
        private String ruleType;

        /** 规则类型名称 */
        private String ruleTypeName;

        /** 自动判定命中次数 */
        private long autoHitCount;

        /** 人工改判后的命中次数 */
        private long reviewHitCount;

        /** 被人工改判的条数 */
        private long overturnCount;

        /** 该规则的明细一致率（0-100） */
        private double agreeRate;
    }

    /**
     * 复核状态分布行。
     */
    @Data
    public static class StatusRow implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 状态编码 */
        private String status;

        /** 状态名称 */
        private String statusName;

        /** 记录数 */
        private long count;
    }
}
