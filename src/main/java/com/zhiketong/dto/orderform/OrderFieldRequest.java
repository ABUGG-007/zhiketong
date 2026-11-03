package com.zhiketong.dto.orderform;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

/**
 * 工单自定义字段新增/修改请求（R-116 / R-117）。
 *
 * <p>{@code fieldCode} 创建后不可修改：字段值表以编码与名称快照持久化，
 * 允许改码会导致历史工单与新建工单语义错位，服务端在修改时忽略该字段。</p>
 *
 * @author 智客通开发组
 */
@Data
public class OrderFieldRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 字段编码：小写字母开头，可含数字与下划线，2-32 位 */
    @Pattern(regexp = "^[a-z][a-z0-9_]{1,31}$",
            message = "字段编码须以小写字母开头，仅含小写字母、数字与下划线，长度 2-32 位")
    private String fieldCode;

    /** 字段名称（表单标签） */
    @NotBlank(message = "字段名称不能为空")
    @Size(min = 2, max = 64, message = "字段名称长度须为 2-64 个字符")
    private String fieldName;

    /** 字段类型：TEXT/MULTILINE/SELECT/NUMBER/DATE */
    @NotBlank(message = "字段类型不能为空")
    @Pattern(regexp = "TEXT|MULTILINE|SELECT|NUMBER|DATE",
            message = "字段类型只能为 TEXT/MULTILINE/SELECT/NUMBER/DATE")
    private String fieldType;

    /** 下拉选项：仅 SELECT 类型使用，最多 20 项 */
    @Valid
    @Size(max = 20, message = "下拉选项最多 20 项")
    private List<@Size(max = 32, message = "单个选项不能超过 32 个字符") String> options;

    /** 是否必填：1 是 0 否，默认 0 */
    @Min(value = 0, message = "必填标记只能为 0 或 1")
    @Max(value = 1, message = "必填标记只能为 0 或 1")
    private Integer required;

    /** 默认值 */
    @Size(max = 255, message = "默认值不能超过 255 个字符")
    private String defaultValue;

    /** 输入提示 */
    @Size(max = 100, message = "输入提示不能超过 100 个字符")
    private String placeholder;

    /** 字段说明 */
    @Size(max = 255, message = "字段说明不能超过 255 个字符")
    private String description;

    /** 排序号，越小越靠前 */
    private Integer sort;

    /** 状态：1 启用 0 停用，默认 1 */
    @Min(value = 0, message = "状态只能为 0 或 1")
    @Max(value = 1, message = "状态只能为 0 或 1")
    private Integer status;
}
