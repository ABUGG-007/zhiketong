package com.zhiketong.dto.workorder;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 调整工单优先级请求（R-61），同时触发 SLA 重算。
 *
 * @author 智客通开发组
 */
@Data
public class WorkOrderPriorityRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 新优先级：P0/P1/P2/P3，且不能与当前值相同 */
    @NotBlank(message = "优先级不能为空")
    @Pattern(regexp = "P0|P1|P2|P3", message = "优先级只能为 P0/P1/P2/P3")
    private String priority;

    /** 调整原因 */
    @Size(max = 255, message = "调整原因不能超过 255 个字符")
    private String remark;
}
