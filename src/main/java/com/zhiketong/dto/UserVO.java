package com.zhiketong.dto;

import com.zhiketong.entity.SysUser;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户视图对象（不返回密码等敏感字段）。
 */
@Data
public class UserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String nickname;
    private String roleCode;
    private String roleName;
    private String phone;
    private String email;
    private String avatar;
    private Integer status;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;

    /**
     * 实体转 VO。
     */
    public static UserVO of(SysUser user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRoleCode(user.getRoleCode());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setCreateTime(user.getCreateTime());
        vo.setRoleName(roleNameOf(user.getRoleCode()));
        return vo;
    }

    private static String roleNameOf(String roleCode) {
        if ("ADMIN".equals(roleCode)) {
            return "管理员";
        }
        if ("AGENT".equals(roleCode)) {
            return "客服坐席";
        }
        if ("USER".equals(roleCode)) {
            return "普通用户";
        }
        return roleCode;
    }
}
