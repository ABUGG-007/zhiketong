package com.zhiketong.dto.embed;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理端接入配置：包含公开配置、来源白名单、限流阈值与签名密钥。
 *
 * <p>仅 ADMIN 可访问；appSecret 用于服务端签名对接，不能出现在商家页面。</p>
 */
@Data
@Schema(description = "管理端接入配置")
public class AdminEmbedConfigVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "租户接入标识")
    private String tenantCode;

    @Schema(description = "租户名称")
    private String tenantName;

    @Schema(description = "接入 Key")
    private String appKey;

    @Schema(description = "签名密钥")
    private String appSecret;

    @Schema(description = "客服名称")
    private String brandName;

    @Schema(description = "欢迎语")
    private String welcome;

    @Schema(description = "主题色")
    private String themeColor;

    @Schema(description = "悬浮位置")
    private String position;

    @Schema(description = "未读红点")
    private boolean badge;

    @Schema(description = "允许的嵌入来源，逗号分隔")
    private String allowedOrigins;

    @Schema(description = "单 IP 每分钟提问上限")
    private Integer rateLimitPerMinute;

    @Schema(description = "接入状态：1 启用 0 停用")
    private Integer status;

    @Schema(description = "密钥最近轮换时间")
    private LocalDateTime secretRotateTime;

    @Schema(description = "最近更新时间")
    private LocalDateTime updateTime;
}
