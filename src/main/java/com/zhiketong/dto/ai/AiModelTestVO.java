package com.zhiketong.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 大模型连通性测试结果。
 *
 * <p>测试会真实发起一次极短的对话请求（max_tokens 限制在 32 以内），
 * 用于确认接口地址、API Key、模型名三者是否匹配；失败原因直接取平台返回的错误信息。</p>
 */
@Data
@Schema(description = "大模型连通性测试结果")
public class AiModelTestVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "是否连通成功")
    private boolean success;

    @Schema(description = "耗时（毫秒）")
    private long latencyMs;

    @Schema(description = "实际使用的模型")
    private String model;

    @Schema(description = "结果说明")
    private String message;

    @Schema(description = "模型返回的示例内容")
    private String sample;
}
