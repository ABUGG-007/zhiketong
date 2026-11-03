package com.zhiketong.dto.chat;

import lombok.Data;

import java.io.Serializable;

/**
 * 发送消息响应（R-36）。
 *
 * @author 智客通开发组
 */
@Data
public class SendMessageResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 落库后的用户/坐席消息 */
    private MessageVO message;

    /** AI 回复，仅会话状态为 AI 时有值 */
    private MessageVO aiReply;

    /** 处理后的会话状态 */
    private String sessionStatus;

    /** 会话连续未命中次数（Redis 统一计数，本轮回复后的值），仅 AI 接待阶段有意义 */
    private long fallbackCount;

    /** 连续未命中是否已达建议转人工阈值（阈值见 zhiketong.chat.fallback-transfer-threshold） */
    private boolean suggestTransfer;
}
