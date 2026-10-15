package com.edu.auth.controller;

import com.edu.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccountRequestControllerContractTest {
    private JdbcTemplate db; private StringRedisTemplate redis; private ValueOperations<String,String> values; private MockMvc mvc;
    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        db = mock(JdbcTemplate.class); redis = mock(StringRedisTemplate.class); values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        mvc = MockMvcBuilders.standaloneSetup(new AccountRequestController(db, redis)).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    private String request(String name) { return "{\"type\":\"PASSWORD_RECOVERY\",\"username\":\""+name+"\",\"contact\":\"synthetic@example.invalid\",\"reason\":\"这是一份完全合成的账号请求\"}"; }

    @Test void publicRequestsForExistingAndMissingNamesHaveSameResponseAndDoNotQueryAccounts() throws Exception {
        when(values.setIfAbsent(anyString(), eq("1"), eq(60L), eq(TimeUnit.SECONDS))).thenReturn(true);
        for (String username : new String[]{"synthetic-existing", "synthetic-missing"}) {
            mvc.perform(post("/auth/support").contentType(MediaType.APPLICATION_JSON).content(request(username)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data").doesNotExist());
            verify(db).update("INSERT INTO sys_account_request(type,username,contact,reason) VALUES(?,?,?,?)", "PASSWORD_RECOVERY", username, "synthetic@example.invalid", "这是一份完全合成的账号请求");
        }
        // The only JDBC operations are parameterized INSERTs; existence is never checked or disclosed.
        verifyNoMoreInteractions(db);
    }

    @Test void peerLimitIsSharedAndSpoofedForwardedHeadersCannotResetIt() throws Exception {
        var first = new AtomicBoolean(true);
        when(values.setIfAbsent(eq("support-limit:127.0.0.1"), eq("1"), eq(60L), eq(TimeUnit.SECONDS))).thenAnswer(inv -> first.getAndSet(false));
        mvc.perform(post("/auth/support").header("X-Forwarded-For", "192.0.2.1").contentType(MediaType.APPLICATION_JSON).content(request("synthetic")))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(post("/auth/support").header("X-Forwarded-For", "192.0.2.2").contentType(MediaType.APPLICATION_JSON).content(request("synthetic")))
                .andExpect(jsonPath("$.code").value(429));
        verify(db, times(1)).update(anyString(), any(), any(), any(), any());
    }

    @Test void missingRedisConfirmationAndUnavailableRedisNeverWriteSupportRequest() throws Exception {
        mvc.perform(post("/auth/support").contentType(MediaType.APPLICATION_JSON).content(request("synthetic")))
                .andExpect(jsonPath("$.code").value(429));
        when(values.setIfAbsent(anyString(), eq("1"), eq(60L), eq(TimeUnit.SECONDS))).thenThrow(new IllegalStateException("synthetic Redis unavailable"));
        mvc.perform(post("/auth/support").contentType(MediaType.APPLICATION_JSON).content(request("synthetic")))
                .andExpect(jsonPath("$.code").value(500));
        verifyNoInteractions(db);
    }

    @Test void invalidTypeContactAndReasonFailBeforeRateCounterOrDatabase() throws Exception {
        for (String input : new String[]{"{\"type\":\"UNKNOWN\",\"contact\":\"synthetic\",\"reason\":\"这是一份完全合成的账号请求\"}",
                "{\"type\":\"OPEN_ACCOUNT\",\"contact\":\"\",\"reason\":\"这是一份完全合成的账号请求\"}",
                "{\"type\":\"OPEN_ACCOUNT\",\"contact\":\"synthetic\",\"reason\":\"short\"}"}) {
            mvc.perform(post("/auth/support").contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(jsonPath("$.code").value(400));
        }
        verifyNoInteractions(redis, db);
    }
}
