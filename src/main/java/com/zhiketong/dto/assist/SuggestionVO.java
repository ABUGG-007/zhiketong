package com.zhiketong.dto.assist;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 输入联想（R-111 访客端 / R-112 坐席端）：按当前输入召回知识库问法。
 *
 * @author 智客通开发组
 */
@Data
public class SuggestionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 联想场景：VISITOR 站内访客 / AGENT 坐席 */
    private String scope;

    /** 归一化后的输入内容 */
    private String query;

    /** 联想候选，按得分降序 */
    private List<Item> items = new ArrayList<>();

    /** 使用提示 */
    private String note;

    /**
     * 单条联想候选：只返回问法与可信度，坐席侧额外附带答案摘要便于直接引用。
     */
    @Data
    public static class Item implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 知识条目 ID */
        private Long kbId;

        /** 标准问题（展示用） */
        private String question;

        /** 实际命中的问法（标准问题或相似问法） */
        private String matchedQuestion;

        /** 匹配类型：STANDARD 标准问题 / SIMILAR 相似问法 / KEYWORD 关键词 */
        private String matchedType;

        /** 匹配得分 0-1 */
        private Double score;

        /** 分类名称 */
        private String categoryName;

        /** 答案摘要：仅坐席侧返回 */
        private String answer;
    }
}
