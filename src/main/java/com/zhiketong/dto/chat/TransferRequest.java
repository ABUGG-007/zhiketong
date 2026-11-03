package com.zhiketong.dto.chat;

import lombok.Data;

import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 请求转人工（R-37 / C-04）。
 *
 * @author 智客通开发组
 */
@Data
public class TransferRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 转人工原因 */
    @Size(max = 100, message = "转人工原因不能超过 100 个字符")
    private String reason;

    /** 咨询类型（技能组 ID），空表示不限技能组 */
    private Long skillGroupId;
}
