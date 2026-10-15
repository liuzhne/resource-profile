package com.edu.agent.sse;

import com.edu.agent.security.AgentSelfAuthFilter;
import com.edu.agent.security.AgentSessionVerifier;
import com.edu.common.config.JwtProperties;
import com.edu.common.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MVC emitter + issued JWT fixture: deny handshakes and prevent sends after session changes. */
class WarningSseSecurityTest {
    private static final String PATH = "/agent/api/v1/warning/stream";
    private JwtProperties properties;
    private JwtUtil jwt;
    private ValueOperations<String, String> values;
    private AgentSessionVerifier sessions;
    private final Map<String, String> whitelist = new ConcurrentHashMap<>();
    private SseEmitterRegistry registry;
    private MockMvc mvc;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        whitelist.clear();
        properties = new JwtProperties();
        properties.setSecret("test-only-sse-key-with-at-least-32-bytes");
        properties.setAccessTokenExpire(60_000L);
        jwt = spy(new JwtUtil(properties));
        jwt.init();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(invocation -> whitelist.get(invocation.getArgument(0)));
        sessions = new AgentSessionVerifier(jwt, redis);
        registry = new SseEmitterRegistry(sessions);
        WarningSseController controller = new WarningSseController(registry, sessions);
        ReflectionTestUtils.setField(controller, "timeoutMillis", 60_000L);
        AgentSelfAuthFilter filter = new AgentSelfAuthFilter(sessions, new ObjectMapper().findAndRegisterModules());
        ReflectionTestUtils.setField(filter, "enabled", true);
        mvc = MockMvcBuilders.standaloneSetup(controller).addFilter(filter).build();
    }

    private String live(String role, List<String> permissions) {
        String token = jwt.generateAccessToken("7", Map.of("roles", List.of(role), "permissions", permissions));
        whitelist.put("token:7", token);
        return token;
    }

    private MvcResult open(String token) throws Exception {
        return mvc.perform(get(PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(request().asyncStarted())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"))
                .andReturn();
    }

    @Test void studentBearerAndQueryTokenReturn403WithoutHelloOrRegistration() throws Exception {
        String student = live("student", List.of("agent:read"));
        mvc.perform(get(PATH).header("Authorization", "Bearer " + student)).andExpect(status().isForbidden())
                .andExpect(request().asyncNotStarted()).andExpect(content().string(""));
        mvc.perform(get(PATH).param("token", student)).andExpect(status().isForbidden())
                .andExpect(request().asyncNotStarted()).andExpect(content().string(""));
        registry.broadcast("warning", Map.of("taskId", 9, "studentId", 175));
        assertThat(registry.size()).isZero();
    }

    @Test void staffMissingPermissionReturns403AndAdminIsAllowed() throws Exception {
        String teacher = live("teacher", List.of("student:read"));
        mvc.perform(get(PATH).header("Authorization", "Bearer " + teacher)).andExpect(status().isForbidden());
        MvcResult admin = open(live("admin", List.of()));
        assertThat(admin.getResponse().getContentAsString()).contains("event:hello");
        assertThat(registry.size()).isEqualTo(1);
        whitelist.clear();
        registry.heartbeat();
    }

    @Test void validTeacherReceivesWarningsButNoNewEventAfterLogout() throws Exception {
        String token = live("teacher", List.of("agent:read"));
        MvcResult connection = open(token);
        registry.broadcast("warning", Map.of("taskId", 9));
        assertThat(connection.getResponse().getContentAsString()).contains("event:warning", "\"taskId\":9");
        String beforeLogout = connection.getResponse().getContentAsString();
        whitelist.clear(); // same auth-service logout/permission-edit revocation operation
        registry.broadcast("warning", Map.of("taskId", 10, "studentId", 175));
        assertThat(connection.getResponse().getContentAsString()).isEqualTo(beforeLogout);
        assertThat(registry.size()).isZero();
        mvc.perform(get(PATH).header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test void validTeacherQueryTokenReceivesHelloAndHeartbeat() throws Exception {
        String token = live("teacher", List.of("agent:read"));
        MvcResult connection = mvc.perform(get(PATH).param("token", token))
                .andExpect(status().isOk()).andExpect(request().asyncStarted()).andReturn();
        registry.heartbeat();
        assertThat(connection.getResponse().getContentAsString()).contains("event:hello", ":ping");
        whitelist.clear();
        registry.heartbeat();
    }

    @Test void idleConnectionIsClosedAtHeartbeatAfterSessionRotation() throws Exception {
        String token = live("teacher", List.of("agent:read"));
        MvcResult connection = open(token);
        String before = connection.getResponse().getContentAsString();
        live("student", List.of()); // role changed/re-login replaced the original access session
        registry.heartbeat();
        assertThat(registry.size()).isZero();
        assertThat(connection.getResponse().getContentAsString()).isEqualTo(before);
    }

    @Test void absoluteJwtExpiryClosesExistingConnectionBeforeAnEvent() throws Exception {
        String token = live("teacher", List.of("agent:read"));
        MvcResult connection = open(token);
        String before = connection.getResponse().getContentAsString();
        doThrow(new io.jsonwebtoken.ExpiredJwtException(null, null, "expired"))
                .when(jwt).parseToken(token);
        registry.broadcast("warning", Map.of("taskId", 10));
        assertThat(registry.size()).isZero();
        assertThat(connection.getResponse().getContentAsString()).isEqualTo(before);
    }

    @Test void redisOutageClosesConnectedStreamsAndDeniesNewHandshake() throws Exception {
        String token = live("teacher", List.of("agent:read"));
        MvcResult connection = open(token);
        String before = connection.getResponse().getContentAsString();
        when(values.get(anyString())).thenThrow(new IllegalStateException("private-redis-detail"));
        registry.broadcast("warning", Map.of("taskId", 10));
        assertThat(registry.size()).isZero();
        assertThat(connection.getResponse().getContentAsString()).isEqualTo(before);
        mvc.perform(get(PATH).header("Authorization", "Bearer " + token)).andExpect(status().isServiceUnavailable());
    }

    @Test void anonymousAndExpiredCannotOpenAStream() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized()).andExpect(request().asyncNotStarted());
        properties.setAccessTokenExpire(0L);
        String expired = live("admin", List.of());
        mvc.perform(get(PATH).param("token", expired)).andExpect(status().isUnauthorized());
        assertThat(registry.size()).isZero();
    }

    @Test void controllerStillRejectsWhenOuterFilterIsDisabled() throws Exception {
        WarningSseController controller = new WarningSseController(registry, sessions);
        MockMvc direct = MockMvcBuilders.standaloneSetup(controller).build();
        direct.perform(get(PATH)).andExpect(status().isUnauthorized()).andExpect(request().asyncNotStarted());
        String student = live("student", List.of("agent:read"));
        direct.perform(get(PATH).param("token", student)).andExpect(status().isForbidden()).andExpect(request().asyncNotStarted());
        assertThat(registry.size()).isZero();
    }
}
