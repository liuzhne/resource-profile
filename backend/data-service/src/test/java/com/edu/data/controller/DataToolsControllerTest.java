package com.edu.data.controller;

import com.edu.common.security.*;
import com.edu.common.util.JwtUtil;
import com.edu.data.service.DashboardService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DataToolsControllerTest {
    JwtUtil jwt;DashboardService service;DataToolsController tools;DashboardController dashboard;
    @BeforeEach void setup(){jwt=mock(JwtUtil.class);service=mock(DashboardService.class);var guard=new AccessGuard(jwt,new InternalCallCredential("internal-secret-at-least-32-characters"));tools=new DataToolsController(service,guard);dashboard=new DashboardController(service,guard);when(jwt.getSubject("tok")).thenReturn("7");}
    @AfterEach void cleanup(){RequestContextHolder.resetRequestAttributes();}
    void request(String role,List<String> permissions,String path){when(jwt.parseRoles("tok")).thenReturn(Set.of(role));when(jwt.parseToken("tok")).thenReturn(Jwts.claims().add("permissions",permissions).build());RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest("GET",path)));}
    @Test void staffWithDataReadCanSearchReceiveNotificationsAndTodos(){request("teacher",List.of("data:read"),"/data/search");assertThat(tools.search("张某",10,"Bearer tok").getCode()).isEqualTo(200);verify(service).search("张某",10);assertThat(tools.notifications(10,"Bearer tok").getCode()).isEqualTo(200);assertThat(dashboard.todos(10,"Bearer tok").getCode()).isEqualTo(200);verify(service,times(2)).getTodos(10);}
    @Test void studentCannotGainStaffAccessByPermissionClaim(){request("student",List.of("data:read"),"/data/search");assertThat(tools.search("张某",10,"Bearer tok").getCode()).isEqualTo(403);assertThat(tools.notifications(10,"Bearer tok").getCode()).isEqualTo(403);assertThat(dashboard.todos(10,"Bearer tok").getCode()).isEqualTo(403);verifyNoInteractions(service);}
    @Test void staffWithoutRequestPermissionAndAnonymousFailClosed(){request("teacher",List.of("student:read"),"/data/search");assertThat(tools.search("张某",10,"Bearer tok").getCode()).isEqualTo(403);assertThat(tools.notifications(10,"Bearer tok").getCode()).isEqualTo(403);assertThat(dashboard.todos(10,"Bearer tok").getCode()).isEqualTo(403);assertThat(tools.search("张某",10,null).getCode()).isEqualTo(403);verifyNoInteractions(service);}
    @Test void administratorMaintainsBypassButCanonicalRoleIsStillRequired(){request("admin",List.of(),"/data/search");assertThat(tools.search("张某",10,"Bearer tok").getCode()).isEqualTo(200);assertThat(tools.notifications(10,"Bearer tok").getCode()).isEqualTo(200);assertThat(dashboard.todos(10,"Bearer tok").getCode()).isEqualTo(200);}
}
