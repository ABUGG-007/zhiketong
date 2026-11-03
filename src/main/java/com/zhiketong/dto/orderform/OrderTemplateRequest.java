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
 * 工单模板新增/修改请求（R-122 / R-123）。
 *
 * <p>模板把常用字段组合成一套表单：坐席按模板建单时只需填写模板绑定的字段。
 * 模板可预置问题分类与优先级，创建工单时请求体未指定则取模板默认值。</p>
 *
 * @author 智客通开发组
 */
@Data
public class OrderTemplateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 模板名称 */
    @NotBlank(message = "模板名称不能为空")
    @Size(min = 2, max = 64, message = "模板名称长度须为 2-64 个字符")
    private String templateName;

    /** 默认问题分类：为空时由服务端按通用分类补齐 */
    @Size(max = 32, message = "问题分类不能超过 32 个字符")
    private String category;

    /** 默认优先级：P0/P1/P2/P3，为空时按 P2 */
    @Pattern(regexp = "P0|P1|P2|P3", message = "优先级只能为 P0/P1/P2/P3")
    private String defaultPriority;

    /** 默认受理技能组 ID */
    @Min(value = 1, message = "技能组 ID 不合法")
    private Long skillGroupId;

    /** 模板字段 ID 列表，按顺序渲染，最多 30 个 */
    @Valid
    @Size(max = 30, message = "单个模板最多绑定 30 个字段")
    private List<@Min(value = 1, message = "字段 ID 不合法") Long> fieldIds;

    /** 模板说明 */
    @Size(max = 255, message = "模板说明不能超过 255 个字符")
    private String description;

    /** 排序号，越小越靠前 */
    private Integer sort;

    /** 状态：1 启用 0 停用，默认 1 */
    @Min(value = 0, message = "状态只能为 0 或 1")
    @Max(value = 1, message = "状态只能为 0 或 1")
    private Integer status;
}
