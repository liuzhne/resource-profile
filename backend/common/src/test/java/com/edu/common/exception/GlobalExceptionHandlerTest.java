package com.edu.common.exception;

import com.edu.common.result.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
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
    void otherCheckedExceptionsStillFallBackToSystemError() {
        Result<Void> result = handler.handleException(new Exception("boom"));

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMessage()).isEqualTo("系统错误，请联系管理员");
    }
}
