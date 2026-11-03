package com.zhiketong.ai;

import com.zhiketong.config.properties.AiProperties;
import com.zhiketong.dto.AiChatResponse;
import com.zhiketong.entity.AiCallLog;
import com.zhiketong.mapper.AiCallLogMapper;
import com.zhiketong.service.AiModelConfigService;
import com.zhiketong.service.TenantPlanService;
import com.zhiketong.service.TenantUsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话实现：按 OpenAI 兼容协议调用大模型，支持任意平台与中转站。
 *
 * <p>运行参数优先取管理端「大模型配置」中启用且默认的那一条（{@link AiModelConfigService}），
 * 管理端保存后立即生效、无需重启；数据库没有可用配置时回退到 application.yml 的 zhiketong.ai。</p>
 *
 * <p>未配置 API Key、调用失败或超时时降级为兜底话术，保证演示环境不中断；
 * 每次真实调用都会写入 ai_call_log 便于统计。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeepSeekAiChatService implements AiChatService {

    private static final String SYSTEM_PROMPT =
            "你是「智客通」智能客服助手，服务对象是中小企业客户。"
                    + "请用简洁、礼貌、专业的中文回答，控制在 300 字以内；"
                    + "不编造订单、物流、退款等事实；"
                    + "如果用户问题需要人工核验或表示要投诉，请建议用户点击「转人工」；"
                    + "不要输出 Markdown 表格或代码块。";

    private static final String FALLBACK_ANSWER =
            "抱歉，智能客服暂时繁忙。您可以换个说法再问一次，或者点击「转人工」由人工客服为您服务。";

    private final AiProperties aiProperties;
    private final AiModelConfigService aiModelConfigService;
    private final OpenAiCompatibleClient openAiCompatibleClient;
    private final AiCallLogMapper aiCallLogMapper;
    private final TenantUsageService tenantUsageService;

    @Override
    public boolean isAvailable() {
        return canCall(currentConfig());
    }

    @Override
    public AiChatResponse chat(Long sessionId, Long messageId, String userMessage, List<AiMessage> history) {
        return complete(sessionId, messageId, SYSTEM_PROMPT, userMessage, history, FALLBACK_ANSWER);
    }

    @Override
    public AiChatResponse complete(Long sessionId, String systemPrompt, String userPrompt) {
        // 坐席辅助类任务没有对客话术，失败时返回空内容由调用方规则兜底
        return complete(sessionId, null, systemPrompt, userPrompt, null, "");
    }

    /**
     * 统一的大模型调用入口：对话与坐席辅助任务共用配置、日志与降级逻辑。
     *
     * @param sessionId       会话 ID
     * @param messageId       消息 ID，任务型调用传 null
     * @param systemPrompt    系统提示词
     * @param userMessage     用户输入或任务输入
     * @param history         历史对话，可为空
     * @param fallbackContent 降级时返回的内容，任务型调用传空串
     */
    private AiChatResponse complete(Long sessionId, Long messageId, String systemPrompt, String userMessage,
                                   List<AiMessage> history, String fallbackContent) {
        long start = System.currentTimeMillis();
        AiRuntimeConfig config = currentConfig();
        if (!canCall(config)) {
            return fallback(start, "未配置 AI Key 或 AI 未启用", config, sessionId, messageId, fallbackContent);
        }
        if (tenantUsageService.exhausted(TenantPlanService.METRIC_AI)) {
            // 套餐额度用尽时降级为本地方案，对话与坐席辅助链路均不中断（S4-B PL-04）
            return fallback(start, "本月 AI 应答额度已用尽，请联系管理员升级套餐",
                    config, sessionId, messageId, fallbackContent);
        }
        try {
            List<Map<String, String>> messages = buildMessages(systemPrompt, userMessage, history);
            OpenAiCompatibleClient.Completion completion = openAiCompatibleClient.complete(config, messages);

            saveLog(sessionId, messageId, completion.getModel(),
                    completion.getPromptTokens(), completion.getCompletionTokens(),
                    completion.getLatencyMs(), true, null);
            log.info("AI 调用成功：config={}, model={}, sessionId={}, latency={}ms, tokens={}+{}",
                    config.getName(), completion.getModel(), sessionId, completion.getLatencyMs(),
                    completion.getPromptTokens(), completion.getCompletionTokens());

            return AiChatResponse.builder()
                    .content(completion.getContent())
                    .model(completion.getModel())
                    .elapsedMs(completion.getLatencyMs())
                    .fallback(false)
                    .build();
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("AI 调用失败：config={}, model={}, reason={}",
                    config.getName(), config.getModel(), e.getMessage());
            saveLog(sessionId, messageId, config.getModel(),
                    0, 0, elapsed, false, abbreviate(e.getMessage(), 500));
            return fallback(start, "大模型调用失败", config, sessionId, messageId, fallbackContent);
        }
    }

    /**
     * 当前生效的运行时参数：管理端数据库配置 > application.yml 内置配置。
     *
     * <p>读取数据库异常时不让对话链路中断，直接回退内置配置。</p>
     */
    private AiRuntimeConfig currentConfig() {
        try {
            AiRuntimeConfig runtime = aiModelConfigService.resolveRuntime();
            if (runtime != null) {
                return runtime;
            }
        } catch (Exception e) {
            log.warn("读取大模型配置失败，回退内置配置：{}", e.getMessage());
        }
        AiRuntimeConfig builtin = AiRuntimeConfig.of(aiProperties);
        if (!aiProperties.isEnabled()) {
            // 显式关闭：清空 Key 并收回无 Key 豁免，使 isAvailable() 返回 false，走兜底话术
            builtin.setApiKey("");
            builtin.setKeyOptional(false);
        }
        return builtin;
    }

    /**
     * 是否具备真实调用条件：地址与模型必填；有 Key 或平台允许不填 Key（本地推理 / 内网自建）。
     */
    private boolean canCall(AiRuntimeConfig config) {
        if (config == null
                || !StringUtils.hasText(config.getBaseUrl())
                || !StringUtils.hasText(config.getModel())) {
            return false;
        }
        return StringUtils.hasText(config.getApiKey()) || config.isKeyOptional();
    }

    private List<Map<String, String>> buildMessages(String systemPrompt, String userMessage,
                                                    List<AiMessage> history) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", systemPrompt));

        if (history != null && !history.isEmpty()) {
            int limit = Math.max(0, aiProperties.getHistoryRounds() * 2);
            int from = Math.max(0, history.size() - limit);
            for (AiMessage item : history.subList(from, history.size())) {
                if (item != null && StringUtils.hasText(item.getContent())) {
                    messages.add(message(item.getRole(), item.getContent()));
                }
            }
        }
        messages.add(message("user", userMessage));
        return messages;
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> message = new HashMap<>(4);
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private AiChatResponse fallback(long start, String reason, AiRuntimeConfig config,
                                    Long sessionId, Long messageId, String fallbackContent) {
        long elapsed = System.currentTimeMillis() - start;
        String model = config == null ? null : config.getModel();
        saveLog(sessionId, messageId, model, 0, 0, elapsed, false, reason);
        return AiChatResponse.builder()
                .content(fallbackContent)
                .model(model)
                .elapsedMs(elapsed)
                .fallback(true)
                .fallbackReason(reason)
                .build();
    }

    private void saveLog(Long sessionId, Long messageId, String model,
                         int promptTokens, int completionTokens,
                         long latencyMs, boolean success, String errorMsg) {
        try {
            AiCallLog callLog = new AiCallLog();
            callLog.setSessionId(sessionId);
            callLog.setMessageId(messageId);
            callLog.setModel(model);
            callLog.setPromptTokens(promptTokens);
            callLog.setCompletionTokens(completionTokens);
            callLog.setLatencyMs((int) Math.min(latencyMs, Integer.MAX_VALUE));
            callLog.setSuccess(success ? 1 : 0);
            callLog.setErrorMsg(errorMsg);
            aiCallLogMapper.insert(callLog);
        } catch (Exception e) {
            log.warn("写入 AI 调用日志失败：{}", e.getMessage());
        }
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        String value = new String(text.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
