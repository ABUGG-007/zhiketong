package com.zhiketong.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置项（application.yml 中 zhiketong.jwt）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "zhiketong.jwt")
public class JwtProperties {

    /** 签名密钥，HS256 要求长度不小于 32 字节；仅作为开发兜底值，生产必须通过 JWT_SECRET 注入 */
    private String secret = "zhiketong-saas-dev-only-secret-key-override-in-prod";

    /** 过期时间（小时） */
    private long expireHours = 2;

    /** 请求头名称 */
    private String header = "Authorization";

    /** Token 前缀 */
    private String prefix = "Bearer ";
}
