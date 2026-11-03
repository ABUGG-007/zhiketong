package com.zhiketong.dto.workorder;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 重开工单请求（R-60）。
 *
 * @author 智客通开发组
 */
@Data
public class WorkOrderReopenRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 重开原因：1-255 个字符 */
    @NotBlank(message = "重开原因不能为空")
    @Size(max = 255, message = "重开原因不能超过 255 个字符")
    private String reason;
}
