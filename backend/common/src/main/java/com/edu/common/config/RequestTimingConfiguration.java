package com.edu.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import java.io.IOException;
import java.util.Locale;

/** Application processing before body serialization; does not buffer SSE or downloads. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RequestTimingConfiguration {
    private static final String START = RequestTimingConfiguration.class.getName() + ".start";
    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> requestTimingFilter() {
        var registration = new FilterRegistrationBean<OncePerRequestFilter>(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                request.setAttribute(START, System.nanoTime());
                chain.doFilter(request, response);
            }
        });
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
    @Bean
    @ConditionalOnMissingBean(TimingAdvice.class)
    public TimingAdvice requestTimingAdvice() { return new TimingAdvice(); }

    @RestControllerAdvice
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public static class TimingAdvice implements ResponseBodyAdvice<Object> {
        @Override
        public boolean supports(MethodParameter method, Class<? extends HttpMessageConverter<?>> converter) { return true; }
        @Override
        public Object beforeBodyWrite(Object body, MethodParameter method, MediaType mediaType,
                                      Class<? extends HttpMessageConverter<?>> converter,
                                      ServerHttpRequest request, ServerHttpResponse response) {
            if (request instanceof ServletServerHttpRequest servlet) {
                Object started = servlet.getServletRequest().getAttribute(START);
                if (started instanceof Long start) {
                    response.getHeaders().add("Server-Timing", String.format(Locale.ROOT, "app;dur=%.2f", (System.nanoTime() - start) / 1_000_000.0));
                }
            }
            return body;
        }
    }
}
