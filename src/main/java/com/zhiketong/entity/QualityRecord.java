package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 质检记录实体，对应表 quality_record。
 * <p>
 * 一个会话只保留一条记录（{@code uk_session}），重复质检覆盖写；
 * engine 见 {@link com.zhiketong.enums.QualityEngine}，grade 见 {@link com.zhiketong.enums.QualityGrade}，
 * status 见 {@link com.zhiketong.enums.QualityRecordStatus}。
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("quality_record")
public class QualityRecord extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID，由多租户拦截器在写入时按当前上下文填充 */
    private Long tenantId;

    /** 会话 ID */
    private Long sessionId;

    /** 会话编号快照 */
    private String sessionNo;

    /** 访客用户 ID 快照 */
    private Long customerId;

    /** 接待坐席 ID 快照，空表示纯机器人会话 */
    private Long agentId;

    /** 接待坐席名称快照 */
    private String agentName;

    /** 会话结束时间快照 */
    private LocalDateTime sessionEndTime;

    /** 参与质检的消息条数 */
    private Integer messageCount;

    /** 访客消息条数 */
    private Integer customerMsgCount;

    /** 坐席消息条数 */
    private Integer agentMsgCount;

    /** 智能客服消息条数 */
    private Integer aiMsgCount;

    /** 质检得分 0-100 */
    private Integer score;

    /** 等级：A/B/C/D */
    private String grade;

    /** 本次判定使用的合格线快照 */
    private Integer passScore;

    /** 是否合格：1 合格 0 不合格 */
    private Integer passFlag;

    /** 累计扣分 */
    private Integer totalDeduct;

    /** 参与评估的规则数 */
    private Integer ruleCount;

    /** 命中规则数 */
    private Integer hitCount;

    /** 评估引擎：AI/RULE/MIX */
    private String engine;

    /** 语义与情感评估所用模型 */
    private String aiModel;

    /** AI 质检结论摘要 */
    private String aiSummary;

    /** 复核状态：PENDING/REVIEWED/CONFIRMED */
    private String status;

    /** 复核人 ID */
    private Long reviewerId;

    /** 复核人名称快照 */
    private String reviewerName;

    /** 复核意见 */
    private String reviewComment;

    /** 人工复核得分，为空表示复核未调分 */
    private Integer reviewScore;

    /** 复核时间 */
    private LocalDateTime reviewTime;

    /** 最近复核动作：REVIEW/CONFIRM/REJECT，为空表示尚未复核 */
    private String reviewAction;

    /** 手动质检发起人 ID，为空表示定时任务自动质检 */
    private Long evaluateBy;

    /** 最近一次质检时间 */
    private LocalDateTime evaluateTime;

    /** 逻辑删除：0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;

    /**
     * 生效得分：复核分优先，未复核时取自动质检分。
     *
     * @return 当前生效得分 0-100
     */
    public int effectiveScore() {
        if (reviewScore != null) {
            return reviewScore;
        }
        return score == null ? 0 : score;
    }

    /**
     * 生效是否合格：以生效得分与合格线快照比对，保证复核调分后判定同步变化。
     *
     * @return 1 合格 0 不合格
     */
    public int effectivePassFlag() {
        return effectiveScore() >= (passScore == null ? 0 : passScore) ? 1 : 0;
    }
}
