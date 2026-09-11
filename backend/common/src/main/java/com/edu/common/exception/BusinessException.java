package com.edu.common.exception;

import lombok.Getter;

/**
 * 业务异常：带错误码（对齐 {@code Result.error(code, message)} 的语义）。
 *
 * <p>替代各 service 全程裸抛 {@code RuntimeException}（被 {@link GlobalExceptionHandler} 统一兜成 500）。
 * 用法：参数错 {@code new BusinessException(400, "...")}、未授权 401、禁止 403、不存在 404、限流 429。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        this(500, message);
    }
}
