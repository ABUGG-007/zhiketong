package com.zhiketong.dto.kb;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识条目匹配预览结果（R-29）。
 *
 * @author 智客通开发组
 */
@Data
public class EntrySearchResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 待匹配的用户问句 */
    private String query;

    /** 候选条目，按匹配得分降序 */
    private List<Candidate> candidates = new ArrayList<>();

    /**
     * 匹配候选条目。
     */
    @Data
    public static class Candidate implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 条目 ID */
        private Long id;

        /** 标准问题 */
        private String question;

        /** 标准答案 */
        private String answer;

        /** 所属分类名称 */
        private String categoryName;

        /** 匹配得分，保留 4 位小数 */
        private BigDecimal score;

        /** 命中的关键词 */
        private List<String> hitKeywords = new ArrayList<>();

        /** 是否达到直接回答阈值 */
        private boolean directAnswer;
    }
}
