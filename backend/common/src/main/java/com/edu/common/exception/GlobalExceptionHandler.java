package com.edu.common.exception;

import com.edu.common.result.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 统一异常处理（A7：下沉 common，全服务自动装配——经 common 的
 * {@code AutoConfiguration.imports} 注册，各服务依赖 common 即得，不再每模块各写一份）。
 *
 * <p>所有 controller 异常归一为 {@link Result}：
 * <ul>
 *   <li>{@link BusinessException} → 其自带错误码（400/401/403/404/429...）；</li>
 *   <li>参数校验类（{@code @Valid @RequestBody} / 绑定 / {@code @RequestParam} 约束）→ 400；</li>
 *   <li>未映射路径（{@link NoResourceFoundException}）→ 404；</li>
 *   <li>其余 {@link RuntimeException} → 500（消息透出）；兜底 {@link Exception} → 500（消息隐藏）。</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)  // 仅 MVC 服务；不进 gateway(WebFlux)
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常 code={} msg={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .findFirst()
                .orElse("参数校验失败");
        return Result.error(400, message);
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .findFirst()
                .orElse("参数绑定失败");
        return Result.error(400, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(jakarta.validation.ConstraintViolation::getMessage)
                .findFirst()
                .orElse("参数校验失败");
        return Result.error(400, message);
    }

    /**
     * 未映射路径 → 404。此前落入兜底 {@link #handleException}：返回「系统错误」并打 ERROR 全量堆栈。
     * 前端唤醒休眠服务的探活请求会打到未引入 actuator 的服务的 {@code /actuator/health}，
     * 每次唤醒都在各服务刷出一条假「系统错误」，干扰真实故障排查。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResourceFound(NoResourceFoundException e) {
        log.debug("资源不存在: {}", e.getResourcePath());
        return Result.error(404, "资源不存在");
    }

    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常: {}", e.getMessage());
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统错误: {}", e.getMessage(), e);
        return Result.error("系统错误，请联系管理员");
    }
}
