package com.zhiketong.dto.voc;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户之声词云（R-214）。
 *
 * <p>词条来自未命中问法的中文 n-gram 词频统计，权重为「包含该词的问法累计提问次数」，
 * 因此词云反映的是「访客真实在问什么」，而不是单纯的字符串出现次数。
 * 可用 {@code topicId} 只看某个主题下的用词。</p>
 *
 * @author 智客通开发组
 */
@Data
public class VocWordCloudVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 统计范围的问法条数 */
    private int questionCount;

    /** 权重合计（词汇量） */
    private int totalWeight;

    /** 词条列表，按权重倒序 */
    private List<Word> words = new ArrayList<>();

    /** 词条。 */
    @Data
    public static class Word implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 词条文本 */
        private String term;

        /** 权重 = 包含该词的问法累计提问次数 */
        private long weight;

        /** 包含该词的问法条数 */
        private int questionCount;
    }
}
