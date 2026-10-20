package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 聊天消息实体，对应表 chat_message。
 * <p>
 * 该表只有 create_time、没有 update_time，因此不继承 BaseEntity。
 * senderType 取值见 {@link com.zhiketong.enums.SenderType}（USER/AI/AGENT/SYSTEM）。
 *
 * @author 智客通开发组
 */
@Data
@TableName("chat_message")
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话 ID */
    private Long sessionId;

    /** 发送方类型：USER/AI/AGENT/SYSTEM */
    private String senderType;

    /** 发送人 ID（AI/SYSTEM 为空） */
    private Long senderId;

    /** 消息内容 */
    private String content;

    /** 内容类型：TEXT */
    private String contentType;

    /** 命中的知识库条目 ID */
    private Long matchedKbId;

    /** AI 置信度 0.0000-1.0000 */
    private BigDecimal aiConfidence;

    /** AI 模型名称 */
    private String aiModel;

    /** 已读标记：0 未读 1 已读 */
    private Integer readFlag;

    /** 创建时间 */
    private LocalDateTime createTime;
}
