package com.zhiketong.dto.quality;

import com.zhiketong.entity.QualityRecord;
import com.zhiketong.enums.QualityEngine;
import com.zhiketong.enums.QualityGrade;
import com.zhiketong.enums.QualityRecordStatus;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 质检记录列表项（R-166）。命中明细与完整评分卡见 {@link QualityRecordDetailVO}。
 *
 * @author 智客通开发组
 */
@Data
public class QualityRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 质检记录 ID */
    private Long id;

    /** 会话 ID */
    private Long sessionId;

    /** 会话编号 */
    private String sessionNo;

    /** 接待坐席 ID，空表示纯机器人会话 */
    private Long agentId;

    /** 接待坐席名称 */
    private String agentName;

    /** 会话结束时间 */
    private LocalDateTime sessionEndTime;

    /** 质检得分 */
    private Integer score;

    /** 等级编码 A/B/C/D */
    private String grade;

    /** 等级名称 */
    private String gradeName;

    /** 是否合格：1 合格 0 不合格 */
    private Integer passFlag;

    /** 合格线快照 */
    private Integer passScore;

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

    /** 复核状态 */
    private String status;

    /** 复核状态名称 */
    private String statusName;

    /** 人工复核得分，为空表示未调分 */
    private Integer reviewScore;

    /** 最近复核动作：REVIEW/CONFIRM/REJECT，为空表示未复核 */
    private String reviewAction;

    /** 生效得分：复核分优先，未复核时等于自动分 */
    private Integer effectiveScore;

    /** 生效等级：按生效得分重新计算 */
    private String effectiveGrade;

    /** 生效等级名称 */
    private String effectiveGradeName;

    /** 生效是否合格：1 合格 0 不合格 */
    private Integer effectivePassFlag;

    /** 消息条数 */
    private Integer messageCount;

    /** 最近一次质检时间 */
    private LocalDateTime evaluateTime;

    /** 手动质检发起人 ID，为空表示自动质检 */
    private Long evaluateBy;

    /**
     * 实体转列表视图对象。
     *
     * @param record 质检记录实体
     * @return 视图对象
     */
    public static QualityRecordVO of(QualityRecord record) {
        QualityRecordVO vo = new QualityRecordVO();
        vo.setId(record.getId());
        vo.setSessionId(record.getSessionId());
        vo.setSessionNo(record.getSessionNo());
        vo.setAgentId(record.getAgentId());
        vo.setAgentName(record.getAgentName());
        vo.setSessionEndTime(record.getSessionEndTime());
        vo.setScore(record.getScore());
        vo.setGrade(record.getGrade());
        QualityGrade grade = QualityGrade.fromCode(record.getGrade());
        vo.setGradeName(grade == null ? record.getGrade() : grade.getDescription());
        vo.setPassFlag(record.getPassFlag());
        vo.setPassScore(record.getPassScore());
        vo.setTotalDeduct(record.getTotalDeduct());
        vo.setRuleCount(record.getRuleCount());
        vo.setHitCount(record.getHitCount());
        vo.setEngine(record.getEngine());
        QualityEngine engine = QualityEngine.fromCode(record.getEngine());
        vo.setEngineName(engine == null ? record.getEngine() : engine.getDescription());
        vo.setStatus(record.getStatus());
        QualityRecordStatus status = QualityRecordStatus.fromCode(record.getStatus());
        vo.setStatusName(status == null ? record.getStatus() : status.getDescription());
        vo.setReviewScore(record.getReviewScore());
        vo.setReviewAction(record.getReviewAction());
        int effectiveScore = record.effectiveScore();
        vo.setEffectiveScore(effectiveScore);
        QualityGrade effectiveGrade = QualityGrade.of(effectiveScore);
        vo.setEffectiveGrade(effectiveGrade.getCode());
        vo.setEffectiveGradeName(effectiveGrade.getDescription());
        vo.setEffectivePassFlag(record.effectivePassFlag());
        vo.setMessageCount(record.getMessageCount());
        vo.setEvaluateTime(record.getEvaluateTime());
        vo.setEvaluateBy(record.getEvaluateBy());
        return vo;
    }
}
