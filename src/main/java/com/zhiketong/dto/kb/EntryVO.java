package com.zhiketong.dto.kb;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识条目视图对象（R-23 / R-24 / R-25 / R-26）。
 *
 * @author 智客通开发组
 */
@Data
public class EntryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 条目 ID */
    private Long id;

    /** 所属分类 ID */
    private Long categoryId;

    /** 所属分类名称 */
    private String categoryName;

    /** 标准问题 */
    private String question;

    /** 标准答案 */
    private String answer;

    /** 关键词，英文逗号分隔 */
    private String keywords;

    /** 标签，英文逗号分隔 */
    private String tags;

    /** 相似问法数量（列表页展示，detail 时为空） */
    private Integer similarCount;

    /** 相似问法列表（仅 detail 接口返回） */
    private List<SimilarQuestionVO> similarQuestions = new ArrayList<>();

    /** 累计命中次数 */
    private Integer hitCount;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 创建人 ID */
    private Long createBy;

    /** 创建人名称 */
    private String createByName;

    /** 更新人 ID */
    private Long updateBy;

    /** 更新人名称 */
    private String updateByName;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
