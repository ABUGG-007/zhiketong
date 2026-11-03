package com.zhiketong.dto.stats;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识库命中统计（R-67）。
 *
 * @author 智客通开发组
 */
@Data
public class KbHitStatVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 累计命中次数（明细见 {@link TopEntry#getHitCount()}） */
    private long totalHits;

    /** 直接命中条目的消息数 */
    private long directAnswerCount;

    /** 直接命中率 */
    private BigDecimal directAnswerRate;

    /** 命中排行 TopN */
    private List<TopEntry> topEntries = new ArrayList<>();

    /**
     * 命中排行条目。
     */
    @Data
    public static class TopEntry implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 条目 ID */
        private Long entryId;

        /** 标准问题 */
        private String question;

        /** 所属分类名称 */
        private String categoryName;

        /** 累计命中次数 */
        private Integer hitCount;

        /** 直接回答次数 */
        private long directAnswerCount;
    }
}
