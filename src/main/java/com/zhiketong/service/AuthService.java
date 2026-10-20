package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.Constants;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.ChangePasswordRequest;
import com.zhiketong.dto.LoginRequest;
import com.zhiketong.dto.LoginResponse;
import com.zhiketong.dto.RegisterRequest;
import com.zhiketong.dto.UserVO;
import com.zhiketong.entity.SysLoginLog;
import com.zhiketong.entity.SysUser;
import com.zhiketong.mapper.SysLoginLogMapper;
import com.zhiketong.mapper.SysUserMapper;
import com.zhiketong.security.JwtToken;
import com.zhiketong.security.JwtTokenUtil;
import com.zhiketong.security.LoginUser;
import com.zhiketong.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务：登录、注册、当前用户、登出、修改密码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final SysLoginLogMapper sysLoginLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 登录：校验账号密码、状态，签发 JWT，并记录登录日志。
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(LoginRequest request, String ip, String userAgent) {
        SysUser user = findByUsername(request.getUsername());
        if (user == null) {
            saveLoginLog(null, request.getUsername(), ip, userAgent, false, "用户不存在");
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != Constants.STATUS_ENABLED) {
            saveLoginLog(user.getId(), user.getUsername(), ip, userAgent, false, "账号已被禁用");
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用，请联系管理员");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            saveLoginLog(user.getId(), user.getUsername(), ip, userAgent, false, "密码错误");
            throw new BusinessException("用户名或密码错误");
        }

        JwtToken jwtToken = jwtTokenUtil.generate(
                user.getId(), user.getUsername(), user.getNickname(), user.getRoleCode());

        // 更新最后登录信息
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginTime(LocalDateTime.now());
        update.setLastLoginIp(ip);
        sysUserMapper.updateById(update);

        saveLoginLog(user.getId(), user.getUsername(), ip, userAgent, true, "登录成功");

        return LoginResponse.builder()
                .token(jwtToken.getToken())
                .tokenType("Bearer")
                .expiresIn(jwtToken.getExpiresInSeconds())
                .user(UserVO.of(user))
                .build();
    }

    /**
     * 注册普通用户。
     */
    @Transactional(rollbackFor = Exception.class)
    public UserVO register(RegisterRequest request) {
        SysUser exists = findByUsername(request.getUsername());
        if (exists != null) {
            throw new BusinessException(ResultCode.CONFLICT, "用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setRoleCode(Constants.ROLE_USER);
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setStatus(Constants.STATUS_ENABLED);
        sysUserMapper.insert(user);
        log.info("新用户注册成功：userId={}, username={}", user.getId(), user.getUsername());
        return UserVO.of(sysUserMapper.selectById(user.getId()));
    }

    /**
     * 当前登录用户信息。
     */
    @Transactional(readOnly = true)
    public UserVO currentUser() {
        LoginUser loginUser = SecurityUtils.requireLogin();
        SysUser user = sysUserMapper.selectById(loginUser.getUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在或已被删除");
        }
        return UserVO.of(user);
    }

    /**
     * 登出：将当前 Token 的 jti 写入 Redis 黑名单，剩余有效期即为 TTL。
     */
    public void logout() {
        LoginUser loginUser = SecurityUtils.requireLogin();
        if (loginUser.getTokenId() == null || loginUser.getTokenExpireAt() == null) {
            return;
        }
        long ttlSeconds = (loginUser.getTokenExpireAt() - System.currentTimeMillis()) / 1000;
        if (ttlSeconds > 0) {
            stringRedisTemplate.opsForValue().set(
                    Constants.TOKEN_BLACKLIST_PREFIX + loginUser.getTokenId(),
                    "1", ttlSeconds, TimeUnit.SECONDS);
        }
    }

    /**
     * 修改密码。
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordRequest request) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        SysUser user = sysUserMapper.selectById(loginUser.getUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("原密码不正确");
        }
        if (request.getOldPassword().equals(request.getNewPassword())) {
            throw new BusinessException("新密码不能与原密码相同");
        }
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setPassword(passwordEncoder.encode(request.getNewPassword()));
        sysUserMapper.updateById(update);
        log.info("用户修改密码成功：userId={}", user.getId());
    }

    private SysUser findByUsername(String username) {
        return sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .last("LIMIT 1"));
    }

    /**
     * 记录登录日志，失败不影响主流程。
     */
    private void saveLoginLog(Long userId, String username, String ip, String userAgent,
                              boolean success, String message) {
        try {
            SysLoginLog loginLog = new SysLoginLog();
            loginLog.setUserId(userId);
            loginLog.setUsername(username);
            loginLog.setIp(ip);
            loginLog.setUserAgent(userAgent);
            loginLog.setSuccess(success ? 1 : 0);
            loginLog.setMessage(message);
            sysLoginLogMapper.insert(loginLog);
        } catch (Exception e) {
            log.warn("记录登录日志失败：{}", e.getMessage());
        }
    }
}
