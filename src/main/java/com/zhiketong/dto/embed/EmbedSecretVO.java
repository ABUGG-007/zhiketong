package com.zhiketong.dto.embed;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 接入凭证：控制台展示 appKey 与签名密钥，密钥仅 ADMIN 可见，用于服务端签名对接。
 */
@Data
@Schema(description = "接入凭证")
public class EmbedSecretVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "接入 Key")
    private String appKey;

    @Schema(description = "签名密钥，轮换后旧密钥立即失效")
    private String appSecret;
}
