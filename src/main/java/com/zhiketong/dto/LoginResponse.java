package com.zhiketong.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录响应：Token + 用户信息。
 */
@Data
@Builder
public class LoginResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** JWT Token */
    private String token;

    /** Token 类型，固定 Bearer */
    private String tokenType;

    /** 有效期（秒） */
    private Long expiresIn;

    /** 用户信息 */
    private UserVO user;
}
