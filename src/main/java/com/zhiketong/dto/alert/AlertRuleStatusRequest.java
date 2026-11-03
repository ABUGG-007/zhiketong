package com.zhiketong.dto.alert;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 告警规则启停请求（R-183）。
 *
 * @author 智客通开发组
 */
@Data
public class AlertRuleStatusRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标状态：1 启用 0 停用 */
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "status 只能为 0 或 1")
    @Max(value = 1, message = "status 只能为 0 或 1")
    private Integer status;
}
