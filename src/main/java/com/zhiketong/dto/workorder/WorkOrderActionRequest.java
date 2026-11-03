package com.zhiketong.dto.workorder;

import lombok.Data;

import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 受理 / 关闭工单请求（R-56 / R-59），备注可选。
 *
 * @author 智客通开发组
 */
@Data
public class WorkOrderActionRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 说明：受理说明或关闭说明，ADMIN 强制关闭时必填 */
    @Size(max = 255, message = "说明不能超过 255 个字符")
    private String remark;
}
