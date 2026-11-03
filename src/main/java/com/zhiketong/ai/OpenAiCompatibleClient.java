package com.zhiketong.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议客户端：一份实现覆盖 DeepSeek、通义千问、智谱、Kimi、豆包、
 * 千帆、混元、MiniMax、硅基流动、OpenRouter、OneAPI/NewAPI 自建中转、Ollama 等平台。
 *
 * <p>平台差异全部收敛在「接口根地址 + 模型名 + 附加请求头」三项配置里，
 * 调用逻辑统一为 {@code POST {baseUrl}/chat/completions}；
 * 每次调用按配置里的 timeoutMs 动态构建 RestTemplate，避免不同平台共用同一超时。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleClient {

    /** 未配置超时时的兜底值 */
    private static final int DEFAULT_TIMEOUT_MS = 15000;

    /** 错误信息最大长度，避免把整页 HTML 写进日志/数据库 */
    private static final int MAX_ERROR_LENGTH = 300;

    private final RestTemplateBuilder restTemplateBuilder;
    private final ObjectMapper objectMapper;

    /**
     * 发起一次非流式对话补全。
     *
     * @param config   运行时参数（地址、Key、模型、温度、超时等）
     * @param messages 已组装好的 OpenAI 格式消息
     * @return 模型回复与 token 消耗
     */
    public Completion complete(AiRuntimeConfig config, List<Map<String, String>> messages) {
        if (!StringUtils.hasText(config.getBaseUrl())) {
            throw new IllegalStateException("接口地址为空");
        }
        if (!StringUtils.hasText(config.getModel())) {
            throw new IllegalStateException("模型名称为空");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        if (StringUtils.hasText(config.getApiKey())) {
            headers.setBearerAuth(config.getApiKey().trim());
        }
        applyExtraHeaders(headers, config.getExtraHeaders());

        Map<String, Object> body = new HashMap<>(8);
        body.put("model", config.getModel().trim());
        body.put("messages", messages);
        body.put("temperature", config.getTemperature());
        body.put("max_tokens", config.getMaxTokens() > 0 ? config.getMaxTokens() : 512);
        body.put("stream", false);

        String url = completionsUrl(config.getBaseUrl());
        long start = System.currentTimeMillis();
        try {
            ResponseEntity<String> response = restTemplate(config.getTimeoutMs())
                    .exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            String content = root.path("choices").path(0).path("message").path("content").asText(null);
            if (!StringUtils.hasText(content)) {
                throw new IllegalStateException("大模型返回内容为空");
            }
            Completion completion = new Completion();
            completion.setContent(content.trim());
            completion.setModel(root.path("model").asText(config.getModel()));
            completion.setPromptTokens(root.path("usage").path("prompt_tokens").asInt(0));
            completion.setCompletionTokens(root.path("usage").path("completion_tokens").asInt(0));
            completion.setLatencyMs(System.currentTimeMillis() - start);
            return completion;
        } catch (HttpStatusCodeException e) {
            throw new IllegalStateException(describeHttpError(e), e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            String reason = StringUtils.hasText(e.getMessage()) ? e.getMessage() : e.getClass().getSimpleName();
            throw new IllegalStateException("调用平台接口失败：" + abbreviate(reason), e);
        }
    }

    /**
     * 拼接对话补全地址，允许 baseUrl 已经写到 /chat/completions。
     */
    public static String completionsUrl(String baseUrl) {
        String url = baseUrl == null ? "" : baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/chat/completions")) {
            return url;
        }
        // 少数平台（如部分自建网关）只提供根路径，仍统一补全标准后缀
        return url + "/chat/completions";
    }

    private RestTemplate restTemplate(int timeoutMs) {
        int timeout = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
        Duration duration = Duration.ofMillis(timeout);
        return restTemplateBuilder
                .setConnectTimeout(duration)
                .setReadTimeout(duration)
                .build();
    }

    /**
     * 附加请求头：部分中转站要求 provider 自定义头（如 HTTP-Referer、X-Title）。
     * 解析失败只记日志，不阻断主流程。
     */
    private void applyExtraHeaders(HttpHeaders headers, String extraHeaders) {
        if (!StringUtils.hasText(extraHeaders)) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(extraHeaders);
            if (!node.isObject()) {
                return;
            }
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (StringUtils.hasText(entry.getKey())) {
                    headers.set(entry.getKey(), entry.getValue().asText());
                }
            }
        } catch (Exception e) {
            log.warn("附加请求头 JSON 解析失败，已忽略：{}", e.getMessage());
        }
    }

    /**
     * 把平台返回的错误体转成可读提示，便于管理端「测试连接」直接展示。
     */
    private String describeHttpError(HttpStatusCodeException e) {
        String body = e.getResponseBodyAsString();
        String detail = null;
        if (StringUtils.hasText(body)) {
            try {
                JsonNode node = objectMapper.readTree(body);
                JsonNode error = node.path("error");
                if (error.isTextual()) {
                    detail = error.asText();
                } else {
                    detail = firstText(error.path("message"), error.path("code"),
                            node.path("message"), node.path("msg"), node.path("error_msg"));
                }
            } catch (Exception ignored) {
                // 响应体不是 JSON，直接截取原文
            }
            if (!StringUtils.hasText(detail)) {
                detail = body;
            }
        }
        String prefix = "HTTP " + e.getRawStatusCode();
        if (e.getRawStatusCode() == 401 || e.getRawStatusCode() == 403) {
            prefix += "（API Key 无效或无权限）";
        } else if (e.getRawStatusCode() == 404) {
            prefix += "（接口地址或模型名称不存在）";
        } else if (e.getRawStatusCode() == 429) {
            prefix += "（请求频率或额度超限）";
        }
        return StringUtils.hasText(detail) ? prefix + "：" + abbreviate(detail) : prefix;
    }

    private String firstText(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node != null && node.isValueNode() && StringUtils.hasText(node.asText())) {
                return node.asText();
            }
        }
        return null;
    }

    private String abbreviate(String text) {
        String value = text.replaceAll("\\s+", " ").trim();
        return value.length() > MAX_ERROR_LENGTH ? value.substring(0, MAX_ERROR_LENGTH) + "…" : value;
    }

    /**
     * 一次对话补全的结果。
     */
    @Data
    public static class Completion {

        /** 回复正文 */
        private String content;

        /** 平台实际返回的模型名 */
        private String model;

        /** 输入 token */
        private int promptTokens;

        /** 输出 token */
        private int completionTokens;

        /** 耗时（毫秒） */
        private long latencyMs;
    }
}
