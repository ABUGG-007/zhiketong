package com.zhiketong.dto.kb;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 未命中问题转知识词条请求（R-82）。
 *
 * @author 智客通开发组
 */
@Data
public class UnmatchedConvertRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标分类 ID */
    @NotNull(message = "所属分类不能为空")
    @Min(value = 1, message = "所属分类 ID 不合法")
    private Long categoryId;

    /** 标准问题：留空时使用未命中问题的原始问法 */
    @Size(max = 255, message = "标准问题长度不能超过 255")
    private String question;

    /** 标准答案 */
    @NotBlank(message = "标准答案不能为空")
    @Size(min = 2, max = 2000, message = "标准答案长度须为 2-2000 个字符")
    private String answer;

    /** 关键词，英文逗号分隔 */
    @Size(max = 255, message = "关键词总长度不能超过 255")
    private String keywords;

    /** 标签，英文逗号分隔 */
    @Size(max = 255, message = "标签总长度不能超过 255")
    private String tags;

    /** 是否把原问法同时登记为相似问法，默认 true */
    private Boolean keepAsSimilar;
}
