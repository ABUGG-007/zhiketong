package com.zhiketong.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * AI 对话请求（骨架阶段提供 REST 调试入口，正式对话后续走 WebSocket）。
 */
@Data
public class AiChatRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息内容不能超过 2000 字")
    private String message;

    /** 会话 ID，可为空（仅调试时使用） */
    private Long sessionId;
}
