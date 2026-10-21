package com.zhiketong.dto.agent;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.io.Serializable;

/**
 * 坐席在线/忙碌状态切换请求。
 */
@Data
public class AgentStatusUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "在线状态不能为空")
    @Pattern(regexp = "ONLINE|BUSY", message = "在线状态只能为 ONLINE 或 BUSY")
    private String onlineStatus;
}
