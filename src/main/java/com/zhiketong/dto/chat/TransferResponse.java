package com.zhiketong.dto.chat;

import com.zhiketong.dto.agent.AgentBriefVO;
import lombok.Data;

import java.io.Serializable;

/**
 * 转人工响应（R-37）。
 *
 * @author 智客通开发组
 */
@Data
public class TransferResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话 ID */
    private Long sessionId;

    /** 处理后的状态：WAITING/HUMAN */
    private String status;

    /** 排队序号，已接入时为 0 */
    private Integer queuePosition;

    /** 当前排队总数 */
    private Integer queueSize;

    /** 预估等待秒数，最少 30 */
    private Integer estimatedWaitSeconds;

    /** 已接入的坐席信息，未接入为 null */
    private AgentBriefVO agent;
}
