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

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(jwtProperties.getHeader());
        String prefix = jwtProperties.getPrefix();
        if (StringUtils.hasText(header) && header.startsWith(prefix)) {
            String token = header.substring(prefix.length()).trim();
            try {
                Claims claims = jwtTokenUtil.parse(token);
                String jti = claims.getId();

                boolean blacklisted = false;
                try {
                    blacklisted = Boolean.TRUE.equals(
                            stringRedisTemplate.hasKey(Constants.TOKEN_BLACKLIST_PREFIX + jti));
                } catch (Exception redisException) {
                    // Redis 不可用时不影响接口鉴权主流程
                    log.warn("校验 Token 黑名单失败：{}", redisException.getMessage());
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
                }
            } catch (JwtException | IllegalArgumentException e) {
                log.debug("Token 解析失败：{}", e.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }
}
