package com.edu.teacher.service;

import com.edu.common.exception.BusinessException;
import com.edu.teacher.dto.*;
import com.edu.teacher.entity.*;
import com.edu.teacher.mapper.*;
import com.edu.teacher.service.impl.TeacherServiceImpl;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

class TeacherPortraitSqlTest {
    SqlSession session;
    TeacherPortraitService portrait;
    TeacherServiceImpl profiles;
    @BeforeEach void setup() throws Exception {
        session = PortraitSqlSupport.open();
        var teachers = session.getMapper(TeacherMapper.class);
        profiles = new TeacherServiceImpl(teachers);
        portrait = new TeacherPortraitService(teachers, session.getMapper(TeacherRecordMapper.class), session.getMapper(TeacherEvaluationMapper.class));
        PortraitSqlSupport.execute(session, "INSERT INTO sys_user(id,username,password,user_type,status,email,phone) VALUES (2,'teacher','hash',1,1,'shared-account@example.test','shared-phone'),(3,'student','hash',2,1,NULL,NULL)");
        PortraitSqlSupport.execute(session, "INSERT INTO teacher_info(id,user_id,employee_id,name,dept_name,title,political,phone,email) VALUES (1,2,'T1','教师一','新学院','新职称','群众','personal-one','one@example.test'),(2,2,'T2','教师二','其他学院','教授',NULL,'personal-two','two@example.test'),(3,2,'T3','删除教师','旧学院','旧职称',NULL,NULL,NULL)");
        PortraitSqlSupport.execute(session,"UPDATE teacher_info SET deleted=1 WHERE id=3");
    }
    @AfterEach void close() { if(session != null) { session.rollback(); session.close(); } }
    @Test void contactBelongsToProfileAndOptionsAreActualNonDeletedValues() {
        assertThat(profiles.getById(1L).getPhone()).isEqualTo("personal-one");
        assertThat(profiles.getById(2L).getPhone()).isEqualTo("personal-two");
        assertThat(profiles.getById(1L).getEmail()).isEqualTo("one@example.test");
        assertThat(portrait.options().get("departments")).containsExactly("其他学院","新学院");
        assertThat(portrait.options().get("titles")).containsExactly("教授","新职称");
        assertThat(portrait.accounts()).hasSize(1);
        assertThat(portrait.accounts().get(0).keySet()).containsExactlyInAnyOrder("id","username");
    }
    @Test void emptyMetricsAreUnknownAndZeroEvaluationIsReal() {
        var empty=portrait.portrait(1L);
        assertThat(empty.getEvaluationCount()).isZero();
        assertThat(empty.getEvaluationScore()).isNull(); assertThat(empty.getSatisfaction()).isNull();
        portrait.saveEvaluation(1L,null,evaluation("0",null));
        var one=portrait.portrait(1L);
        assertThat(one.getEvaluationScore()).isEqualByComparingTo("0"); assertThat(one.getSatisfaction()).isNull();
        portrait.saveEvaluation(1L,null,evaluation("100",true));
        portrait.saveEvaluation(1L,null,evaluation("50",false));
        portrait.saveEvaluation(2L,null,evaluation("100",true));
        var three=portrait.portrait(1L);
        assertThat(three.getEvaluationCount()).isEqualTo(3);
        assertThat(three.getEvaluationScore()).isEqualByComparingTo("50.00");
        assertThat(three.getSatisfaction()).isEqualByComparingTo("50.00");
    }
    @Test void recordCrudPersistsClearsAndKeepsOwnerAndDeletionScope() {
        TeacherRecordRequest request=record("TEACHING","真实成果"); request.setDescription("旧说明");
        var added=portrait.saveRecord(1L,null,request);
        assertThat(added.getId()).isNotNull(); assertThat(added.getTeacherId()).isEqualTo(1);
        request.setTitle("已编辑"); request.setDescription(null);
        var updated=portrait.saveRecord(1L,added.getId(),request);
        assertThat(updated.getTitle()).isEqualTo("已编辑"); assertThat(updated.getDescription()).isNull();
        assertThatThrownBy(()->portrait.saveRecord(2L,added.getId(),request)).isInstanceOf(BusinessException.class).hasMessageContaining("记录不存在");
        assertThat(portrait.portrait(1L).getTeaching()).hasSize(1);
        portrait.saveRecord(1L,null,record("RESEARCH","科研项目"));
        assertThat(portrait.portrait(1L).getResearch()).hasSize(1);
        portrait.deleteRecord(1L,added.getId());
        assertThat(portrait.portrait(1L).getTeaching()).isEmpty();
        assertThatThrownBy(()->portrait.deleteRecord(2L,added.getId())).isInstanceOf(BusinessException.class);
    }
    @Test void evaluationUpdateCanClearOptionalAnswersAndDeletionUpdatesMetrics() {
        var added=portrait.saveEvaluation(1L,null,evaluation("80",true));
        var next=evaluation("70",null);next.setComment(null);
        var updated=portrait.saveEvaluation(1L,added.getId(),next);
        assertThat(updated.getSatisfied()).isNull(); assertThat(updated.getComment()).isNull();
        assertThatThrownBy(()->portrait.saveEvaluation(2L,added.getId(),next)).isInstanceOf(BusinessException.class);
        portrait.deleteEvaluation(1L,added.getId());
        assertThat(portrait.portrait(1L).getEvaluationCount()).isZero();
    }
    @Test void mainProfileCrudUsesTypedFieldsAllowsNullClearAndRejectsStudentAccount() {
        TeacherWriteRequest request=new TeacherWriteRequest();request.setUserId(2L);request.setEmployeeId("T4");request.setName("新增");request.setPolitical("群众");request.setPhone("personal-four");request.setEmail("four@example.test");
        Teacher value=request.validatedEntity();profiles.save(value);
        assertThat(profiles.getById(value.getId()).getPolitical()).isEqualTo("群众");
        request.setPolitical(null);request.setPhone(null);request.setEmail(null);
        Teacher update=request.validatedEntity();update.setId(value.getId());profiles.update(update);
        assertThat(profiles.getById(value.getId()).getPhone()).isNull();
        request.setUserId(3L);request.setEmployeeId("T5");
        assertThatThrownBy(()->profiles.save(request.validatedEntity())).isInstanceOf(BusinessException.class).hasMessageContaining("教师账号");
        profiles.delete(value.getId());assertThat(profiles.getById(value.getId())).isNull();
        request.setUserId(2L);request.setEmployeeId("T4");
        assertThatThrownBy(()->profiles.save(request.validatedEntity())).isInstanceOf(BusinessException.class).hasMessageContaining("档案编号已存在");
    }
    @Test void evaluationValidationRejectsMissingAndOutOfRangeScoresAndInvalidRecordKinds() {
        assertThatThrownBy(()->portrait.saveEvaluation(1L,null,evaluation("101",true))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->portrait.saveEvaluation(1L,null,evaluation("-1",true))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->portrait.saveRecord(1L,null,record("FAKE","无效"))).isInstanceOf(BusinessException.class);
    }
    private TeacherRecordRequest record(String kind,String title) { var value=new TeacherRecordRequest();value.setKind(kind);value.setTitle(title);value.setEventDate(LocalDate.of(2026,10,7));return value; }
    private TeacherEvaluationRequest evaluation(String score,Boolean satisfied) { var value=new TeacherEvaluationRequest();value.setTitle("真实评价");value.setEventDate(LocalDate.of(2026,10,7));value.setScore(new BigDecimal(score));value.setSatisfied(satisfied);value.setComment("原评价");return value; }
}
