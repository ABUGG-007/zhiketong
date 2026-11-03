package com.zhiketong.ai;

import com.zhiketong.config.properties.AiProperties;
import lombok.Data;
import org.springframework.util.StringUtils;

/**
 * 大模型运行时参数：屏蔽「管理端数据库配置」与「application.yml 内置配置」的差异。
 *
 * <p>对话与连通性测试统一走本对象，调用方只需关心 baseUrl/apiKey/model 等业务字段，
 * 不需要判断配置来源。数据库存在启用且已填 Key 的默认配置时优先使用，
 * 否则由 {@link DeepSeekAiChatService} 回退到 yml 内置参数。</p>
 *
 * @author 智客通开发组
 */
@Data
public class AiRuntimeConfig {

    /** 数据库配置 ID；来自 yml 时为 null */
    private Long configId;

    /** 配置展示名称，用于日志与调用记录 */
    private String name;

    /** 平台标识：deepseek/openai/qwen/... */
    private String provider;

    /** OpenAI 兼容接口根地址（不含 /chat/completions） */
    private String baseUrl;

    /** API Key */
    private String apiKey;

    /** 模型名称 */
    private String model;

    /** 采样温度 */
    private double temperature;

    /** 单次最大输出 token */
    private int maxTokens;

    /** 请求超时（毫秒） */
    private int timeoutMs;

    /** 附加请求头 JSON，为空表示无额外头 */
    private String extraHeaders;

    /** 是否来自管理端数据库配置 */
    private boolean fromDatabase;

    /** 该平台是否允许不填 API Key（本地推理 / 内网自建服务） */
    private boolean keyOptional;

    /**
     * 由 application.yml 的 zhiketong.ai 构建。
     */
    public static AiRuntimeConfig of(AiProperties properties) {
        AiRuntimeConfig config = new AiRuntimeConfig();
        config.setName("内置配置");
        config.setProvider(properties.getProvider());
        config.setBaseUrl(properties.getBaseUrl());
        config.setApiKey(properties.getApiKey());
        config.setModel(properties.getModel());
        config.setTemperature(properties.getTemperature());
        config.setMaxTokens(properties.getMaxTokens());
        config.setTimeoutMs(properties.getTimeoutMs());
        config.setFromDatabase(false);
        config.setKeyOptional(!StringUtils.hasText(properties.getApiKey())
                && isLocalEndpoint(properties.getBaseUrl()));
        return config;
    }

    /**
     * 判断地址是否指向本机（yml 内置配置回退时使用）。
     */
    private static boolean isLocalEndpoint(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return false;
        }
        String url = baseUrl.trim().toLowerCase();
        return url.contains("//127.0.0.1") || url.contains("//localhost") || url.contains("//0.0.0.0");
    }
}
