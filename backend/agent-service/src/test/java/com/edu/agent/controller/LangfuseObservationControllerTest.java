package com.edu.agent.controller;

import com.edu.agent.dto.LangfuseObservationPage;
import com.edu.agent.service.LangfuseObservationService;
import com.edu.common.exception.BusinessException;
import com.edu.common.security.AccessGuard;
import com.edu.common.security.InternalCallCredential;
import com.edu.common.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LangfuseObservationControllerTest {
    private static final String AUTH = "Bearer fixture-jwt";
    private static final String INTERNAL = "fixture-internal-secret-at-least-32-characters";
    private LangfuseObservationService service;
    private JwtUtil jwt;
    private LangfuseObservationController controller;

    @BeforeEach void init() {
        service = mock(LangfuseObservationService.class); jwt = mock(JwtUtil.class);
        controller = new LangfuseObservationController(new AccessGuard(jwt, new InternalCallCredential(INTERNAL)), service);
    }
    @AfterEach void cleanup() { RequestContextHolder.resetRequestAttributes(); }

    @Test void administratorCanReadExactProjectedPage() {
        when(jwt.parseRoles("fixture-jwt")).thenReturn(Set.of("admin"));
        var page = new LangfuseObservationPage(List.of(), null, false, "2026-10-07T00:00:00Z", "2026-10-08T00:00:00Z");
        when(service.observations("from", "to", "20", "cursor", null)).thenReturn(page);
        var result = controller.observations("from", "to", "20", "cursor", null, AUTH);
        assertEquals(200, result.getCode()); assertSame(page, result.getData());
        verify(service).observations("from", "to", "20", "cursor", null);
    }

    @Test void administratorCanQueryOneTraceWithoutElevatingOtherRoles() {
        when(jwt.parseRoles("fixture-jwt")).thenReturn(Set.of("admin"));
        var page = new LangfuseObservationPage(List.of(), null, false, "2026-10-07T00:00:00Z", "2026-10-08T00:00:00Z");
        when(service.observations(null, null, "20", null, "safe-trace-id")).thenReturn(page);
        assertSame(page, controller.observations(null, null, "20", null, "safe-trace-id", AUTH).getData());
        verify(service).observations(null, null, "20", null, "safe-trace-id");
        when(jwt.parseRoles("fixture-jwt")).thenReturn(Set.of("student"));
        assertEquals(403, controller.observations(null, null, "20", null, "safe-trace-id", AUTH).getCode());
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"student", "teacher", "counselor", "academic_advisor", "psychologist"})
    void everyNonAdminRoleIsDeniedBeforeServiceOrNetwork(String role) {
        when(jwt.parseRoles("fixture-jwt")).thenReturn(Set.of(role));
        assertEquals(403, controller.observations(null, null, null, null, null, AUTH).getCode());
        verifyNoInteractions(service);
    }

    @Test void anonymousInvalidBearerAndInternalOnlyCannotReadEvenWhenIngressFilterIsDisabled() {
        var request = new MockHttpServletRequest(); request.addHeader(InternalCallCredential.HEADER, INTERNAL);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(jwt.parseRoles("fixture-jwt")).thenReturn(Set.of());
        for (String auth : new String[]{null, "", "Basic fixture", AUTH})
            assertEquals(403, controller.observations(null, null, null, null, null, auth).getCode());
        verifyNoInteractions(service);
    }

    @Test void internalCredentialDoesNotElevateAStudentJwt() {
        var request = new MockHttpServletRequest(); request.addHeader(InternalCallCredential.HEADER, INTERNAL);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(jwt.parseRoles("fixture-jwt")).thenReturn(Set.of("student"));
        assertEquals(403, controller.observations(null, null, null, null, null, AUTH).getCode()); verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(ints = {400, 429, 502, 503, 504})
    void safeServiceFailureUsesResultCodeWithoutData(int code) {
        when(jwt.parseRoles("fixture-jwt")).thenReturn(Set.of("admin"));
        when(service.observations(null, null, null, null, null)).thenThrow(new BusinessException(code, "固定安全提示"));
        var result = controller.observations(null, null, null, null, null, AUTH);
        assertEquals(code, result.getCode()); assertEquals("固定安全提示", result.getMessage()); assertNull(result.getData());
    }

    @Test void mappedEndpointUsesExactQueryContractAndAdminGate() throws Exception {
        when(jwt.parseRoles("fixture-jwt")).thenReturn(Set.of("admin"));
        when(service.observations("2026-10-07T00:00:00Z", "2026-10-08T00:00:00Z", "20", "abc+/==", null))
                .thenReturn(new LangfuseObservationPage(List.of(), null, false, "2026-10-07T00:00:00Z", "2026-10-08T00:00:00Z"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/agent/api/v1/observability/observations").header("Authorization", AUTH)
                        .param("from", "2026-10-07T00:00:00Z").param("to", "2026-10-08T00:00:00Z")
                        .param("limit", "20").param("cursor", "abc+/=="))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray()).andExpect(jsonPath("$.data.hasMore").value(false));
        verify(service).observations("2026-10-07T00:00:00Z", "2026-10-08T00:00:00Z", "20", "abc+/==", null);
    }
}
