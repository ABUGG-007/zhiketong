package com.zhiketong.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 大模型配置新增 / 编辑请求。
 *
 * <p>编辑时 apiKey 留空表示保持原密钥不变，避免前端只回填脱敏串导致密钥被覆盖。
 * 新增时由 Service 强制要求 apiKey，因为列表接口不会返回明文。</p>
 */
@Data
@Schema(description = "大模型配置保存请求")
public class AiModelConfigSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "配置名称", example = "主力-DeepSeek")
    @NotBlank(message = "配置名称不能为空")
    @Size(max = 64, message = "配置名称不能超过 64 个字符")
    private String name;

    @Schema(description = "平台标识", example = "deepseek")
    @NotBlank(message = "请选择平台")
    @Size(max = 32, message = "平台标识不能超过 32 个字符")
    private String provider;

    @Schema(description = "OpenAI 兼容接口地址", example = "https://api.deepseek.com/v1")
    @NotBlank(message = "接口地址不能为空")
    @Size(max = 255, message = "接口地址不能超过 255 个字符")
    private String baseUrl;

    @Schema(description = "API Key，编辑时留空表示不修改")
    @Size(max = 255, message = "API Key 不能超过 255 个字符")
    private String apiKey;

    @Schema(description = "模型名称", example = "deepseek-chat")
    @NotBlank(message = "模型名称不能为空")
    @Size(max = 128, message = "模型名称不能超过 128 个字符")
    private String model;

    @Schema(description = "采样温度 0.00-2.00")
    @DecimalMin(value = "0.0", message = "温度不能小于 0")
    @DecimalMax(value = "2.0", message = "温度不能大于 2")
    private BigDecimal temperature;

    @Schema(description = "单次最大输出 token")
    @Min(value = 1, message = "最大输出 token 不能小于 1")
    @Max(value = 32768, message = "最大输出 token 不能大于 32768")
    private Integer maxTokens;

    @Schema(description = "请求超时（毫秒）")
    @Min(value = 1000, message = "超时时间不能小于 1000 毫秒")
    @Max(value = 120000, message = "超时时间不能大于 120000 毫秒")
    private Integer timeoutMs;

    @Schema(description = "附加请求头 JSON，可为空")
    @Size(max = 500, message = "附加请求头不能超过 500 个字符")
    private String extraHeaders;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "是否设为默认模型")
    private Boolean isDefault;

    @Schema(description = "备注")
    @Size(max = 255, message = "备注不能超过 255 个字符")
    private String remark;
}
