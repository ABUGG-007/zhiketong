package com.zhiketong.dto.kb;

import lombok.Data;

import java.io.Serializable;

/**
 * 知识条目命中排行项（R-30）。
 *
 * @author 智客通开发组
 */
@Data
public class EntryRankingVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 排名，从 1 开始 */
    private Integer rank;

    /** 条目 ID */
    private Long id;

    /** 标准问题 */
    private String question;

    /** 所属分类名称 */
    private String categoryName;

    /** 累计命中次数 */
    private Integer hitCount;
}
