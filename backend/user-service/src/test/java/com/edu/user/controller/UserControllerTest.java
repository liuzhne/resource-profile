package com.edu.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.result.Result;
import com.edu.common.security.AccessGuard;
import com.edu.common.util.JwtUtil;
import com.edu.user.entity.User;
import com.edu.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerTest {

    private static final String AUTH = "Bearer tok";

    private UserService userService;
    private JwtUtil jwtUtil;
    private UserController controller;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        jwtUtil = mock(JwtUtil.class);
        controller = new UserController(userService, new AccessGuard(jwtUtil));
        when(jwtUtil.getSubject("tok")).thenReturn("7");
    }

    private void asRole(String role) {
        when(jwtUtil.parseRoles("tok")).thenReturn(Set.of(role));
    }

    @Test
    void list_adminAllowed() {
        asRole("admin");
        when(userService.list(1, 10, null, null, null)).thenReturn(new Page<>());

        Result<Page<User>> result = controller.list(1, 10, null, null, null, AUTH);

        assertThat(result.getCode()).isEqualTo(200);
        verify(userService).list(1, 10, null, null, null);
    }

    @Test
    void list_studentDeniedWithoutServiceCall() {
        asRole("student");

        Result<Page<User>> result = controller.list(1, 10, null, null, null, AUTH);

        assertThat(result.getCode()).isEqualTo(403);
        verify(userService, never()).list(any(), any(), any(), any(), any());
    }

    @Test
    void get_internalAllowed() {
        when(userService.getById(3L)).thenReturn(new User());
        assertThat(controller.getById(3L, null).getCode()).isEqualTo(200);
        verify(userService).getById(3L);
    }

    @Test
    void save_adminAllowed() {
        asRole("admin");
        User user = new User();
        assertThat(controller.save(user, AUTH).getCode()).isEqualTo(200);
        verify(userService).save(user);
    }

    @Test
    void update_adminAllowedAndUsesPathId() {
        asRole("admin");
        User user = new User();
        assertThat(controller.update(9L, user, AUTH).getCode()).isEqualTo(200);
        assertThat(user.getId()).isEqualTo(9L);
        verify(userService).update(user);
    }

    @Test
    void delete_adminAllowed() {
        asRole("admin");
        assertThat(controller.delete(9L, AUTH).getCode()).isEqualTo(200);
        verify(userService).delete(9L);
    }
}
