package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 系统用户实体，对应表 sys_user。
 * <p>
 * 管理员、客服坐席、普通用户共用一张表，由 roleCode 区分角色。
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 登录账号 */
    private String username;

    /** 密码（BCrypt 加密） */
    private String password;

    /** 昵称/姓名 */
    private String nickname;

    /** 角色编码：ADMIN/AGENT/USER */
    private String roleCode;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 头像地址 */
    private String avatar;

    /** 状态：1 启用 0 禁用 */
    private Integer status;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;

    /** 最后登录 IP */
    private String lastLoginIp;

    /** 逻辑删除：0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;
}
