package com.zhiketong.dto.kb;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 相似问法视图对象（R-73）。
 *
 * @author 智客通开发组
 */
@Data
public class SimilarQuestionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 相似问法 ID */
    private Long id;

    /** 所属知识条目 ID */
    private Long entryId;

    /** 相似问法内容 */
    private String question;

    /** 来源：MANUAL / AI / IMPORT */
    private String source;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
