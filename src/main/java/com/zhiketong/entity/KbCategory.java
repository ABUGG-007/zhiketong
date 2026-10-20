package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库分类实体，对应表 kb_category。
 * <p>
 * parentId 为 0 表示顶级分类，父子通过 parentId 进行逻辑关联（无物理外键）。
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("kb_category")
public class KbCategory extends BaseEntity {

    private static final long serialVersionUID = 1L;

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

    /** 逻辑删除：0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;
}
