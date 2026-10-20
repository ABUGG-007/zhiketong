package com.zhiketong.common;

import lombok.Getter;

/**
 * 统一响应码定义。
 *
 * <p>与接口文档保持一致：200 成功、400 参数错误、401 未登录、403 无权限、404 不存在、409 状态冲突、500 服务器错误。</p>
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "状态冲突，请刷新后重试"),
    ERROR(500, "服务器内部错误");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
