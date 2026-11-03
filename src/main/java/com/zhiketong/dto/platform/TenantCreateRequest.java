package com.zhiketong.dto.platform;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 平台端开通租户请求（S4-B，PL-02）。
 *
 * <p>一次提交同时完成三件事：建租户、建商户管理员账号、初始化网页接入配置，
 * 保证新租户开通后即可登录并对外接入，不需要人工补数据。</p>
 *
 * @author 智客通开发组
 */
@Data
public class TenantCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 租户编码：2-32 位小写字母、数字、下划线或连字符，留空自动生成 */
    @Pattern(regexp = "^$|^[a-z0-9_-]{2,32}$",
            message = "租户编码须为 2-32 位小写字母、数字、下划线或连字符")
    private String tenantCode;

    /** 租户名称（企业名） */
    @NotBlank(message = "租户名称不能为空")
    @Size(min = 2, max = 50, message = "租户名称长度须为 2-50 个字符")
    private String tenantName;

    /** 套餐编码，须为 tenant_plan 中存在且启用的套餐 */
    @NotBlank(message = "套餐不能为空")
    @Size(max = 32, message = "套餐编码长度不能超过 32")
    private String planCode;

    /** 服务到期时间，留空时按套餐周期自动计算（试用版 +14 天，其余 +1 年） */
    private LocalDateTime expireTime;

    /** 联系人姓名 */
    @Size(max = 32, message = "联系人姓名长度不能超过 32")
    private String contactName;

    /** 联系人手机号 */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "联系人手机号格式不正确")
    private String contactPhone;

    /** 联系人邮箱 */
    @Email(message = "联系人邮箱格式不正确")
    @Size(max = 100, message = "联系人邮箱长度不能超过 100")
    private String contactEmail;

    /** 备注 */
    @Size(max = 200, message = "备注长度不能超过 200")
    private String remark;

    /** 租户管理员登录名 */
    @NotBlank(message = "租户管理员登录名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{3,32}$", message = "租户管理员登录名须为 3-32 位字母、数字或下划线")
    private String adminUsername;

    /** 租户管理员初始密码 */
    @NotBlank(message = "租户管理员初始密码不能为空")
    @Size(min = 6, max = 32, message = "租户管理员初始密码长度须为 6-32 位")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "租户管理员初始密码须同时包含字母和数字")
    private String adminPassword;

    /** 租户管理员昵称 */
    @Size(max = 32, message = "租户管理员昵称长度不能超过 32")
    private String adminNickname;
}
