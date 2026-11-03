package com.zhiketong.dto.embed;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 网页悬浮球初始化配置（公开接口，不含任何敏感信息）。
 */
@Data
@Schema(description = "网页接入配置")
public class EmbedConfigVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "租户接入标识")
    private String tenantCode;

    @Schema(description = "客服名称")
    private String brandName = "智客通客服";

    @Schema(description = "欢迎语")
    private String welcome;

    @Schema(description = "主题色 #RRGGBB")
    private String themeColor = "#4f46e5";

    @Schema(description = "悬浮位置：right / left")
    private String position = "right";

    @Schema(description = "是否显示未读红点")
    private boolean badge = true;

    @Schema(description = "常见问题，用于快捷提问")
    private List<String> quickQuestions = new ArrayList<>();

    @Schema(description = "是否支持转人工")
    private boolean humanTransfer = true;
}
