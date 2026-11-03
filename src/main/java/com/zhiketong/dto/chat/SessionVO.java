package com.zhiketong.dto.chat;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话视图对象（R-31 ~ R-34）。
 *
 * @author 智客通开发组
 */
@Data
public class SessionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话 ID */
    private Long id;

    /** 会话编号 */
    private String sessionNo;

    /** 用户 ID */
    private Long userId;

    /** 用户名称 */
    private String userName;

    /** 接入坐席 ID */
    private Long agentId;

    /** 接入坐席名称 */
    private String agentName;

    /** 期望技能组 ID（转人工时访客选择的咨询类型） */
    private Long skillGroupId;

    /** 期望技能组名称 */
    private String skillGroupName;

    /** 状态：AI/WAITING/HUMAN/CLOSED */
    private String status;

    /** 关闭原因 */
    private String closeReason;

    /** 满意度评分 1-5 */
    private Integer rating;

    /** 评价内容 */
    private String ratingComment;

    /** 访客首条有效消息时间（首响计时起点） */
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

    /** 创建时间 */
    private LocalDateTime createTime;
}
