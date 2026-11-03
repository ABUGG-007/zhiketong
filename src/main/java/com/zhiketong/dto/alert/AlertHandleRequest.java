package com.zhiketong.dto.alert;

import lombok.Data;

import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 告警确认与关闭请求（R-188 / R-189 管理端，R-197 坐席端）。
 *
 * @author 智客通开发组
 */
@Data
public class AlertHandleRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 处理说明：确认意见或关闭原因 */
    @Size(max = 255, message = "处理说明不能超过 255 个字符")
    private String note;
}
