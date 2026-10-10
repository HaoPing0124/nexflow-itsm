package com.nexflow.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    UNAUTHORIZED(401, "未认证或登录已过期"),
    FORBIDDEN(403, "没有权限执行该操作"),
    NOT_FOUND(404, "资源不存在"),
    BAD_REQUEST(400, "请求参数错误"),
    ILLEGAL_TRANSITION(400, "非法的状态流转"),
    VALIDATION_ERROR(400, "参数校验失败"),
    CONFLICT(409, "数据已被他人修改，请刷新后重试");

    private final int code;
    private final String defaultMessage;
}
