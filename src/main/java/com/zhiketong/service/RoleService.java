package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhiketong.dto.admin.RoleVO;
import com.zhiketong.entity.SysRole;
import com.zhiketong.entity.SysUser;
import com.zhiketong.mapper.SysRoleMapper;
import com.zhiketong.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统角色查询服务（M1，R-13）。
 *
 * <p>角色列表不分页，按 sort 升序返回，并统计每个角色下未删除用户数。</p>
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final SysRoleMapper sysRoleMapper;
    private final SysUserMapper sysUserMapper;

    /**
     * 角色列表（R-13）：keyword 匹配 role_code/role_name，status 精确筛选。
     */
    @Transactional(readOnly = true)
    public List<RoleVO> list(String keyword, Integer status) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(SysRole::getRoleCode, kw)
                    .or().like(SysRole::getRoleName, kw));
        }
        if (status != null) {
            wrapper.eq(SysRole::getStatus, status);
        }
        wrapper.orderByAsc(SysRole::getSort).orderByAsc(SysRole::getId);

        List<SysRole> roles = sysRoleMapper.selectList(wrapper);
        Map<String, Long> userCountMap = countUsersByRole();
        List<RoleVO> list = new ArrayList<>(roles.size());
        for (SysRole role : roles) {
            list.add(RoleVO.of(role, userCountMap.getOrDefault(role.getRoleCode(), 0L)));
        }
        return list;
    }

    /**
     * 一次分组查询统计各角色用户数，避免逐角色 count 的 N+1 查询。
     */
    private Map<String, Long> countUsersByRole() {
        QueryWrapper<SysUser> wrapper = new QueryWrapper<SysUser>()
                .select("role_code", "COUNT(*) AS user_count")
                .groupBy("role_code");
        List<Map<String, Object>> rows = sysUserMapper.selectMaps(wrapper);
        Map<String, Long> counts = new HashMap<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object roleCode = row.containsKey("role_code") ? row.get("role_code") : row.get("roleCode");
            Object count = row.containsKey("user_count") ? row.get("user_count") : row.get("userCount");
            if (roleCode != null && count != null) {
                counts.put(String.valueOf(roleCode), Long.parseLong(String.valueOf(count)));
            }
        }
        return counts;
    }
}
