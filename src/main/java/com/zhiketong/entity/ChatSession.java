package com.zhiketong.entity;

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

    /** 会话编号：CS + yyyyMMddHHmmss + 4 位随机 */
    private String sessionNo;

    /** 用户 ID */
    private Long userId;

    /** 接入坐席 ID */
    private Long agentId;

    /** 状态：AI/WAITING/HUMAN/CLOSED */
    private String status;

    /** 关闭原因：USER_CLOSED/AGENT_CLOSED/TIMEOUT/SYSTEM */
    private String closeReason;

    /** 满意度评分 1-5 */
    private Integer rating;

    /** 评价内容 */
    private String ratingComment;

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

    /** 逻辑删除：0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;
}
