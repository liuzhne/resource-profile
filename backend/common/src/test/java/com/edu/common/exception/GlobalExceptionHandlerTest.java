package com.edu.common.exception;

import com.edu.common.result.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unmappedPathReturns404NotSystemError() {
        Result<Void> result = handler.handleNoResourceFound(
                new NoResourceFoundException(HttpMethod.GET, "actuator/health"));

        assertThat(result.getCode()).isEqualTo(404);
        assertThat(result.getMessage()).isEqualTo("资源不存在");
    }

    @Test
    void unmappedPathSetsHttpStatus404() throws NoSuchMethodException {
        // 响应体 code 之外还须真正的 HTTP 404；缺了 @ResponseStatus，Spring MVC 默认回 HTTP 200
        ResponseStatus status = GlobalExceptionHandler.class
                .getMethod("handleNoResourceFound", NoResourceFoundException.class)
                .getAnnotation(ResponseStatus.class);

        assertThat(status).isNotNull();
        assertThat(status.value()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void otherCheckedExceptionsStillFallBackToSystemError() {
        Result<Void> result = handler.handleException(new Exception("boom"));

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMessage()).isEqualTo("系统错误，请联系管理员");
    }
}
