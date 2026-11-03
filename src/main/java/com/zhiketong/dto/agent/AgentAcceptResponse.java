package com.zhiketong.dto.agent;

import com.zhiketong.dto.chat.MessageVO;
import com.zhiketong.dto.chat.SessionVO;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 接入排队会话响应（R-44）。
 *
 * @author 智客通开发组
 */
@Data
public class AgentAcceptResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 接入后的会话 */
    private SessionVO session;

    /** 会话所属用户 */
    private AgentBriefVO user;

    /** 最近 10 条消息，供坐席快速了解上下文 */
    private List<MessageVO> lastMessages;
}
