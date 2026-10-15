package com.edu.student.controller;

import com.edu.common.security.*;
import com.edu.common.util.JwtUtil;
import com.edu.student.dto.*;
import com.edu.student.entity.Student;
import com.edu.student.service.*;
import org.junit.jupiter.api.*;
import java.time.LocalDate;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentPortraitControllerTest {
    JwtUtil jwt;StudentPortraitService service;StudentPortraitController controller;
    @BeforeEach void setup(){jwt=mock(JwtUtil.class);service=mock(StudentPortraitService.class);controller=new StudentPortraitController(service,new AccessGuard(jwt,new InternalCallCredential("internal-secret-at-least-32-characters")));Student owner=new Student();owner.setUserId(7L);when(service.requireStudent(1L)).thenReturn(owner);when(jwt.getSubject("tok")).thenReturn("7");}
    void role(String role){when(jwt.parseRoles("tok")).thenReturn(Set.of(role));}
    @Test void selfReadsButCannotWriteOrBrowseBindingOptions(){role("student");String auth="Bearer tok";assertThat(controller.rank(1L,auth).getCode()).isEqualTo(200);assertThat(controller.records(1L,auth).getCode()).isEqualTo(200);assertThat(controller.options(auth).getCode()).isEqualTo(403);assertThat(controller.accounts(auth).getCode()).isEqualTo(403);var request=new StudentQualityRecordRequest();assertThat(controller.addRecord(1L,request,auth).getCode()).isEqualTo(403);assertThat(controller.updateRecord(1L,2L,request,auth).getCode()).isEqualTo(403);assertThat(controller.deleteRecord(1L,2L,auth).getCode()).isEqualTo(403);verify(service,never()).saveRecord(any(),any(),any());verify(service,never()).deleteRecord(any(),any());}
    @Test void differentStudentAndAnonymousAreDeniedWithoutFetchingRecords(){role("student");when(jwt.getSubject("tok")).thenReturn("9");assertThat(controller.rank(1L,"Bearer tok").getCode()).isEqualTo(403);assertThat(controller.records(1L,null).getCode()).isEqualTo(403);verify(service,never()).rank(1L);verify(service,never()).records(1L);}
    @Test void teacherWritesQualityAtPathAndStaffReadersCannotWrite(){role("teacher");var request=new StudentQualityRecordRequest();assertThat(controller.addRecord(1L,request,"Bearer tok").getCode()).isEqualTo(200);assertThat(controller.updateRecord(1L,2L,request,"Bearer tok").getCode()).isEqualTo(200);assertThat(controller.deleteRecord(1L,2L,"Bearer tok").getCode()).isEqualTo(200);verify(service).saveRecord(1L,null,request);verify(service).saveRecord(1L,2L,request);verify(service).deleteRecord(1L,2L);role("counselor");assertThat(controller.records(1L,"Bearer tok").getCode()).isEqualTo(200);assertThat(controller.addRecord(1L,request,"Bearer tok").getCode()).isEqualTo(403);}
    @Test void teacherProfileWriterCannotSetHighBirthDate(){role("teacher");var profiles=mock(StudentService.class);var main=new StudentController(profiles,new AccessGuard(jwt,new InternalCallCredential("internal-secret-at-least-32-characters")));var request=new StudentWriteRequest();request.setBirthDate(LocalDate.of(2005,1,1));assertThat(main.save(request,"Bearer tok").getCode()).isEqualTo(403);assertThat(main.update(1L,request,"Bearer tok").getCode()).isEqualTo(403);verifyNoInteractions(profiles);}
}
