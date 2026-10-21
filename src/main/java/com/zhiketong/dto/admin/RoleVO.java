package com.zhiketong.dto.admin;

import com.zhiketong.entity.SysRole;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色视图对象（R-13）：包含该角色下未删除用户数量。
 */
@Data
public class RoleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String roleCode;
    private String roleName;
    private String description;
    private Integer sort;
    private Integer status;

    /** 该角色下未删除用户数 */
    private Long userCount;

    private LocalDateTime createTime;

    /**
     * 实体转 VO。
     *
     * @param role      角色实体
     * @param userCount 该角色未删除用户数
     * @return 角色视图对象
     */
    public static RoleVO of(SysRole role, long userCount) {
        if (role == null) {
            return null;
        }
        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setRoleCode(role.getRoleCode());
        vo.setRoleName(role.getRoleName());
        vo.setDescription(role.getDescription());
        vo.setSort(role.getSort());
        vo.setStatus(role.getStatus());
        vo.setUserCount(userCount);
        vo.setCreateTime(role.getCreateTime());
        return vo;
    }
}
