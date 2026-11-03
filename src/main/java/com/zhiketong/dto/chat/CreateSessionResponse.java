package com.zhiketong.dto.chat;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建会话响应（R-31）。
 *
 * @author 智客通开发组
 */
@Data
public class CreateSessionResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 新建会话 */
    private SessionVO session;

    /** 欢迎消息 */
    private MessageVO welcomeMessage;
}
