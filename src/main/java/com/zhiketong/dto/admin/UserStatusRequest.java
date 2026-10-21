package com.zhiketong.dto.admin;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 启用/禁用用户请求（R-11）。
 */
@Data
public class UserStatusRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：0 禁用 1 启用 */
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态只能为 0 或 1")
    @Max(value = 1, message = "状态只能为 0 或 1")
    private Integer status;
}
