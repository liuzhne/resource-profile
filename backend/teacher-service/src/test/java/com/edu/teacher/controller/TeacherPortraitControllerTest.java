package com.edu.teacher.controller;

import com.edu.common.security.*;
import com.edu.common.result.Result;
import com.edu.common.util.JwtUtil;
import com.edu.teacher.dto.*;
import com.edu.teacher.entity.Teacher;
import com.edu.teacher.service.TeacherPortraitService;
import org.junit.jupiter.api.*;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeacherPortraitControllerTest {
    JwtUtil jwt; TeacherPortraitService service; TeacherPortraitController controller;
    @BeforeEach void setup(){jwt=mock(JwtUtil.class);service=mock(TeacherPortraitService.class);controller=new TeacherPortraitController(service,new AccessGuard(jwt,new InternalCallCredential("internal-secret-at-least-32-characters")));when(jwt.getSubject("tok")).thenReturn("7");}
    @AfterEach void cleanup(){RequestContext.clear();}
    void role(String role){when(jwt.parseRoles("tok")).thenReturn(Set.of(role));}
    @Test void staffReadsPortraitButStudentsCannotBrowseTeachers(){role("teacher");assertThat(controller.portrait(1L,"Bearer tok").getCode()).isEqualTo(200);verify(service).portrait(1L);role("student");assertThat(controller.portrait(2L,"Bearer tok").getCode()).isEqualTo(403);verify(service,never()).portrait(2L);assertThat(controller.options("Bearer tok").getCode()).isEqualTo(403);}
    @Test void teacherCannotWriteAnyRecordEvaluationOrBindAccounts(){role("teacher");String auth="Bearer tok";var record=new TeacherRecordRequest();var evaluation=new TeacherEvaluationRequest();assertThat(controller.addRecord(1L,record,auth).getCode()).isEqualTo(403);assertThat(controller.updateRecord(1L,2L,record,auth).getCode()).isEqualTo(403);assertThat(controller.deleteRecord(1L,2L,auth).getCode()).isEqualTo(403);assertThat(controller.addEvaluation(1L,evaluation,auth).getCode()).isEqualTo(403);assertThat(controller.updateEvaluation(1L,2L,evaluation,auth).getCode()).isEqualTo(403);assertThat(controller.deleteEvaluation(1L,2L,auth).getCode()).isEqualTo(403);assertThat(controller.accounts(auth).getCode()).isEqualTo(403);verifyNoInteractions(service);}
    @Test void adminWritesAreScopedToPathIdentifiers(){role("admin");String auth="Bearer tok";var record=new TeacherRecordRequest();var evaluation=new TeacherEvaluationRequest();assertThat(controller.addRecord(1L,record,auth).getCode()).isEqualTo(200);assertThat(controller.updateRecord(1L,2L,record,auth).getCode()).isEqualTo(200);assertThat(controller.deleteRecord(1L,2L,auth).getCode()).isEqualTo(200);assertThat(controller.addEvaluation(1L,evaluation,auth).getCode()).isEqualTo(200);assertThat(controller.updateEvaluation(1L,2L,evaluation,auth).getCode()).isEqualTo(200);assertThat(controller.deleteEvaluation(1L,2L,auth).getCode()).isEqualTo(200);assertThat(controller.accounts(auth).getCode()).isEqualTo(200);verify(service).saveRecord(1L,2L,record);verify(service).saveEvaluation(1L,2L,evaluation);verify(service).deleteRecord(1L,2L);verify(service).deleteEvaluation(1L,2L);}
    @Test void newPoliticalAndContactFieldsAreFilteredByActualResponseAdvice(){var advice=new FieldPermissionAdvice();for(String role:new String[]{"student","teacher","academic_advisor"}){RequestContext.setRoles(Set.of(role));Teacher teacher=teacher();Result<Teacher> body=Result.success(teacher);advice.beforeBodyWrite(body,null,null,null,null,null);assertThat(teacher.getPolitical()).isNull();assertThat(teacher.getPhone()).isNull();assertThat(teacher.getEmail()).isNull();assertThat(teacher.getName()).isEqualTo("公开姓名");}for(String role:new String[]{"admin","counselor","psychologist"}){RequestContext.setRoles(Set.of(role));Teacher teacher=teacher();advice.beforeBodyWrite(Result.success(teacher),null,null,null,null,null);assertThat(teacher.getPolitical()).isEqualTo("群众");assertThat(teacher.getPhone()).isEqualTo("private-contact");}}
    Teacher teacher(){var teacher=new Teacher();teacher.setName("公开姓名");teacher.setPolitical("群众");teacher.setPhone("private-contact");teacher.setEmail("private@example.test");return teacher;}
}
