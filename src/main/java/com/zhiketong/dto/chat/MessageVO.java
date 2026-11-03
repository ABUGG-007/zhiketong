package com.zhiketong.dto.chat;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 消息视图对象（R-35 / R-36 / S-03 / S-04 / S-05）。
 *
 * @author 智客通开发组
 */
@Data
public class MessageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息 ID */
    private Long id;

    /** 会话 ID */
    private Long sessionId;

    /** 发送方类型：USER/AI/AGENT/SYSTEM */
    private String senderType;

    /** 发送人 ID（AI/SYSTEM 为 0） */
    private Long senderId;

    /** 发送人名称 */
    private String senderName;

    /** 消息内容 */
    private String content;

    /** 内容类型：TEXT */
    private String contentType;

    /** 命中的知识库条目 ID */
    private Long matchedKbId;

    /** AI 置信度 */
    private BigDecimal aiConfidence;

    /** AI 模型名称 */
    private String aiModel;

    /** 已读标记：0 未读 1 已读 */
    private Integer readFlag;

    /** 应答分类：DIRECT / REFERENCE / FREE / SUGGEST / CHITCHAT / INVALID / FALLBACK */
    private String answerCategory;

    /** 创建时间 */
    private LocalDateTime createTime;
}
