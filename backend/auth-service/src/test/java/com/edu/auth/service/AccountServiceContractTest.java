package com.edu.auth.service;

import com.edu.auth.dto.PasswordChangeRequest;
import com.edu.auth.dto.ProfileUpdateRequest;
import com.edu.auth.entity.User;
import com.edu.auth.mapper.UserMapper;
import com.edu.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AccountServiceContractTest {
    private UserMapper users; private PasswordEncoder encoder; private StringRedisTemplate redis; private AccountService service;
    @BeforeEach void setup() {
        users=mock(UserMapper.class); encoder=mock(PasswordEncoder.class); redis=mock(StringRedisTemplate.class);
        service=new AccountService(users,encoder,redis);
        var user=new User(); user.setId(42L); user.setPassword("synthetic-old-hash"); user.setStatus(1);
        when(users.selectById(42L)).thenReturn(user);
        when(encoder.matches("synthetic-old-password", "synthetic-old-hash")).thenReturn(true);
        when(encoder.encode(anyString())).thenReturn("synthetic-new-hash");
        when(users.changePassword(42L,"synthetic-old-hash","synthetic-new-hash")).thenReturn(1);
        when(redis.delete(List.of("token:42","refresh:42"))).thenReturn(2L);
    }
    private PasswordChangeRequest password(String old, String fresh) { var r=new PasswordChangeRequest();r.setOldPassword(old);r.setNewPassword(fresh);return r; }

    @Test void profileUsesOnlyFourEditableColumnsForSpecifiedSessionOwner() {
        var request=new ProfileUpdateRequest();request.setNickname("合成昵称");request.setEmail("synthetic@example.invalid");request.setPhone("+60123456789");request.setBio("合成简介");
        when(users.updateProfile(42L,"合成昵称","synthetic@example.invalid","+60123456789","合成简介")).thenReturn(1);
        service.update(42L,request);
        verify(users).updateProfile(42L,"合成昵称","synthetic@example.invalid","+60123456789","合成简介");
        verifyNoInteractions(redis);
    }
    @Test void disabledOrMissingProfileCannotReportSaved() {
        assertThatThrownBy(() -> service.update(42L,new ProfileUpdateRequest())).isInstanceOfSatisfying(BusinessException.class,e -> assertThat(e.getCode()).isEqualTo(404));
    }
    @Test void originalPasswordMustMatchBeforeAnyPasswordOrSessionMutation() {
        assertThatThrownBy(() -> service.changePassword(42L,password("wrong","synthetic-new-password"))).hasMessage("原密码不正确");
        verify(users,never()).changePassword(anyLong(),anyString(),anyString());verify(encoder,never()).encode(anyString());verifyNoInteractions(redis);
    }
    @Test void changedOrUnchangedPasswordCannotOverwriteConcurrentHashOrRevokeSession() {
        assertThatThrownBy(() -> service.changePassword(42L,password("synthetic-old-password","synthetic-old-password"))).hasMessageContaining("相同");
        when(users.changePassword(42L,"synthetic-old-hash","synthetic-new-hash")).thenReturn(0);
        assertThatThrownBy(() -> service.changePassword(42L,password("synthetic-old-password","synthetic-new-password"))).isInstanceOfSatisfying(BusinessException.class,e -> assertThat(e.getCode()).isEqualTo(409));
        verifyNoInteractions(redis);
    }
    @Test void utf8PasswordLengthIsCappedAtBcryptByteBoundary() {
        assertThatThrownBy(() -> service.changePassword(42L,password("synthetic-old-password","合".repeat(25)))).hasMessageContaining("72字节");
        verify(users,never()).changePassword(anyLong(),anyString(),anyString());
        service.changePassword(42L,password("synthetic-old-password","合".repeat(24)));
        verify(redis).delete(List.of("token:42","refresh:42"));
    }
    @Test void successfulPasswordChangeRevokesBothSessionsAndZeroMeansAlreadyRevoked() {
        when(redis.delete(List.of("token:42","refresh:42"))).thenReturn(0L);
        service.changePassword(42L,password("synthetic-old-password","synthetic-new-password"));
        verify(redis).delete(List.of("token:42","refresh:42"));
    }
}
