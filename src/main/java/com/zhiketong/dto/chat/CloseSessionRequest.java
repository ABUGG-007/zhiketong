package com.zhiketong.dto.chat;

import lombok.Data;

import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 结束会话请求（R-38）。
 *
 * @author 智客通开发组
 */
@Data
public class CloseSessionRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 关闭原因：USER_CLOSED/AGENT_CLOSED/SYSTEM，不传按角色默认 */
    @Pattern(regexp = "^(USER_CLOSED|AGENT_CLOSED|SYSTEM)?$", message = "关闭原因不合法")
    private String closeReason;

    /** 结束备注（坐席结束会话时可选，M4 使用） */
    @Size(max = 255, message = "结束备注不能超过 255 个字符")
    private String remark;
}
