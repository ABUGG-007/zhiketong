package com.zhiketong.dto.monitor;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 组长强制派单请求（R-203，AS-10）。
 *
 * @author 智客通开发组
 */
@Data
public class SupervisorReassignRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标坐席 ID */
    @NotNull(message = "目标坐席不能为空")
    private Long targetAgentId;

    /** 派单原因，写入会话系统消息 */
    @Size(max = 100, message = "派单原因不能超过 100 个字符")
    private String reason;
}
