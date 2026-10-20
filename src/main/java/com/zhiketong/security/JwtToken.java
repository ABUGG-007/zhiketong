package com.zhiketong.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 生成 Token 后的返回信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JwtToken implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 压缩后的 JWT 字符串 */
    private String token;

    /** JWT 唯一标识 */
    private String tokenId;

    /** 过期时间（毫秒时间戳） */
    private Long expireAt;

    /** 有效期（秒） */
    private Long expiresInSeconds;
}
