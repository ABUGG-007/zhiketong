package com.zhiketong.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理端大模型配置展示对象。
 *
 * <p>api_key 不明文返回，只下发脱敏串与「是否已配置」标记，
 * 避免管理员账号被劫持后直接拿到各平台密钥。</p>
 */
@Data
@Schema(description = "大模型配置")
public class AiModelConfigVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "配置 ID")
    private Long id;

    @Schema(description = "配置名称")
    private String name;

    @Schema(description = "平台标识")
    private String provider;

    @Schema(description = "平台名称")
    private String providerLabel;

    @Schema(description = "OpenAI 兼容接口地址")
    private String baseUrl;

    @Schema(description = "脱敏后的 API Key")
    private String apiKeyMasked;

    @Schema(description = "是否已配置 API Key")
    private boolean apiKeyConfigured;

    @Schema(description = "模型名称")
    private String model;

    @Schema(description = "采样温度")
    private BigDecimal temperature;

    @Schema(description = "单次最大输出 token")
    private Integer maxTokens;

    @Schema(description = "请求超时（毫秒）")
    private Integer timeoutMs;

    @Schema(description = "附加请求头 JSON")
    private String extraHeaders;

    @Schema(description = "启用状态：1 启用 0 停用")
    private Integer enabled;

    @Schema(description = "是否默认模型：1 是 0 否")
    private Integer isDefault;

    @Schema(description = "最近测试时间")
    private LocalDateTime lastTestTime;

    @Schema(description = "最近测试结果：1 成功 0 失败")
    private Integer lastTestOk;

    @Schema(description = "最近测试耗时（毫秒）")
    private Integer lastTestLatencyMs;

    @Schema(description = "最近测试结果说明")
    private String lastTestMessage;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
