package com.zhiketong.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 大模型对话消息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiMessage {

    /** 角色：system / user / assistant */
    private String role;

    /** 消息内容 */
    private String content;
}
