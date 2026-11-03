package com.zhiketong.dto.kb;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 新增知识分类请求（R-19）。
 *
 * @author 智客通开发组
 */
@Data
public class CategoryCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 分类名称：2-50 个字符，同一父级下唯一 */
    @NotBlank(message = "分类名称不能为空")
    @Size(min = 2, max = 50, message = "分类名称长度须为 2-50 个字符")
    private String name;

    /** 父分类 ID：默认 0，必须是一级分类 */
    @Min(value = 0, message = "父分类 ID 不能小于 0")
    private Long parentId;

    /** 排序号：默认 99 */
    @Min(value = 0, message = "排序号不能小于 0")
    private Integer sort;

    /** 状态：1 启用 0 停用，默认 1 */
    @Min(value = 0, message = "状态只能为 0 或 1")
    @Max(value = 1, message = "状态只能为 0 或 1")
    private Integer status;

    /** 分类说明 */
    @Size(max = 255, message = "分类描述长度不能超过 255")
    private String description;
}
