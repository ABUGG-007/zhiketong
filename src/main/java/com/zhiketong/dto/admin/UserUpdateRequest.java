package com.zhiketong.dto.admin;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 编辑用户请求（R-09）：字段均为可选，传则更新；username、password 不可通过本接口修改。
 */
@Data
public class UserUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 昵称：≤ 32 */
    @Size(max = 32, message = "昵称长度不能超过 32")
    private String nickname;

    /** 角色编码：ADMIN/AGENT/USER */
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

    /** 状态：0 禁用 1 启用 */
    @Min(value = 0, message = "状态只能为 0 或 1")
    @Max(value = 1, message = "状态只能为 0 或 1")
    private Integer status;
}
