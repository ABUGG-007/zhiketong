package com.zhiketong.dto.kb;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 编辑知识分类请求（R-20），字段均可选。
 *
 * @author 智客通开发组
 */
@Data
public class CategoryUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 分类名称：2-50 个字符，同一父级下唯一 */
    @Size(min = 2, max = 50, message = "分类名称长度须为 2-50 个字符")
    private String name;

    /** 父分类 ID：0 或一级分类 */
    @Min(value = 0, message = "父分类 ID 不能小于 0")
    private Long parentId;

    /** 排序号 */
    @Min(value = 0, message = "排序号不能小于 0")
    private Integer sort;

    /** 状态：1 启用 0 停用 */
    @Min(value = 0, message = "状态只能为 0 或 1")
    @Max(value = 1, message = "状态只能为 0 或 1")
    private Integer status;

    /** 分类说明 */
    @Size(max = 255, message = "分类描述长度不能超过 255")
    private String description;
}
