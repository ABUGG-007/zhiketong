package com.zhiketong.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiketong.config.properties.AiProperties;
import com.zhiketong.dto.AiChatResponse;
import com.zhiketong.entity.AiCallLog;
import com.zhiketong.mapper.AiCallLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek（OpenAI 兼容协议）对话实现。
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
    private final AiCallLogMapper aiCallLogMapper;
    private final RestTemplate aiRestTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public boolean isAvailable() {
        return aiProperties.isEnabled() && StringUtils.hasText(aiProperties.getApiKey());
    }

    @Override
    public AiChatResponse chat(Long sessionId, Long messageId, String userMessage, List<AiMessage> history) {
        long start = System.currentTimeMillis();
        if (!isAvailable()) {
            return fallback(start, "未配置 AI Key 或 AI 未启用", sessionId, messageId, 0, 0);
        }
        try {
            List<Map<String, String>> messages = buildMessages(userMessage, history);
            Map<String, Object> body = new HashMap<>(8);
            body.put("model", aiProperties.getModel());
            body.put("messages", messages);
            body.put("temperature", aiProperties.getTemperature());
            body.put("max_tokens", aiProperties.getMaxTokens());
            body.put("stream", false);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(aiProperties.getApiKey());

            String url = aiProperties.getBaseUrl();
            if (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            url = url + "/chat/completions";

            ResponseEntity<String> response = aiRestTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode choice = root.path("choices").path(0).path("message");
            String content = choice.path("content").asText(null);
            if (!StringUtils.hasText(content)) {
                throw new IllegalStateException("大模型返回内容为空");
            }
            int promptTokens = root.path("usage").path("prompt_tokens").asInt(0);
            int completionTokens = root.path("usage").path("completion_tokens").asInt(0);
            long elapsed = System.currentTimeMillis() - start;

            saveLog(sessionId, messageId, aiProperties.getModel(),
                    promptTokens, completionTokens, elapsed, true, null);
            log.info("AI 调用成功：sessionId={}, latency={}ms, tokens={}+{}",
                    sessionId, elapsed, promptTokens, completionTokens);

            return AiChatResponse.builder()
                    .content(content.trim())
                    .model(aiProperties.getModel())
                    .elapsedMs(elapsed)
                    .fallback(false)
                    .build();
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("AI 调用失败：{}", e.getMessage());
            saveLog(sessionId, messageId, aiProperties.getModel(),
                    0, 0, elapsed, false, abbreviate(e.getMessage(), 500));
            return fallback(start, "大模型调用失败", sessionId, messageId, 0, 0);
        }
    }

    private List<Map<String, String>> buildMessages(String userMessage, List<AiMessage> history) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", SYSTEM_PROMPT));

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

    private AiChatResponse fallback(long start, String reason,
                                    Long sessionId, Long messageId,
                                    int promptTokens, int completionTokens) {
        long elapsed = System.currentTimeMillis() - start;
        saveLog(sessionId, messageId, aiProperties.getModel(),
                promptTokens, completionTokens, elapsed, false, reason);
        return AiChatResponse.builder()
                .content(FALLBACK_ANSWER)
                .model(aiProperties.getModel())
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
