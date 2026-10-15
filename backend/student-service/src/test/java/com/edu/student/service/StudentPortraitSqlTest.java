package com.edu.student.service;

import com.edu.common.exception.BusinessException;
import com.edu.student.dto.*;
import com.edu.student.entity.*;
import com.edu.student.mapper.*;
import com.edu.student.service.impl.StudentServiceImpl;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.*;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

class StudentPortraitSqlTest {
    SqlSession session; StudentPortraitService portrait; StudentServiceImpl profiles;
    @BeforeEach void setup() throws Exception {
        session=PortraitSqlSupport.open();var mapper=session.getMapper(StudentMapper.class);
        portrait=new StudentPortraitService(mapper,session.getMapper(StudentQualityRecordMapper.class));profiles=new StudentServiceImpl(mapper);
        PortraitSqlSupport.execute(session,"INSERT INTO sys_user(id,username,password,user_type,status) VALUES (2,'teacher','hash',1,1),(3,'student','hash',2,1),(4,'other_student','hash',2,1),(5,'disabled_student','hash',2,0)");
        PortraitSqlSupport.execute(session,"INSERT INTO student_info(id,user_id,student_id,name,dept_name,major_name,grade,gpa,status) VALUES (1,3,'S1','甲','新学院','专业A','2026',3.00,1),(2,3,'S2','乙','新学院','专业A','2026',4.00,1),(3,3,'S3','丙','新学院','专业A','2026',3.00,1),(4,3,'S4','丁','新学院','专业A','2026',0.00,1),(5,3,'S5','未填','新学院','专业A','2026',NULL,1),(6,3,'S6','他年级','新学院','专业A','2025',4.00,1),(7,3,'S7','他专业','新学院','专业B','2026',4.00,1),(8,3,'S8','毕业','新学院','专业A','2026',4.00,2),(9,3,'S9','已删','旧学院','旧专业','2024',4.00,1)");
        PortraitSqlSupport.execute(session,"UPDATE student_info SET deleted=1 WHERE id=9");
    }
    @AfterEach void close(){if(session!=null){session.rollback();session.close();}}
    @Test void rankingUsesSameDeptMajorAndGradeActiveNonDeletedWithTiesAndZero() {
        var rank=portrait.rank(1L);assertThat(rank.isAvailable()).isTrue();assertThat(rank.getRank()).isEqualTo(2);assertThat(rank.getCohortSize()).isEqualTo(4);
        assertThat(portrait.rank(3L).getRank()).isEqualTo(2);
        assertThat(portrait.rank(4L).getRank()).isEqualTo(4);
        assertThat(portrait.rank(5L).isAvailable()).isFalse();assertThat(portrait.rank(5L).getRank()).isNull();
        assertThat(portrait.rank(8L).isAvailable()).isFalse();
    }
    @Test void dictionariesCoverCurrentYearAndIgnoreDeletedRowsAndAccountOptionsAreBoundedProjection() {
        assertThat(portrait.options().get("grades")).containsExactly("2026","2025");
        assertThat(portrait.options().get("departments")).containsExactly("新学院");
        assertThat(portrait.options().get("majors")).containsExactly("专业A","专业B");
        assertThat(portrait.accounts()).hasSize(2);
        assertThat(portrait.accounts()).allSatisfy(account->assertThat(account.keySet()).containsExactlyInAnyOrder("id","username"));
    }
    @Test void qualityCrudPersistsActualValuesClearsDescriptionAndRejectsCrossProfileWrites() {
        var request=record("COMPETITION","真实竞赛");request.setDescription("原描述");
        var added=portrait.saveRecord(1L,null,request);assertThat(added.getStudentId()).isEqualTo(1);
        request.setTitle("更正竞赛");request.setDescription(null);
        var updated=portrait.saveRecord(1L,added.getId(),request);assertThat(updated.getTitle()).isEqualTo("更正竞赛");assertThat(updated.getDescription()).isNull();
        assertThatThrownBy(()->portrait.saveRecord(2L,added.getId(),request)).isInstanceOf(BusinessException.class).hasMessageContaining("记录不存在");
        portrait.saveRecord(1L,null,record("HONOR","荣誉"));portrait.saveRecord(1L,null,record("VOLUNTEER","志愿服务"));
        assertThat(portrait.records(1L)).hasSize(3);assertThat(portrait.records(2L)).isEmpty();
        portrait.deleteRecord(1L,added.getId());assertThat(portrait.records(1L)).hasSize(2);
        assertThatThrownBy(()->portrait.deleteRecord(2L,added.getId())).isInstanceOf(BusinessException.class);
    }
    @Test void newProfilesKeepUnknownGpaCreditsNullAndExplicitZeroPersists() {
        var request=profile(4L,"S10");Student value=request.validatedEntity();profiles.save(value);
        var stored=profiles.getById(value.getId());assertThat(stored.getGpa()).isNull();assertThat(stored.getCredits()).isNull();
        request.setGpa(java.math.BigDecimal.ZERO);request.setCredits(0);Student update=request.validatedEntity();update.setId(value.getId());profiles.update(update);
        assertThat(profiles.getById(value.getId()).getGpa()).isEqualByComparingTo("0");assertThat(profiles.getById(value.getId()).getCredits()).isZero();
        request.setGpa(null);request.setCredits(null);update=request.validatedEntity();update.setId(value.getId());profiles.update(update);
        assertThat(profiles.getById(value.getId()).getGpa()).isNull();assertThat(profiles.getById(value.getId()).getCredits()).isNull();
    }
    @Test void bindingValidationRejectsNewDuplicatesAndWrongOrDisabledAccountsPreservesExistingLegacyBinding() {
        assertThatThrownBy(()->profiles.save(profile(3L,"S10").validatedEntity())).isInstanceOf(BusinessException.class).hasMessageContaining("关联其他档案");
        assertThatThrownBy(()->profiles.save(profile(2L,"S10").validatedEntity())).isInstanceOf(BusinessException.class).hasMessageContaining("学生账号");
        assertThatThrownBy(()->profiles.save(profile(5L,"S10").validatedEntity())).isInstanceOf(BusinessException.class).hasMessageContaining("学生账号");
        var unchanged=profile(3L,"S2").validatedEntity();unchanged.setId(2L);profiles.update(unchanged);
        assertThat(profiles.getById(2L).getUserId()).isEqualTo(3);
        var newOwner=profile(4L,"S10").validatedEntity();profiles.save(newOwner);
        var rebind=profile(4L,"S2").validatedEntity();rebind.setId(2L);
        assertThatThrownBy(()->profiles.update(rebind)).isInstanceOf(BusinessException.class).hasMessageContaining("关联其他档案");
    }
    @Test void omittedHighBirthDateIsPreservedAndExplicitNullCanClearIt() throws Exception {
        PortraitSqlSupport.execute(session,"UPDATE student_info SET birth_date='2005-01-01' WHERE id=1");
        Student omitted=profile(3L,"S1").validatedEntity(); omitted.setId(1L); profiles.update(omitted);
        assertThat(profiles.getById(1L).getBirthDate()).isEqualTo(LocalDate.of(2005,1,1));
        var json=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var explicit=json.readValue("{\"userId\":3,\"studentId\":\"S1\",\"name\":\"学生\",\"birthDate\":null}",StudentWriteRequest.class);
        assertThat(explicit.isBirthDateProvided()).isTrue();
        Student cleared=explicit.validatedEntity();cleared.setId(1L);profiles.update(cleared);
        assertThat(profiles.getById(1L).getBirthDate()).isNull();
        assertThat(json.writeValueAsString(profiles.getById(1L))).doesNotContain("clearBirthDate");
    }
    @Test void ownershipRowLockSerializesConcurrentCreatesAndRejectsTheSecondBinding() throws Exception {
        // Real independent DB connections: the shared account is locked until the first profile commits.
        session.commit(true);
        var factory=new org.apache.ibatis.session.defaults.DefaultSqlSessionFactory(session.getConfiguration());
        try(var second=factory.openSession(false)) {
            var competing=new StudentServiceImpl(second.getMapper(StudentMapper.class));
            var first=profile(4L,"S10").validatedEntity();profiles.save(first);
            var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
            var entered=new java.util.concurrent.CountDownLatch(1);
            try {
                var future=executor.submit(()-> { entered.countDown();competing.save(profile(4L,"S11").validatedEntity());return true; });
                assertThat(entered.await(2,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(()->future.get(100,java.util.concurrent.TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
                session.commit();
                assertThatThrownBy(()->future.get(2,java.util.concurrent.TimeUnit.SECONDS)).isInstanceOf(java.util.concurrent.ExecutionException.class).hasCauseInstanceOf(BusinessException.class);
                second.rollback();
                assertThat(session.getMapper(StudentMapper.class).countOtherOwners(4L,0L)).isEqualTo(1);
            } finally {executor.shutdownNow();second.rollback();}
        }
    }
    @Test void qualityValidationRejectsMissingDatesAndUnknownKindsAndGpaOutOfRange() {
        var request=record("UNKNOWN","无效");assertThatThrownBy(()->portrait.saveRecord(1L,null,request)).isInstanceOf(BusinessException.class);
        request.setKind("HONOR");request.setEventDate(null);assertThatThrownBy(()->portrait.saveRecord(1L,null,request)).isInstanceOf(BusinessException.class);
        var profile=profile(4L,"S10");profile.setGpa(new java.math.BigDecimal("4.01"));assertThatThrownBy(profile::validatedEntity).isInstanceOf(BusinessException.class);
    }
    private StudentWriteRequest profile(Long userId,String code){var value=new StudentWriteRequest();value.setUserId(userId);value.setStudentId(code);value.setName("测试学生");return value;}
    private StudentQualityRecordRequest record(String kind,String title){var value=new StudentQualityRecordRequest();value.setKind(kind);value.setTitle(title);value.setEventDate(LocalDate.of(2026,10,7));return value;}
}
