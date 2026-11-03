package com.zhiketong.dto.embed;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 悬浮球回答结果：与站内会话共用同一套应答编排（直答 / 参考生成 / 自由生成 / 兜底）。
 */
@Data
@Schema(description = "悬浮球回答结果")
public class EmbedAnswerResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "回复内容")
    private String answer;

    @Schema(description = "是否命中知识库（达到直答阈值）")
    private boolean matched;

    @Schema(description = "匹配得分，0-1")
    private double score;

    @Schema(description = "是否建议转人工")
    private boolean suggestHuman;

    @Schema(description = "命中的标准问题")
    private String sourceQuestion;

    @Schema(description = "应答来源：kb-direct 知识库直答 / ai-reference 参考知识大模型生成 / "
            + "ai-free 大模型自由生成 / kb-suggest 知识库推荐 / fallback 兜底话术 / "
            + "chitchat 闲聊 / invalid 无效输入")
    private String answerSource;

    @Schema(description = "应答分类：DIRECT / REFERENCE / FREE / SUGGEST / CHITCHAT / INVALID / FALLBACK")
    private String answerCategory;
}
