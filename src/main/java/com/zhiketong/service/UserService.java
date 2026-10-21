package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.Constants;
import com.zhiketong.common.PageResult;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.UserVO;
import com.zhiketong.dto.admin.UserCreateRequest;
import com.zhiketong.dto.admin.UserPasswordResetRequest;
import com.zhiketong.dto.admin.UserStatusRequest;
import com.zhiketong.dto.admin.UserUpdateRequest;
import com.zhiketong.entity.AgentStatus;
import com.zhiketong.entity.ChatSession;
import com.zhiketong.entity.SysRole;
import com.zhiketong.entity.SysUser;
import com.zhiketong.enums.AgentOnlineStatus;
import com.zhiketong.enums.RoleCode;
import com.zhiketong.enums.SessionStatus;
import com.zhiketong.mapper.AgentStatusMapper;
import com.zhiketong.mapper.ChatSessionMapper;
import com.zhiketong.mapper.SysRoleMapper;
import com.zhiketong.mapper.SysUserMapper;
import com.zhiketong.security.LoginUser;
import com.zhiketong.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统用户管理服务（M1，R-06 ~ R-12）。
 *
 * <p>直接读写 sys_user 真实数据，密码统一 BCrypt 存储；新建坐席时同步初始化 agent_status 行。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    /** 内置管理员账号，不允许降级、禁用、删除 */
    private static final String BUILT_IN_ADMIN_USERNAME = "admin";

    /** 新建坐席默认最大同时接待数 */
    private static final int DEFAULT_MAX_CONCURRENT = 5;

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final AgentStatusMapper agentStatusMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 用户分页列表（R-06）：keyword 模糊匹配 username/nickname/phone，按 id 倒序。
     */
    @Transactional(readOnly = true)
    public PageResult<UserVO> page(int pageNum, int pageSize, String keyword,
                                   String roleCode, Integer status) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(SysUser::getUsername, kw)
                    .or().like(SysUser::getNickname, kw)
                    .or().like(SysUser::getPhone, kw));
        }
        String roleFilter = trimToNull(roleCode);
        if (roleFilter != null) {
            if (RoleCode.fromCode(roleFilter) == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "角色编码只能为 ADMIN、AGENT 或 USER");
            }
            wrapper.eq(SysUser::getRoleCode, roleFilter);
        }
        if (status != null) {
            wrapper.eq(SysUser::getStatus, status);
        }
        wrapper.orderByDesc(SysUser::getId);

        Page<SysUser> page = sysUserMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<UserVO> list = new ArrayList<>(page.getRecords().size());
        for (SysUser user : page.getRecords()) {
            list.add(UserVO.of(user));
        }
        return PageResult.of(page, list);
    }

    /**
     * 用户详情（R-07）。
     */
    @Transactional(readOnly = true)
    public UserVO detail(Long id) {
        return UserVO.of(getExistingUser(id));
    }

    /**
     * 新增用户（R-08）：用户名/手机号/邮箱唯一，密码 BCrypt 存储；AGENT 同步初始化坐席状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public UserVO create(UserCreateRequest request) {
        String username = request.getUsername().trim();
        if (countByUsername(username) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "用户名已存在");
        }
        SysRole role = requireEnabledRole(request.getRoleCode().trim());

        String phone = trimToNull(request.getPhone());
        if (phone != null && countByPhone(phone, null) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "手机号已被使用");
        }
        String email = trimToNull(request.getEmail());
        if (email != null && countByEmail(email, null) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "邮箱已被使用");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(StringUtils.hasText(request.getNickname())
                ? request.getNickname().trim() : username);
        user.setRoleCode(role.getRoleCode());
        user.setPhone(phone);
        user.setEmail(email);
        user.setAvatar(trimToNull(request.getAvatar()));
        user.setStatus(request.getStatus() == null ? Constants.STATUS_ENABLED : request.getStatus());
        try {
            sysUserMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 逻辑删除的账号仍占用数据库唯一键，这里统一转换为 409
            throw new BusinessException(ResultCode.CONFLICT, "用户名已存在");
        }

        if (Constants.ROLE_AGENT.equals(role.getRoleCode())) {
            ensureAgentStatus(user.getId());
        }
        log.info("新增用户成功：userId={}, username={}, roleCode={}",
                user.getId(), user.getUsername(), user.getRoleCode());
        return UserVO.of(sysUserMapper.selectById(user.getId()));
    }

    /**
     * 编辑用户（R-09）：仅更新传入字段；内置 admin 不可降级/禁用，本人不可改角色/状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public UserVO update(Long id, UserUpdateRequest request) {
        SysUser user = getExistingUser(id);
        LoginUser current = SecurityUtils.requireLogin();
        boolean self = id.equals(current.getUserId());
        boolean builtInAdmin = isBuiltInAdmin(user);

        String newRoleCode = null;
        if (request.getRoleCode() != null) {
            String roleCode = request.getRoleCode().trim();
            if (!roleCode.equals(user.getRoleCode())) {
                if (self) {
                    throw new BusinessException(ResultCode.FORBIDDEN, "不能修改自己的角色");
                }
                if (builtInAdmin) {
                    throw new BusinessException(ResultCode.FORBIDDEN, "内置管理员不允许降级");
                }
                newRoleCode = requireEnabledRole(roleCode).getRoleCode();
            }
        }

        Integer newStatus = request.getStatus();
        if (newStatus != null && !newStatus.equals(user.getStatus())) {
            if (self) {
                throw new BusinessException(ResultCode.FORBIDDEN, "不能修改自己的状态");
            }
            if (builtInAdmin) {
                throw new BusinessException(ResultCode.FORBIDDEN, "内置管理员不允许修改状态");
            }
        }

        boolean updatePhone = request.getPhone() != null;
        String phone = trimToNull(request.getPhone());
        if (updatePhone && phone != null && countByPhone(phone, id) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "手机号已被使用");
        }
        boolean updateEmail = request.getEmail() != null;
        String email = trimToNull(request.getEmail());
        if (updateEmail && email != null && countByEmail(email, id) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "邮箱已被使用");
        }

        LambdaUpdateWrapper<SysUser> wrapper = new LambdaUpdateWrapper<>();
        boolean changed = false;
        if (request.getNickname() != null && StringUtils.hasText(request.getNickname())) {
            wrapper.set(SysUser::getNickname, request.getNickname().trim());
            changed = true;
        }
        if (newRoleCode != null) {
            wrapper.set(SysUser::getRoleCode, newRoleCode);
            changed = true;
        }
        if (updatePhone) {
            wrapper.set(SysUser::getPhone, phone);
            changed = true;
        }
        if (updateEmail) {
            wrapper.set(SysUser::getEmail, email);
            changed = true;
        }
        if (request.getAvatar() != null) {
            wrapper.set(SysUser::getAvatar, trimToNull(request.getAvatar()));
            changed = true;
        }
        if (newStatus != null && !newStatus.equals(user.getStatus())) {
            wrapper.set(SysUser::getStatus, newStatus);
            changed = true;
        }
        if (changed) {
            wrapper.eq(SysUser::getId, id);
            sysUserMapper.update(null, wrapper);
        }

        String finalRoleCode = newRoleCode != null ? newRoleCode : user.getRoleCode();
        Integer finalStatus = newStatus != null ? newStatus : user.getStatus();
        if (newRoleCode != null && Constants.ROLE_AGENT.equals(finalRoleCode)) {
            ensureAgentStatus(id);
        }
        if (newRoleCode != null && Constants.ROLE_AGENT.equals(user.getRoleCode())
                && !Constants.ROLE_AGENT.equals(finalRoleCode)) {
            // 角色从坐席调整为非坐席时，移除坐席状态行，避免残留孤儿数据
            agentStatusMapper.delete(new LambdaQueryWrapper<AgentStatus>()
                    .eq(AgentStatus::getAgentId, id));
        }
        if (newStatus != null && !newStatus.equals(user.getStatus())
                && Constants.STATUS_DISABLED == finalStatus
                && Constants.ROLE_AGENT.equals(finalRoleCode)) {
            setAgentOffline(id);
        }
        log.info("编辑用户成功：userId={}", id);
        return UserVO.of(sysUserMapper.selectById(id));
    }

    /**
     * 逻辑删除用户（R-10）：内置 admin、本人不可删；坐席存在 HUMAN 会话时拒绝。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysUser user = getExistingUser(id);
        LoginUser current = SecurityUtils.requireLogin();
        if (isBuiltInAdmin(user)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "内置管理员不允许删除");
        }
        if (id.equals(current.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能删除自己");
        }
        if (Constants.ROLE_AGENT.equals(user.getRoleCode())) {
            Long humanCount = chatSessionMapper.selectCount(new LambdaQueryWrapper<ChatSession>()
                    .eq(ChatSession::getAgentId, id)
                    .eq(ChatSession::getStatus, SessionStatus.HUMAN.getCode()));
            if (humanCount != null && humanCount > 0) {
                throw new BusinessException(ResultCode.CONFLICT, "该坐席存在进行中的会话，请先结束会话");
            }
            agentStatusMapper.delete(new LambdaQueryWrapper<AgentStatus>()
                    .eq(AgentStatus::getAgentId, id));
        }
        sysUserMapper.deleteById(id);
        log.info("删除用户成功：userId={}", id);
    }

    /**
     * 启用/禁用用户（R-11）：内置 admin、本人不可禁用；禁用坐席时同步置离线。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateStatus(Long id, UserStatusRequest request) {
        SysUser user = getExistingUser(id);
        LoginUser current = SecurityUtils.requireLogin();
        Integer status = request.getStatus();
        if (Constants.STATUS_DISABLED == status) {
            if (isBuiltInAdmin(user)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "内置管理员不允许禁用");
            }
            if (id.equals(current.getUserId())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "不能禁用自己");
            }
        }
        LambdaUpdateWrapper<SysUser> wrapper = new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, id)
                .set(SysUser::getStatus, status);
        sysUserMapper.update(null, wrapper);

        if (Constants.STATUS_DISABLED == status && Constants.ROLE_AGENT.equals(user.getRoleCode())) {
            setAgentOffline(id);
        }
        log.info("修改用户状态成功：userId={}, status={}", id, status);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("status", status);
        return data;
    }

    /**
     * 重置用户密码（R-12）：BCrypt 存储，无需原密码。
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, UserPasswordResetRequest request) {
        getExistingUser(id);
        LambdaUpdateWrapper<SysUser> wrapper = new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, id)
                .set(SysUser::getPassword, passwordEncoder.encode(request.getNewPassword()));
        sysUserMapper.update(null, wrapper);
        log.info("重置用户密码成功：userId={}", id);
    }

    /**
     * 查询未删除用户，不存在抛 404。
     */
    private SysUser getExistingUser(Long id) {
        SysUser user = id == null ? null : sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    /**
     * 校验角色存在且已启用。
     */
    private SysRole requireEnabledRole(String roleCode) {
        SysRole role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode)
                .last("LIMIT 1"));
        if (role == null || role.getStatus() == null || role.getStatus() != Constants.STATUS_ENABLED) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "角色不存在或已停用");
        }
        return role;
    }

    /**
     * 确保坐席状态行存在（新建 AGENT 或角色变更为 AGENT 时调用）。
     */
    private void ensureAgentStatus(Long agentId) {
        Long count = agentStatusMapper.selectCount(new LambdaQueryWrapper<AgentStatus>()
                .eq(AgentStatus::getAgentId, agentId));
        if (count != null && count > 0) {
            return;
        }
        AgentStatus agentStatus = new AgentStatus();
        agentStatus.setAgentId(agentId);
        agentStatus.setOnlineStatus(AgentOnlineStatus.OFFLINE.getCode());
        agentStatus.setMaxConcurrent(DEFAULT_MAX_CONCURRENT);
        agentStatus.setCurrentCount(0);
        agentStatus.setTotalServed(0);
        agentStatusMapper.insert(agentStatus);
    }

    /**
     * 坐席离线（禁用坐席时调用）。
     */
    private void setAgentOffline(Long agentId) {
        LambdaUpdateWrapper<AgentStatus> wrapper = new LambdaUpdateWrapper<AgentStatus>()
                .eq(AgentStatus::getAgentId, agentId)
                .set(AgentStatus::getOnlineStatus, AgentOnlineStatus.OFFLINE.getCode());
        agentStatusMapper.update(null, wrapper);
    }

    private long countByUsername(String username) {
        Long count = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        return count == null ? 0L : count;
    }

    private long countByPhone(String phone, Long excludeId) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, phone);
        if (excludeId != null) {
            wrapper.ne(SysUser::getId, excludeId);
        }
        Long count = sysUserMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    private long countByEmail(String email, Long excludeId) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getEmail, email);
        if (excludeId != null) {
            wrapper.ne(SysUser::getId, excludeId);
        }
        Long count = sysUserMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    private boolean isBuiltInAdmin(SysUser user) {
        return Constants.ROLE_ADMIN.equals(user.getRoleCode())
                && BUILT_IN_ADMIN_USERNAME.equals(user.getUsername());
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
