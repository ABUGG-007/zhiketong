package com.zhiketong.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 登录用户上下文（从 JWT 解析后放入 SecurityContext）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long userId;

    /** 登录账号 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 角色编码：ADMIN / AGENT / USER */
    private String roleCode;

    /** JWT 唯一标识，用于登出黑名单 */
    private String tokenId;

    /** Token 过期时间（毫秒时间戳） */
    private Long tokenExpireAt;
}
