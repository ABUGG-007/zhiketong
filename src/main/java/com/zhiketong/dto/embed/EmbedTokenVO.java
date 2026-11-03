package com.zhiketong.dto.embed;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 浏览器接入令牌：令牌用于 config / answer 接口鉴权，外观配置随令牌一并下发。
 */
@Data
@Schema(description = "接入令牌")
public class EmbedTokenVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "短时令牌，放在 X-ZKT-Embed-Token 请求头")
    private String token;

    @Schema(description = "有效期（秒）")
    private long expiresIn;

    @Schema(description = "悬浮球外观与快捷问题")
    private EmbedConfigVO config;
}
