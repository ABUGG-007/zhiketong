package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 租户主数据实体，对应表 tenant（S4-A）。
 *
 * <p>本表的 {@code id} 即所有业务表 {@code tenant_id} 的取值，因此不参与租户隔离；
 * {@code planCode} 关联 {@link TenantPlan#getPlanCode()} 决定套餐额度；
 * {@code status} 与 {@code expireTime} 共同决定租户是否可用，登录时校验。</p>
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("tenant")
public class Tenant extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户编码，嵌入接入等场景的对外标识 */
    private String tenantCode;

    /** 租户名称（企业名） */
    private String tenantName;

    /** 状态：ENABLED 正常 / DISABLED 已停用 / EXPIRED 已到期 */
    private String status;

    /** 套餐编码，关联 tenant_plan.plan_code */
    private String planCode;

    /** 服务到期时间，为空表示不限 */
    private LocalDateTime expireTime;

    /** 联系人姓名 */
    private String contactName;

    /** 联系人手机号 */
    private String contactPhone;

    /** 联系人邮箱 */
    private String contactEmail;

    /** 备注 */
    private String remark;

    /** 逻辑删除：0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;
}
