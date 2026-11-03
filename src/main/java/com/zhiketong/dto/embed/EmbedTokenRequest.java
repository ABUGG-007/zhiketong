package com.zhiketong.dto.embed;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 浏览器接入令牌申请请求：商家页面持有 appKey，服务端校验来源后签发短时令牌。
 */
@Data
@Schema(description = "接入令牌申请")
public class EmbedTokenRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "appKey 不能为空")
    @Size(max = 40, message = "appKey 长度不能超过 40")
    @Schema(description = "接入 Key", example = "zk_demo_5f2a1c9e")
    private String appKey;
}
