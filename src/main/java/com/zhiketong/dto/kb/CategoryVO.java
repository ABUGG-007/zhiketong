package com.zhiketong.dto.kb;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 知识分类视图对象（R-18 / R-19 / R-20）。
 *
 * @author 智客通开发组
 */
@Data
public class CategoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 分类 ID */
    private Long id;

    /** 分类名称 */
    private String name;

    /** 父分类 ID，0 为顶级 */
    private Long parentId;

    /** 排序号 */
    private Integer sort;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 分类说明 */
    private String description;

    /** 该分类下条目数 */
    private Long entryCount;

    /** 子分类，仅树查询返回 */
    private List<CategoryVO> children;
}
