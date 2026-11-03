package com.zhiketong.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 网页接入配置项（application.yml 中 zhiketong.embed）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "zhiketong.embed")
public class EmbedProperties {

    /** 控制台维护的默认租户标识，对应 tenant_embed_config.tenant_code */
    private String tenantCode = "demo";

    /** 浏览器接入令牌有效期（分钟） */
    private long tokenTtlMinutes = 120;

    /** 服务端签名允许的时间偏差（秒） */
    private long timestampToleranceSeconds = 300;

    /** 新建租户时的默认单 IP 每分钟提问上限 */
    private int defaultRateLimitPerMinute = 30;
}
