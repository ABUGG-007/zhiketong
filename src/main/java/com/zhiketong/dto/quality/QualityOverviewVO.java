package com.zhiketong.dto.quality;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 质检概览（R-169）：合格率、平均分、等级分布、规则命中排行与坐席表现。
 *
 * <p>统计口径与 docs/产品推进路线图.md 第 3 章一致：
 * 「质检合格率 = 合格会话 / 已质检会话」，分母只统计已产生质检记录的会话。</p>
 *
 * @author 智客通开发组
 */
@Data
public class QualityOverviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 统计区间内已质检会话数 */
    private long recordCount;

    /** 合格会话数（自动分或复核分 ≥ 合格线） */
    private long passCount;

    /** 不合格会话数 */
    private long failCount;

    /** 合格率（0-100，保留一位小数） */
    private double passRate;

    /** 平均得分（0-100，保留一位小数） */
    private double avgScore;

    /** 待复核记录数 */
    private long pendingCount;

    /** 已复核（未归档）记录数 */
    private long reviewedCount;

    /** 已确认归档记录数 */
    private long confirmedCount;

    /** 参与评估的启用规则数 */
    private long ruleCount;

    /** 已关闭会话总数（用于说明覆盖率） */
    private long closedSessionCount;

    /** 质检覆盖率 = 已质检 / 已关闭会话（0-100） */
    private double coverageRate;

    /** 等级分布 */
    private List<GradeRow> grades = new ArrayList<>();

    /** 规则命中排行（只含命中次数大于 0 的规则） */
    private List<RuleHitRow> rules = new ArrayList<>();

    /** 坐席质检表现（按平均分升序，分数最低的排前面） */
    private List<AgentRow> agents = new ArrayList<>();

    /**
     * 等级分布行。
     */
    @Data
    public static class GradeRow implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 等级编码 */
        private String grade;

        /** 等级名称 */
        private String gradeName;

        /** 会话数 */
        private long count;
    }

    /**
     * 规则命中排行行。
     */
    @Data
    public static class RuleHitRow implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 规则编码 */
        private String ruleCode;

        /** 规则名称 */
        private String ruleName;

        /** 规则类型 */
        private String ruleType;

        /** 规则类型名称 */
        private String ruleTypeName;

        /** 命中次数 */
        private long hitCount;
    }

    /**
     * 坐席质检表现行。
     */
    @Data
    public static class AgentRow implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 坐席 ID */
        private Long agentId;

        /** 坐席名称 */
        private String agentName;

        /** 质检会话数 */
        private long recordCount;

        /** 平均得分 */
        private double avgScore;

        /** 不合格会话数 */
        private long failCount;
    }
}
