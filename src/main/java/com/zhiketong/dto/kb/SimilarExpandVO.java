package com.zhiketong.dto.kb;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * AI 扩写相似问法结果（R-76）。
 * <p>
 * 扩写结果仅作为候选返回，必须由运营人员确认后再调用 R-74 入库，
 * 避免大模型幻觉直接污染知识库。
 *
 * @author 智客通开发组
 */
@Data
public class SimilarExpandVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 词条 ID */
    private Long entryId;

    /** 标准问题 */
    private String question;

    /** 扩写候选（已去重，且排除已有相似问法） */
    private List<String> candidates;

    /** 提示信息，如大模型不可用时的说明 */
    private String note;
}
