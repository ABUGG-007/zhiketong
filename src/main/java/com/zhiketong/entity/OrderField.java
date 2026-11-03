package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工单自定义字段定义实体，对应表 order_field（S3-A）。
 *
 * <p>字段由管理员统一维护，工单模板通过 field_ids 绑定字段集合；
 * 停用字段后不再出现在新建工单表单，历史工单仍按字段值快照展示。</p>
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("order_field")
public class OrderField extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID，由多租户拦截器在写入时按当前上下文填充 */
    private Long tenantId;


    /** 字段编码：英文小写与下划线，提交字段值时使用 */
    private String fieldCode;

    /** 字段名称（表单标签） */
    private String fieldName;

    /** 字段类型：TEXT/MULTILINE/SELECT/NUMBER/DATE */
    private String fieldType;

    /** 下拉选项，英文逗号分隔；仅 SELECT 使用 */
    private String options;

    /** 是否必填：1 是 0 否 */
    private Integer required;

    /** 默认值 */
    private String defaultValue;

    /** 输入提示 */
    private String placeholder;

    /** 字段说明 */
    private String description;

    /** 排序号，越小越靠前 */
    private Integer sort;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 创建人 ID */
    private Long createBy;

    /** 更新人 ID */
    private Long updateBy;

    /** 逻辑删除：0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;
}
