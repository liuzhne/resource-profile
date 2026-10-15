package com.edu.agent.controller;
import com.edu.agent.service.AgentGenerationService;
import com.edu.common.security.InternalCallCredential;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class InternalGenerationControllerTest {
    @Test void directEndpointDoesNotRelyOnGatewayForAuthentication() {
        var generation=mock(AgentGenerationService.class);
        var controller=new InternalGenerationController(generation,new InternalCallCredential("secret"),mock(DiagnosticsController.class),"test");
        for(String token:java.util.Arrays.asList(null,"wrong")) {
            assertThatThrownBy(()->controller.generate("risk",token,Map.of("student_profile",Map.of()))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            assertThatThrownBy(()->controller.metrics(token)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        }
        verifyNoInteractions(generation);
        when(generation.analyzeRisk(Map.of("student_profile",Map.of()))).thenReturn("{\"risk_level\":\"low\"}");
        assertThat(controller.generate("risk","secret",Map.of("student_profile",Map.of()))).containsEntry("risk_level","low");
    }
    @Test void absentSecretAndMissingContextNeverCallModel() {
        var generation=mock(AgentGenerationService.class);
        var closed=new InternalGenerationController(generation,new InternalCallCredential(""),mock(DiagnosticsController.class),"test");
        assertThatThrownBy(()->closed.generate("risk","",Map.of())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        var controller=new InternalGenerationController(generation,new InternalCallCredential("secret"),mock(DiagnosticsController.class),"test");
        assertThatThrownBy(()->controller.generate("plan","secret",Map.of("student_profile",Map.of()))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(generation);
    }
    @Test void servletErrorsKeepRealHttpStatusAndDoNotExposeProviderPayload() throws Exception {
        var generation=mock(AgentGenerationService.class);
        var controller=new InternalGenerationController(generation,new InternalCallCredential("secret"),mock(DiagnosticsController.class),"test");
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.edu.common.exception.GlobalExceptionHandler()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/_internal/ai/risk")
                .contentType("application/json").content("{\"student_profile\":{}}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        when(generation.analyzeRisk(org.mockito.ArgumentMatchers.anyMap())).thenThrow(new RuntimeException("private-provider-payload"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/_internal/ai/risk")
                .header("X-Internal-Token","secret").contentType("application/json").content("{\"student_profile\":{}}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isServiceUnavailable())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-provider-payload"))));
    }
}
