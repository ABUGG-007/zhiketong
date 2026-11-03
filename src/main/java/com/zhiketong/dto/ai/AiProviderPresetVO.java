package com.zhiketong.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 平台预设：管理端「新增配置」时一键带出接口地址、常用模型与申请入口。
 *
 * <p>预设只用于减少手填成本，保存时仍以表单最终值为准；
 * 因此新平台上线无需改后端代码，选择「自定义（OpenAI 兼容）」手填即可。</p>
 */
@Data
@Schema(description = "大模型平台预设")
public class AiProviderPresetVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "平台标识")
    private String provider;

    @Schema(description = "平台名称")
    private String label;

    @Schema(description = "归类：国内平台 / 国际平台 / 中转与自建")
    private String category;

    @Schema(description = "OpenAI 兼容接口地址")
    private String baseUrl;

    @Schema(description = "推荐默认模型")
    private String defaultModel;

    @Schema(description = "常用模型列表")
    private List<String> models;

    @Schema(description = "API Key 申请地址")
    private String keyUrl;

    @Schema(description = "接入说明")
    private String description;

    @Schema(description = "建议附加请求头 JSON，可为空")
    private String extraHeaders;

    public AiProviderPresetVO() {
    }

    public AiProviderPresetVO(String provider, String label, String category, String baseUrl,
                              String defaultModel, List<String> models, String keyUrl,
                              String description, String extraHeaders) {
        this.provider = provider;
        this.label = label;
        this.category = category;
        this.baseUrl = baseUrl;
        this.defaultModel = defaultModel;
        this.models = models;
        this.keyUrl = keyUrl;
        this.description = description;
        this.extraHeaders = extraHeaders;
    }
}
