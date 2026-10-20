package com.zhiketong.ai;

import com.zhiketong.dto.AiChatResponse;

import java.util.List;

/**
 * AI 对话服务抽象：便于后续替换为通义千问、文心一言等实现。
 */
public interface AiChatService {

    /**
     * 调用大模型生成回复。
     *
     * @param sessionId   会话 ID（可为空，仅用于日志）
     * @param messageId   消息 ID（可为空，仅用于日志）
     * @param userMessage 用户消息
     * @param history     历史对话（可为空）
     */
    AiChatResponse chat(Long sessionId, Long messageId, String userMessage, List<AiMessage> history);

    /**
     * 当前是否具备真实调用条件（启用且配置了 Key）。
     */
    boolean isAvailable();
}
