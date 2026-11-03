package com.zhiketong.dto.embed;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 悬浮球提问请求。租户身份由接入令牌或签名请求头确定，请求体不再传 tenant。
 */
@Data
@Schema(description = "悬浮球提问请求")
public class EmbedAnswerRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "问题不能为空")
    @Size(max = 300, message = "问题长度不能超过 300 字")
    @Schema(description = "访客问题", example = "发票可以开专票吗")
    private String content;
}
