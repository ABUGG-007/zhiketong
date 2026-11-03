package com.zhiketong.dto.kb;

import lombok.Data;

import java.io.Serializable;

/**
 * 知识查重与冲突检测结果（R-84）。
 *
 * @author 智客通开发组
 */
@Data
public class KbDuplicateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 词条 A ID */
    private Long entryAId;

    /** 词条 A 标准问题 */
    private String entryAQuestion;

    /** 词条 B ID */
    private Long entryBId;

    /** 词条 B 标准问题 */
    private String entryBQuestion;

    /** 分类名称 */
    private String categoryName;

    /** 相似度 0-1 */
    private double similarity;

    /** 是否答案冲突（同义问法、答案不一致） */
    private boolean conflict;

    /** 判定说明 */
    private String reason;
}
