package com.zhiketong.dto.agent;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 坐席状态视图对象。
 */
@Data
public class AgentStatusVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long agentId;
    private String agentName;
    private String onlineStatus;
    private Integer maxConcurrent;
    private Integer currentCount;
    private Integer totalServed;
    private LocalDateTime lastHeartbeat;
}
