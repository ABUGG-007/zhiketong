package com.zhiketong.dto.platform;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 平台端编辑租户请求（S4-B，PL-02）。
 *
 * <p>只维护资料与到期时间，套餐与状态有独立的变更接口，
 * 避免一次提交同时改套餐和状态导致额度校验口径混乱。</p>
 *
 * @author 智客通开发组
 */
@Data
public class TenantUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 租户名称，留空不修改 */
    @Size(max = 50, message = "租户名称长度不能超过 50")
    private String tenantName;

    /** 联系人姓名，留空不修改 */
    @Size(max = 32, message = "联系人姓名长度不能超过 32")
    private String contactName;

    /** 联系人手机号，留空不修改 */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "联系人手机号格式不正确")
    private String contactPhone;

    /** 联系人邮箱，留空不修改 */
    @Email(message = "联系人邮箱格式不正确")
    @Size(max = 100, message = "联系人邮箱长度不能超过 100")
    private String contactEmail;

    /** 备注，留空不修改 */
    @Size(max = 200, message = "备注长度不能超过 200")
    private String remark;

    /** 服务到期时间，为空不修改 */
    private LocalDateTime expireTime;
}
