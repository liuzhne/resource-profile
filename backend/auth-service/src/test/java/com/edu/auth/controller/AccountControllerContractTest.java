package com.edu.auth.controller;

import com.edu.auth.dto.ProfileUpdateRequest;
import com.edu.auth.dto.UserInfoResponse;
import com.edu.auth.service.AccountService;
import com.edu.auth.service.AuthService;
import com.edu.common.exception.BusinessException;
import com.edu.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccountControllerContractTest {
    private AuthService auth; private AccountService accounts; private MockMvc mvc;
    @BeforeEach void setup() {
        auth = mock(AuthService.class); accounts = mock(AccountService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AccountController(auth, accounts)).setControllerAdvice(new GlobalExceptionHandler()).build();
        when(auth.requireSession("synthetic-access")).thenReturn(42L);
        var info = new UserInfoResponse(); info.setId(42L); info.setEmail("synthetic@example.invalid"); when(auth.getUserInfo(42L)).thenReturn(info);
    }

    @Test void profileIdentityOnlyComesFromSessionAndMassAssignmentFieldsCannotReachService() throws Exception {
        mvc.perform(put("/auth/profile").header("Authorization", "Bearer synthetic-access").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":999,\"roles\":[\"admin\"],\"userType\":0,\"status\":1,\"nickname\":\"合成昵称\",\"email\":\"synthetic@example.invalid\",\"phone\":\"+60123456789\",\"bio\":\"合成简介\"}"))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.id").value(42));
        var profile = ArgumentCaptor.forClass(ProfileUpdateRequest.class);
        verify(accounts).update(eq(42L), profile.capture());
        assertThat(profile.getValue().getNickname()).isEqualTo("合成昵称");
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(profile.getValue())).doesNotContain("999", "roles", "userType", "status");
        verify(auth).getUserInfo(42L);
    }

    @Test void staleSessionCannotUpdateProfileOrPassword() throws Exception {
        when(auth.requireSession("synthetic-access")).thenThrow(new BusinessException(401, "会话已撤销"));
        mvc.perform(put("/auth/profile").header("Authorization", "Bearer synthetic-access").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.code").value(401));
        mvc.perform(post("/auth/password").header("Authorization", "Bearer synthetic-access").contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldPassword\":\"synthetic-old-password\",\"newPassword\":\"synthetic-new-password\"}"))
                .andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(accounts);
    }

    @Test void invalidProfileAndShortPasswordFailBeforeBusinessMutation() throws Exception {
        mvc.perform(put("/auth/profile").header("Authorization", "Bearer synthetic-access").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"invalid-email\"}")).andExpect(jsonPath("$.code").value(400));
        mvc.perform(post("/auth/password").header("Authorization", "Bearer synthetic-access").contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldPassword\":\"synthetic-old-password\",\"newPassword\":\"short\"}"))
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(accounts);
    }

    @Test void successfulPasswordChangeClearsPersistentRefreshCookie() throws Exception {
        var result = mvc.perform(post("/auth/password").secure(true).header("Authorization", "Bearer synthetic-access").contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldPassword\":\"synthetic-old-password\",\"newPassword\":\"synthetic-new-password\"}"))
                .andExpect(jsonPath("$.code").value(200)).andReturn();
        verify(accounts).changePassword(eq(42L), any());
        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0", "HttpOnly", "Secure", "SameSite=Strict");
    }
}
