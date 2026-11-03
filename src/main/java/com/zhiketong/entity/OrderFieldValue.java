package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工单自定义字段值实体，对应表 order_field_value（S3-A）。
 *
 * <p>写入时同时保存字段名称与类型快照：字段定义后续改名、停用或删除，
 * 历史工单仍能按当时的名称与类型正确展示。</p>
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("order_field_value")
public class OrderFieldValue extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID，由多租户拦截器在写入时按当前上下文填充 */
    private Long tenantId;


    /** 工单 ID */
    private Long orderId;

    /** 字段定义 ID */
    private Long fieldId;

    /** 字段编码快照 */
    private String fieldCode;

    /** 字段名称快照 */
    private String fieldName;

    /** 字段类型快照 */
    private String fieldType;

    /** 字段值 */
    private String fieldValue;

    /** 展示顺序快照 */
    private Integer sort;

    /** 逻辑删除：0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;
}
