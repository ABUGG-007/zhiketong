package com.zhiketong.dto.skill;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 技能组启停请求（R-100）。
 *
 * @author 智客通开发组
 */
@Data
public class SkillGroupStatusRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：1 启用 0 停用 */
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态只能为 0 或 1")
    @Max(value = 1, message = "状态只能为 0 或 1")
    private Integer status;
}
