package com.zhiketong.dto.embed;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 保存接入配置请求：外观、来源白名单与限流阈值。
 */
@Data
@Schema(description = "保存接入配置")
public class EmbedConfigUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "客服名称不能为空")
    @Size(max = 64, message = "客服名称不能超过 64 字")
    @Schema(description = "客服名称", example = "智客通客服")
    private String brandName;

    @Size(max = 120, message = "欢迎语不能超过 120 字")
    @Schema(description = "欢迎语")
    private String welcome;

    @NotBlank(message = "主题色不能为空")
    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "主题色必须是 #RRGGBB 格式")
    @Schema(description = "主题色", example = "#4f46e5")
    private String themeColor;

    @NotBlank(message = "悬浮位置不能为空")
    @Pattern(regexp = "^(left|right)$", message = "悬浮位置只能是 left 或 right")
    @Schema(description = "悬浮位置", example = "right")
    private String position;

    @Schema(description = "是否显示未读红点")
    private Boolean badge = Boolean.TRUE;

    @Size(max = 500, message = "来源白名单不能超过 500 字")
    @Schema(description = "允许的嵌入来源，英文逗号分隔；为空不限制",
            example = "https://www.example.com,https://*.example.com")
    private String allowedOrigins;

    @Min(value = 1, message = "限流阈值不能小于 1")
    @Max(value = 600, message = "限流阈值不能超过 600")
    @Schema(description = "单 IP 每分钟提问上限", example = "30")
    private Integer rateLimitPerMinute = 30;

    @Min(value = 0, message = "状态只能为 0 或 1")
    @Max(value = 1, message = "状态只能为 0 或 1")
    @Schema(description = "接入状态：1 启用 0 停用", example = "1")
    private Integer status = 1;
}
