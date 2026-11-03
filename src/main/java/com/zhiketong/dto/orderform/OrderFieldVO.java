package com.zhiketong.dto.orderform;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 工单自定义字段视图对象（R-115 ~ R-119）。
 *
 * @author 智客通开发组
 */
@Data
public class OrderFieldVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 字段 ID */
    private Long id;

    /** 字段编码 */
    private String fieldCode;

    /** 字段名称 */
    private String fieldName;

    /** 字段类型编码 */
    private String fieldType;

    /** 字段类型名称 */
    private String fieldTypeLabel;

    /** 下拉选项（已按逗号拆分） */
    private List<String> options = new ArrayList<>();

    /** 是否必填：1 是 0 否 */
    private Integer required;

    /** 默认值 */
    private String defaultValue;

    /** 输入提示 */
    private String placeholder;

    /** 字段说明 */
    private String description;

    /** 排序号 */
    private Integer sort;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
