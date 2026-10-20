package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统角色实体，对应表 sys_role。
 * <p>
 * 角色编码与枚举 {@link com.zhiketong.enums.RoleCode} 保持一致（ADMIN/AGENT/USER）。
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 角色编码：ADMIN/AGENT/USER */
    private String roleCode;

    /** 角色名称 */
    private String roleName;

    /** 角色说明 */
    private String description;

    /** 排序号，越小越靠前 */
    private Integer sort;

    /** 状态：1 启用 0 停用 */
    private Integer status;
}
