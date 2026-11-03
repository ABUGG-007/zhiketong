package com.zhiketong.dto.workorder;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 处理进展 / 解决工单请求（R-57 / R-58），说明必填。
 *
 * @author 智客通开发组
 */
@Data
public class WorkOrderRemarkRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 处理进展或解决说明：1-500 个字符 */
    @NotBlank(message = "处理说明不能为空")
    @Size(max = 500, message = "处理说明不能超过 500 个字符")
    private String remark;
}
