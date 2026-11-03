package com.zhiketong.dto.agent;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 会话转接请求（R-47）。
 *
 * @author 智客通开发组
 */
@Data
public class AgentTransferRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标坐席用户 ID */
    @NotNull(message = "目标坐席不能为空")
    private Long targetAgentId;

    /** 转接原因 */
    @Size(max = 255, message = "转接原因不能超过 255 个字符")
    private String reason;
}
