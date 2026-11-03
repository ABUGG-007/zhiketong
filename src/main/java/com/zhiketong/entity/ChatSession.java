package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 客服会话实体，对应表 chat_session。
 * <p>
 * status 取值见 {@link com.zhiketong.enums.SessionStatus}（AI/WAITING/HUMAN/CLOSED），
 * closeReason 取值见 {@link com.zhiketong.enums.CloseReason}。
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("chat_session")
public class ChatSession extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID，由多租户拦截器在写入时按当前上下文填充 */
    private Long tenantId;


    /** 会话编号：CS + yyyyMMddHHmmss + 4 位随机 */
    private String sessionNo;

    /** 用户 ID */
    private Long userId;

    /** 接入坐席 ID */
    private Long agentId;

    /** 期望技能组 ID（转人工时确定，空表示不限；分配时优先在同组坐席中挑选） */
    private Long skillGroupId;

    /** 状态：AI/WAITING/HUMAN/CLOSED */
    private String status;

    /** 关闭原因：USER_CLOSED/AGENT_CLOSED/TIMEOUT/SYSTEM */
    private String closeReason;

    /** 满意度评分 1-5 */
    private Integer rating;

    /** 评价内容 */
    private String ratingComment;

    /** 访客首条有效消息时间（首响计时起点，口径见 docs/产品推进路线图.md 第 3 章） */
    private LocalDateTime firstUserMsgTime;

    /** 首次有效回复时间（AI/坐席，不含系统欢迎语） */
    private LocalDateTime firstReplyTime;

    /** 进入排队时间 */
    private LocalDateTime queueEnterTime;

    /** 请求转人工时间 */
    private LocalDateTime transferTime;

    /** 坐席接入时间 */
    private LocalDateTime acceptTime;

    /** 会话结束时间 */
    private LocalDateTime endTime;

    /** 消息条数 */
    private Integer messageCount;

    /** 人工接待期间访客有效提问轮次（会话关闭时计算，S6-A） */
    private Integer replyRoundCount;

    /** 其中坐席 180 秒内首次回复的轮次，用于 3 分钟回复率 */
    @TableField("reply_3min_count")
    private Integer reply3minCount;

    /** 轮次指标计算时间，空表示尚未计算 */
    private LocalDateTime opsMetricTime;

    /** 逻辑删除：0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;
}
