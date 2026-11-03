package com.zhiketong.dto.chat;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 发送消息请求（R-36 / C-01 / C-02）。
 *
 * @author 智客通开发组
 */
@Data
public class SendMessageRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息内容：去空格 1-2000 字符 */
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息内容不能超过 2000 个字符")
    private String content;

    /** 内容类型：仅支持 TEXT */
    @Pattern(regexp = "^(TEXT)?$", message = "内容类型仅支持 TEXT")
    private String contentType;

    /** 客户端消息 ID：16-64 位字母、数字、下划线或连字符 */
    @NotBlank(message = "clientMsgId 不能为空")
    @Size(min = 16, max = 64, message = "clientMsgId 长度须为 16-64 位")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "clientMsgId 只能包含字母、数字、下划线或连字符")
    private String clientMsgId;
}
