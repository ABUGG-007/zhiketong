package com.zhiketong.dto.admin;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 管理员重置用户密码请求（R-12）。
 */
@Data
public class UserPasswordResetRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 新密码：6-32 位，须同时包含字母和数字 */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度须为 6-32 位")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "密码须同时包含字母和数字")
    private String newPassword;
}
