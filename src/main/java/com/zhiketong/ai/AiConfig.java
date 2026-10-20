package com.zhiketong.ai;

import com.zhiketong.config.properties.AiProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * AI 模块配置：带超时控制的 RestTemplate。
 */
@Configuration
public class AiConfig {

    @Bean
    public RestTemplate aiRestTemplate(RestTemplateBuilder builder, AiProperties aiProperties) {
        Duration timeout = Duration.ofMillis(aiProperties.getTimeoutMs());
        return builder
                .setConnectTimeout(timeout)
                .setReadTimeout(timeout)
                .build();
    }
}
