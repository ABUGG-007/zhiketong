package com.zhiketong.dto.monitor;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 组长强插发言请求（R-201，AS-10）。
 *
 * <p>强插消息以 {@code SUPERVISOR} 发送方类型落库，访客与坐席双方可见，
 * 与普通坐席发言区分展示，便于事后追溯是谁介入。</p>
 *
 * @author 智客通开发组
 */
@Data
public class SupervisorWhisperRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 强插内容 */
    @NotBlank(message = "强插内容不能为空")
    @Size(max = 2000, message = "强插内容不能超过 2000 个字符")
    private String content;
}
