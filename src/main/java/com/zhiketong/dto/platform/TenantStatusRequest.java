package com.zhiketong.dto.platform;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.io.Serializable;

/**
 * 平台端变更租户状态请求（S4-B，PL-02）。
 *
 * <p>停用或标记到期后，该租户内账号立即无法登录；已签发的令牌在自然到期前仍可访问，
 * 需要立即切断时同步重置租户管理员密码或清理会话。</p>
 *
 * @author 智客通开发组
 */
@Data
public class TenantStatusRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标状态：ENABLED 正常 / DISABLED 停用 / EXPIRED 到期 */
    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "^(ENABLED|DISABLED|EXPIRED)$", message = "状态只能为 ENABLED、DISABLED 或 EXPIRED")
    private String status;
}
