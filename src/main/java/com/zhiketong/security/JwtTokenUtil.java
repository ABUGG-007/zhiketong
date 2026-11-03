package com.zhiketong.security;

import com.zhiketong.config.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 工具：生成、解析、校验 Token。
 */
@Component
@RequiredArgsConstructor
public class JwtTokenUtil {

    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_NICKNAME = "nickname";
    public static final String CLAIM_ROLE_CODE = "roleCode";
    public static final String CLAIM_TENANT_ID = "tenantId";

    private final JwtProperties jwtProperties;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(keyBytes, SignatureAlgorithm.HS256.getJcaName());
    }

    /**
     * 生成 Token。
     *
     * @param tenantId 租户 ID，平台管理员传 0
     */
    public JwtToken generate(Long userId, String username, String nickname, String roleCode, Long tenantId) {
        long now = System.currentTimeMillis();
        long expireAt = now + jwtProperties.getExpireHours() * 60 * 60 * 1000L;
        String tokenId = UUID.randomUUID().toString().replace("-", "");

        String token = Jwts.builder()
                .setId(tokenId)
                .setSubject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_NICKNAME, nickname)
                .claim(CLAIM_ROLE_CODE, roleCode)
                .claim(CLAIM_TENANT_ID, tenantId == null ? TenantContext.DEFAULT_TENANT_ID : tenantId)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(expireAt))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();

        return new JwtToken(token, tokenId, expireAt, jwtProperties.getExpireHours() * 3600L);
    }

    /**
     * 解析 Token，失败抛出 JwtException。
     */
    public Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 将 Claims 转换为登录用户。
     */
    public LoginUser toLoginUser(Claims claims) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(Long.valueOf(claims.getSubject()));
        loginUser.setUsername(claims.get(CLAIM_USERNAME, String.class));
        loginUser.setNickname(claims.get(CLAIM_NICKNAME, String.class));
        loginUser.setRoleCode(claims.get(CLAIM_ROLE_CODE, String.class));
        Object tenantClaim = claims.get(CLAIM_TENANT_ID);
        // 旧 Token 未携带租户声明时按默认租户处理，保证升级期间已登录用户不被强制下线
        loginUser.setTenantId(tenantClaim == null
                ? TenantContext.DEFAULT_TENANT_ID : Long.valueOf(String.valueOf(tenantClaim)));
        loginUser.setTokenId(claims.getId());
        loginUser.setTokenExpireAt(claims.getExpiration() == null ? null : claims.getExpiration().getTime());
        return loginUser;
    }

    /**
     * 校验 Token 是否有效（签名 + 过期）。
     */
    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
