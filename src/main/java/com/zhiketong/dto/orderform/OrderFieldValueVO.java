package com.zhiketong.dto.orderform;

import lombok.Data;

import java.io.Serializable;

/**
 * 工单自定义字段值视图对象（工单详情展示）。
 *
 * <p>名称与类型取自字段值表的快照，字段定义后续变更不影响历史工单展示。</p>
 *
 * @author 智客通开发组
 */
@Data
public class OrderFieldValueVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 字段定义 ID */
    private Long fieldId;

    /** 字段编码快照 */
    private String fieldCode;

    /** 字段名称快照 */
    private String fieldName;

    /** 字段类型快照 */
    private String fieldType;

    /** 字段值 */
    private String value;

    /** 展示顺序 */
    private Integer sort;
}
