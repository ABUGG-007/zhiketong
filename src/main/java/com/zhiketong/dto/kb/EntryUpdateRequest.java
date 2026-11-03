package com.zhiketong.dto.kb;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

/**
 * 编辑知识条目请求（R-26），字段均可选。
 *
 * @author 智客通开发组
 */
@Data
public class EntryUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 所属分类 ID */
    @Min(value = 1, message = "所属分类 ID 不合法")
    private Long categoryId;

    /** 标准问题：2-255 个字符 */
    @Size(min = 2, max = 255, message = "标准问题长度须为 2-255 个字符")
    private String question;

    /** 标准答案：2-2000 个字符 */
    @Size(min = 2, max = 2000, message = "标准答案长度须为 2-2000 个字符")
    private String answer;

    /** 关键词：英文逗号分隔，2-10 个 */
    @Size(max = 255, message = "关键词总长度不能超过 255")
    private String keywords;

    /** 标签：英文逗号分隔，可为空 */
    @Size(max = 255, message = "标签总长度不能超过 255")
    private String tags;

    /** 相似问法全量替换（可选，最多 20 条；不传则不修改） */
    @Size(max = 20, message = "单次最多登记 20 条相似问法")
    private List<String> similarQuestions;

    /** 状态：1 启用 0 停用 */
    @Min(value = 0, message = "状态只能为 0 或 1")
    @Max(value = 1, message = "状态只能为 0 或 1")
    private Integer status;
}
