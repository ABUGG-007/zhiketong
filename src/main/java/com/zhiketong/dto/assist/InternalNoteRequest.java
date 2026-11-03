package com.zhiketong.dto.assist;

import lombok.Data;

import java.io.Serializable;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 新增内部备注请求（R-114）。
 * <p>备注仅坐席与管理端可见，不对客推送，也不计入会话消息数。</p>
 *
 * @author 智客通开发组
 */
@Data
public class InternalNoteRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 备注内容 */
    @NotBlank(message = "备注内容不能为空")
    @Size(max = 500, message = "备注内容不能超过 500 个字符")
    private String content;
}
