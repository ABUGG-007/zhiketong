package com.zhiketong.security;

import com.zhiketong.common.Constants;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * WebSocket 握手拦截器：校验 URL 上的 token 参数（或 Authorization 头）。
 *
 * <p>连接示例：ws://localhost:8080/ws/chat?token=xxx</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenUtil jwtTokenUtil;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            Claims claims = jwtTokenUtil.parse(token);
            String jti = claims.getId();

            boolean blacklisted = false;
            try {
                blacklisted = Boolean.TRUE.equals(
                        stringRedisTemplate.hasKey(Constants.TOKEN_BLACKLIST_PREFIX + jti));
            } catch (Exception e) {
                log.warn("WebSocket 握手校验黑名单失败：{}", e.getMessage());
            }
            if (blacklisted) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            LoginUser loginUser = jwtTokenUtil.toLoginUser(claims);
            attributes.put("loginUser", loginUser);
            return true;
        } catch (Exception e) {
            log.debug("WebSocket 握手 Token 无效：{}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // 握手完成后无需处理
    }

    private String resolveToken(ServerHttpRequest request) {
        if (!(request instanceof ServletServerHttpRequest)) {
            return null;
        }
        HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
        String token = servletRequest.getParameter("token");
        if (StringUtils.hasText(token)) {
            return token;
        }
        String header = servletRequest.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(Constants.TOKEN_PREFIX)) {
            return header.substring(Constants.TOKEN_PREFIX.length()).trim();
        }
        return null;
    }
}
