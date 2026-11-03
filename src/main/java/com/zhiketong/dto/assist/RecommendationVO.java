package com.zhiketong.dto.assist;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 坐席实时推荐答案（R-104）。
 * <p>
 * 推荐结果只作为草稿返回，坐席确认后才进入输入框并由坐席发送，禁止自动对客发送。
 *
 * @author 智客通开发组
 */
@Data
public class RecommendationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话 ID */
    private Long sessionId;

    /** 当前诉求：访客最近一条消息 */
    private String query;

    /** 推荐来源：KB 知识库命中 / AI 大模型起草 / NONE 无可用建议 */
    private String answerSource;

    /** 知识库候选，按得分降序，最多 5 条 */
    private List<Item> items = new ArrayList<>();

    /** AI 起草的回复草稿，answerSource=AI 时有值 */
    private String aiDraft;

    /** 草稿使用的模型 */
    private String model;

    /** 是否已达到知识库直答阈值 */
    private Boolean directHit;

    /** 使用提示：AI 建议仅为草稿，须坐席确认后才对客生效 */
    private String note;

    /** 生成时间 */
    private LocalDateTime generatedAt;

    /**
     * 知识库候选条目。
     */
    @Data
    public static class Item implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 知识条目 ID */
        private Long kbId;

        /** 标准问题 */
        private String question;

        /** 实际命中的问法 */
        private String matchedQuestion;

        /** 命中类型：STANDARD / SIMILAR */
        private String matchedType;

        /** 标准答案 */
        private String answer;

        /** 匹配得分，0-1 */
        private Double score;

        /** 命中的关键词 */
        private List<String> hitKeywords = new ArrayList<>();

        /** 所属分类名称 */
        private String categoryName;

        /** 引用来源描述 */
        private String answerSource;
    }
}
