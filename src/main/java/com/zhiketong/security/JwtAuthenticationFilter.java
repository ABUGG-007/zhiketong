package com.zhiketong.security;

import com.zhiketong.common.Constants;
import com.zhiketong.config.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JWT 认证过滤器：从 Authorization 头解析 Token 并写入 SecurityContext。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenUtil jwtTokenUtil;
    private final JwtProperties jwtProperties;
    private final StringRedisTemplate stringRedisTemplate;

    /** Redis 不可用时跳过黑名单校验的窗口：避免每个请求都等待连接超时 */
    private static final long REDIS_SKIP_MILLIS = 30_000L;

    /** 黑名单校验的熔断截止时间（毫秒时间戳） */
    private final AtomicLong blacklistSkipUntil = new AtomicLong(0);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String header = request.getHeader(jwtProperties.getHeader());
            String prefix = jwtProperties.getPrefix();
            if (StringUtils.hasText(header) && header.startsWith(prefix)) {
                String token = header.substring(prefix.length()).trim();
                try {
                    Claims claims = jwtTokenUtil.parse(token);
                    String jti = claims.getId();

                    boolean blacklisted = false;
                    if (System.currentTimeMillis() >= blacklistSkipUntil.get()) {
                        try {
                            blacklisted = Boolean.TRUE.equals(
                                    stringRedisTemplate.hasKey(Constants.TOKEN_BLACKLIST_PREFIX + jti));
                        } catch (Exception redisException) {
                            // Redis 不可用时不影响接口鉴权主流程，熔断一段时间避免反复等待超时
                            blacklistSkipUntil.set(System.currentTimeMillis() + REDIS_SKIP_MILLIS);
                            log.warn("校验 Token 黑名单失败，{} 秒内跳过黑名单校验：{}",
                                    REDIS_SKIP_MILLIS / 1000, redisException.getMessage());
                        }
                    }

                    if (!blacklisted && SecurityContextHolder.getContext().getAuthentication() == null) {
                        LoginUser loginUser = jwtTokenUtil.toLoginUser(claims);
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        loginUser,
                                        null,
                                        Collections.singletonList(
                                                new SimpleGrantedAuthority("ROLE_" + loginUser.getRoleCode())));
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        // 租户上下文绑定到请求线程，供多租户拦截器注入 SQL 条件
                        TenantContext.setTenantId(loginUser.getTenantId());
                    }
                } catch (JwtException | IllegalArgumentException e) {
                    log.debug("Token 解析失败：{}", e.getMessage());
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            // 线程复用场景下必须清理，避免下一个请求串租户
            TenantContext.clear();
        }
    }
}
