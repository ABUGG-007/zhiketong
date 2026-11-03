package com.zhiketong.dto.sla;

import lombok.Data;

import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 工单停表 / 恢复计时请求（R-134 挂起、R-135 恢复）。
 *
 * <p>挂起时 {@code pauseType} 缺省为 WAITING_CUSTOMER（等待客户反馈），{@code reason} 记录停表原因；
 * 恢复时 {@code pauseType} 被忽略，{@code reason} 作为恢复说明写入流转日志。</p>
 *
 * @author 智客通开发组
 */
@Data
public class OrderPauseRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 停表类型：WAITING_CUSTOMER 等待客户反馈 / OTHER 其他原因，缺省 WAITING_CUSTOMER */
    @Pattern(regexp = "WAITING_CUSTOMER|OTHER", message = "停表类型只能为 WAITING_CUSTOMER 或 OTHER")
    private String pauseType;

    /** 停表原因 / 恢复说明 */
    @Size(max = 255, message = "说明不能超过 255 个字符")
    private String reason;
}
