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
     * 按自定义系统提示词执行任务型生成：实时推荐答案、进线摘要、服务小结等。
     *
     * <p>结果只作为坐席辅助草稿返回，调用方不得直接对客发送；
     * 不可用或调用失败时 {@code fallback=true} 且 {@code content} 为空，由调用方走规则兜底。</p>
     *
     * @param sessionId    会话 ID（仅用于 ai_call_log，可为空）
     * @param systemPrompt 系统提示词
     * @param userPrompt   任务输入
     * @return 生成结果
     */
    AiChatResponse complete(Long sessionId, String systemPrompt, String userPrompt);

    /**
     * 当前是否具备真实调用条件（启用且配置了 Key）。
     */
    boolean isAvailable();
}
