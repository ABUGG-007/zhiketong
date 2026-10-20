package com.zhiketong.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 大模型配置项（application.yml 中 zhiketong.ai）。
 *
 * <p>默认对接 DeepSeek（OpenAI 兼容协议），API Key 通过环境变量 DEEPSEEK_API_KEY 注入。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "zhiketong.ai")
public class AiProperties {

    /** 是否启用大模型（关闭时只走知识库/兜底回复） */
    private boolean enabled = true;

    /** 提供方标识 */
    private String provider = "deepseek";

    /** OpenAI 兼容的接口根地址 */
    private String baseUrl = "https://api.deepseek.com";

    /** API Key，留空则降级 */
    private String apiKey = "";

    /** 模型名称 */
    private String model = "deepseek-chat";

    /** 采样温度 */
    private double temperature = 0.3;

    /** 最大输出 token */
    private int maxTokens = 512;

    /** 请求超时（毫秒） */
    private int timeoutMs = 15000;

    /** 携带的历史对话轮数 */
    private int historyRounds = 6;
}
