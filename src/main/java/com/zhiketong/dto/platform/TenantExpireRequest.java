package com.zhiketong.dto.platform;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 平台端设置租户到期时间请求（S4-B，PL-02）。
 *
 * @author 智客通开发组
 */
@Data
public class TenantExpireRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 服务到期时间，必须晚于当前时间 */
    @NotNull(message = "到期时间不能为空")
    private LocalDateTime expireTime;
}
