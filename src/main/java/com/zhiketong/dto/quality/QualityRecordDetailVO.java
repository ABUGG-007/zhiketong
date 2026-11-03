package com.zhiketong.dto.quality;

import com.zhiketong.entity.QualityRecord;
import com.zhiketong.enums.QualityEngine;
import com.zhiketong.enums.QualityGrade;
import com.zhiketong.enums.QualityRecordStatus;
import com.zhiketong.enums.QualityRuleType;
import com.zhiketong.enums.QualitySeverity;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 质检记录详情（R-167）：评分卡 + 逐规则命中明细。
 *
 * @author 智客通开发组
 */
@Data
public class QualityRecordDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 质检记录 ID */
    private Long id;

    /** 会话 ID */
    private Long sessionId;

    /** 会话编号 */
    private String sessionNo;

    /** 访客用户 ID */
    private Long customerId;

    /** 接待坐席 ID */
    private Long agentId;

    /** 接待坐席名称 */
    private String agentName;

    /** 会话结束时间 */
    private LocalDateTime sessionEndTime;

    /** 消息条数 */
    private Integer messageCount;

    /** 访客消息条数 */
    private Integer customerMsgCount;

    /** 坐席消息条数 */
    private Integer agentMsgCount;

    /** 智能客服消息条数 */
    private Integer aiMsgCount;

    /** 质检得分 */
    private Integer score;

    /** 等级 */
    private String grade;

    /** 等级名称 */
    private String gradeName;

    /** 合格线快照 */
    private Integer passScore;

    /** 是否合格：1 合格 0 不合格 */
    private Integer passFlag;

    /** 累计扣分 */
    private Integer totalDeduct;

    /** 参与评估规则数 */
    private Integer ruleCount;

    /** 命中规则数 */
    private Integer hitCount;

    /** 评估引擎 */
    private String engine;

    /** 评估引擎名称 */
    private String engineName;

    /** 大模型名称 */
    private String aiModel;

    /** AI 质检结论 */
    private String aiSummary;

    /** 复核状态 */
    private String status;

    /** 复核状态名称 */
    private String statusName;

    /** 复核人名称 */
    private String reviewerName;

    /** 复核意见 */
    private String reviewComment;

    /** 人工复核得分 */
    private Integer reviewScore;

    /** 复核时间 */
    private LocalDateTime reviewTime;

    /** 最近复核动作：REVIEW/CONFIRM/REJECT，为空表示未复核 */
    private String reviewAction;

    /** 生效得分：复核分优先，未复核时等于自动分 */
    private Integer effectiveScore;

    /** 生效等级 */
    private String effectiveGrade;

    /** 生效等级名称 */
    private String effectiveGradeName;

    /** 生效是否合格：1 合格 0 不合格 */
    private Integer effectivePassFlag;

    /** 复核分与自动分差值（复核分 - 自动分），未复核为 0 */
    private Integer scoreDiff;

    /** 手动质检发起人 ID */
    private Long evaluateBy;

    /** 最近一次质检时间 */
    private LocalDateTime evaluateTime;

    /** 逐规则明细 */
    private List<Item> items = new ArrayList<>();

    /**
     * 实体转详情视图对象（明细由服务层填充）。
     *
     * @param record 质检记录实体
     * @return 详情视图对象
     */
    public static QualityRecordDetailVO of(QualityRecord record) {
        QualityRecordDetailVO vo = new QualityRecordDetailVO();
        vo.setId(record.getId());
        vo.setSessionId(record.getSessionId());
        vo.setSessionNo(record.getSessionNo());
        vo.setCustomerId(record.getCustomerId());
        vo.setAgentId(record.getAgentId());
        vo.setAgentName(record.getAgentName());
        vo.setSessionEndTime(record.getSessionEndTime());
        vo.setMessageCount(record.getMessageCount());
        vo.setCustomerMsgCount(record.getCustomerMsgCount());
        vo.setAgentMsgCount(record.getAgentMsgCount());
        vo.setAiMsgCount(record.getAiMsgCount());
        vo.setScore(record.getScore());
        vo.setGrade(record.getGrade());
        QualityGrade grade = QualityGrade.fromCode(record.getGrade());
        vo.setGradeName(grade == null ? record.getGrade() : grade.getDescription());
        vo.setPassScore(record.getPassScore());
        vo.setPassFlag(record.getPassFlag());
        vo.setTotalDeduct(record.getTotalDeduct());
        vo.setRuleCount(record.getRuleCount());
        vo.setHitCount(record.getHitCount());
        vo.setEngine(record.getEngine());
        QualityEngine engine = QualityEngine.fromCode(record.getEngine());
        vo.setEngineName(engine == null ? record.getEngine() : engine.getDescription());
        vo.setAiModel(record.getAiModel());
        vo.setAiSummary(record.getAiSummary());
        vo.setStatus(record.getStatus());
        QualityRecordStatus status = QualityRecordStatus.fromCode(record.getStatus());
        vo.setStatusName(status == null ? record.getStatus() : status.getDescription());
        vo.setReviewerName(record.getReviewerName());
        vo.setReviewComment(record.getReviewComment());
        vo.setReviewScore(record.getReviewScore());
        vo.setReviewTime(record.getReviewTime());
        vo.setReviewAction(record.getReviewAction());
        int effectiveScore = record.effectiveScore();
        vo.setEffectiveScore(effectiveScore);
        QualityGrade effectiveGrade = QualityGrade.of(effectiveScore);
        vo.setEffectiveGrade(effectiveGrade.getCode());
        vo.setEffectiveGradeName(effectiveGrade.getDescription());
        vo.setEffectivePassFlag(record.effectivePassFlag());
        vo.setScoreDiff(effectiveScore - (record.getScore() == null ? 0 : record.getScore()));
        vo.setEvaluateBy(record.getEvaluateBy());
        vo.setEvaluateTime(record.getEvaluateTime());
        return vo;
    }

    /**
     * 单条规则命中明细。
     */
    @Data
    public static class Item implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 明细 ID */
        private Long id;

        /** 规则 ID */
        private Long ruleId;

        /** 规则编码 */
        private String ruleCode;

        /** 规则名称 */
        private String ruleName;

        /** 规则类型 */
        private String ruleType;

        /** 规则类型名称 */
        private String ruleTypeName;

        /** 严重程度 */
        private String severity;

        /** 严重程度名称 */
        private String severityName;

        /** 是否命中：1 命中 0 未命中 */
        private Integer hit;

        /** 实际扣分 */
        private Integer deductScore;

        /** 命中证据 */
        private String evidence;

        /** 改进建议 */
        private String suggestion;

        /** 判定来源：RULE/AI */
        private String source;

        /** 人工改判后的命中：1 命中 0 未命中，为空表示未改判 */
        private Integer reviewHit;

        /** 人工改判后的扣分，为空表示未改判 */
        private Integer reviewDeduct;

        /** 生效命中结论：人工改判优先 */
        private Integer effectiveHit;

        /** 生效扣分：人工改判优先 */
        private Integer effectiveDeduct;

        /** 是否被人工改判过 */
        private Boolean overturned;

        /**
         * 填充类型与严重程度的中文名称。
         */
        public void fillNames() {
            QualityRuleType type = QualityRuleType.fromCode(ruleType);
            this.ruleTypeName = type == null ? ruleType : type.getDescription();
            QualitySeverity level = QualitySeverity.fromCode(severity);
            this.severityName = level == null ? severity : level.getDescription();
        }
    }
}
