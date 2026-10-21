package com.zhiketong.dto.admin;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 新增用户请求（R-08）。
 */
@Data
public class UserCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 登录名：3-32 位字母、数字或下划线，唯一 */
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{3,32}$", message = "用户名须为 3-32 位字母、数字或下划线")
    private String username;

    /** 初始密码：6-32 位，须同时包含字母和数字 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度须为 6-32 位")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "密码须同时包含字母和数字")
    private String password;

    /** 昵称：≤ 32，默认取 username */
    @Size(max = 32, message = "昵称长度不能超过 32")
    private String nickname;

    /** 角色编码：ADMIN/AGENT/USER */
    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "^(ADMIN|AGENT|USER)$", message = "角色编码只能为 ADMIN、AGENT 或 USER")
    private String roleCode;

    /** 手机号：^1[3-9]\\d{9}$，唯一 */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 邮箱：唯一 */
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过 100")
    private String email;

    /** 头像地址：≤ 255 */
    @Size(max = 255, message = "头像地址长度不能超过 255")
    private String avatar;

    /** 状态：0 禁用 1 启用，默认 1 */
    @Min(value = 0, message = "状态只能为 0 或 1")
    @Max(value = 1, message = "状态只能为 0 或 1")
    private Integer status;
}
