package com.zhiketong.dto.kb;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

/**
 * 新增知识条目请求（R-25）。
 *
 * @author 智客通开发组
 */
@Data
public class EntryCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 所属分类 ID：必须存在并启用 */
    @NotNull(message = "所属分类不能为空")
    @Min(value = 1, message = "所属分类 ID 不合法")
    private Long categoryId;

    /** 标准问题：2-255 个字符 */
    @NotBlank(message = "标准问题不能为空")
    @Size(min = 2, max = 255, message = "标准问题长度须为 2-255 个字符")
    private String question;

    /** 标准答案：2-2000 个字符 */
    @NotBlank(message = "标准答案不能为空")
    @Size(min = 2, max = 2000, message = "标准答案长度须为 2-2000 个字符")
    private String answer;

    /** 关键词：英文逗号分隔，2-10 个 */
    @NotBlank(message = "关键词不能为空")
    @Size(max = 255, message = "关键词总长度不能超过 255")
    private String keywords;

    /** 标签：英文逗号分隔，可为空 */
    @Size(max = 255, message = "标签总长度不能超过 255")
    private String tags;

    /** 相似问法：创建时一并登记（可选，最多 20 条） */
    @Size(max = 20, message = "单次最多登记 20 条相似问法")
    private List<String> similarQuestions;

    /** 状态：1 启用 0 停用，默认 1 */
    @Min(value = 0, message = "状态只能为 0 或 1")
    @Max(value = 1, message = "状态只能为 0 或 1")
    private Integer status;
}
