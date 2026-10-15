package com.edu.mental.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.edu.common.exception.BusinessException;
import com.edu.mental.config.MyBatisPlusConfig;
import com.edu.mental.dto.*;
import com.edu.mental.entity.*;
import com.edu.mental.mapper.*;
import com.edu.mental.service.MentalManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual production MyBatis mappers and calculator against an isolated relational database. */
class MentalContractSqlTest {
    SqlSession session; org.apache.ibatis.session.SqlSessionFactory sqlFactory; javax.sql.DataSource dataSource; MentalAssessmentServiceImpl assessments; QuestionServiceImpl questions;
    QuestionnaireServiceImpl questionnaires; MentalManagementService management;
    @BeforeEach void setUp() throws Exception {
        var source=new UnpooledDataSource("org.h2.Driver","jdbc:h2:mem:mental_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_UPPER=false","sa","");
        var config=new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        for(var mapper:List.of(QuestionMapper.class,QuestionnaireMapper.class,MentalResponseMapper.class,MentalAssessmentMapper.class,StudentLookupMapper.class)) config.addMapper(mapper);
        var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(source);factory.setConfiguration(config);
        factory.setPlugins(new MyBatisPlusConfig().mybatisPlusInterceptor());
        sqlFactory=factory.getObject(); dataSource=source; session=sqlFactory.openSession(false);
        try(var s=session.getConnection().createStatement()) {
            s.execute("CREATE TABLE student_info(id BIGINT PRIMARY KEY,user_id BIGINT,name VARCHAR(50),student_id VARCHAR(50),dept_name VARCHAR(50),grade VARCHAR(20),gender INT,class_name VARCHAR(50),deleted INT DEFAULT 0)");
            s.execute("CREATE TABLE mental_questionnaire(id BIGINT AUTO_INCREMENT PRIMARY KEY,title VARCHAR(200),type VARCHAR(50),description VARCHAR(500),questions INT,start_time DATE,end_time DATE,status INT,level_rules VARCHAR(5000),creator_id BIGINT,deleted INT DEFAULT 0,create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,update_time TIMESTAMP)");
            s.execute("CREATE TABLE mental_question(id BIGINT AUTO_INCREMENT PRIMARY KEY,questionnaire_id BIGINT,sort_order INT,content VARCHAR(1000),question_type VARCHAR(30),options VARCHAR(5000),scoring_rules VARCHAR(5000),scale_min INT,scale_max INT,scale_labels VARCHAR(1000),required INT,deleted INT DEFAULT 0,create_time TIMESTAMP,update_time TIMESTAMP)");
            s.execute("CREATE TABLE mental_response(id BIGINT AUTO_INCREMENT PRIMARY KEY,student_id BIGINT,questionnaire_id BIGINT,answers VARCHAR(10000),questionnaire_snapshot VARCHAR(20000),score INT,status INT,create_time TIMESTAMP,UNIQUE(student_id,questionnaire_id))");
            s.execute("CREATE TABLE mental_assessment(id BIGINT AUTO_INCREMENT PRIMARY KEY,student_id BIGINT,questionnaire_id BIGINT,response_id BIGINT UNIQUE,score INT,level VARCHAR(50),result VARCHAR(500),suggestion VARCHAR(500),status INT,warning_status VARCHAR(32) DEFAULT 'OPEN',warning_note VARCHAR(1000),warning_handled_by BIGINT,warning_handled_at TIMESTAMP,create_time TIMESTAMP)");
            s.execute("INSERT INTO student_info(id,user_id,name,student_id,dept_name,grade,gender) VALUES(701,901,'虚构学生','MOCK-701','虚构学院','2026',1)");
        }
        questions=new QuestionServiceImpl(session.getMapper(QuestionMapper.class),session.getMapper(QuestionnaireMapper.class));
        questionnaires=new QuestionnaireServiceImpl(session.getMapper(QuestionnaireMapper.class),questions,new ObjectMapper());
        assessments=new MentalAssessmentServiceImpl(session.getMapper(QuestionnaireMapper.class),session.getMapper(QuestionMapper.class),session.getMapper(MentalResponseMapper.class),session.getMapper(MentalAssessmentMapper.class),session.getMapper(StudentLookupMapper.class));
        management=new MentalManagementService(session.getMapper(MentalAssessmentMapper.class));
    }
    @AfterEach void close() {if(session!=null){session.rollback();session.close();}}
    Questionnaire published() {
        var q=new Questionnaire();q.setTitle("虚构问卷");q.setType("虚构类型");q.setStatus(0);q.setStartTime(LocalDate.now().minusDays(1));q.setEndTime(LocalDate.now().plusDays(1));
        q.setLevelRules("[{\"level\":\"正常\",\"minScore\":3,\"suggestion\":\"测试建议\"},{\"level\":\"中度\",\"minScore\":0}]");
        questionnaires.save(q);var question=new Question();question.setQuestionnaireId(q.getId());question.setContent("原始题干");question.setQuestionType("single_choice");question.setRequired(1);question.setSortOrder(1);
        question.setOptions("[{\"label\":\"原始A\",\"score\":3},{\"label\":\"原始B\",\"score\":0}]");questions.save(question);
        var update=new QuestionnaireUpdateRequest();update.setId(q.getId());update.setStatus(1);questionnaires.updateMetadata(update);return questionnaires.getById(q.getId());
    }
    SubmitAnswerRequest response(Questionnaire q) {
        var a=new SubmitAnswerRequest.AnswerItem();a.setQuestionId(questions.listByQuestionnaire(q.getId()).get(0).getId());a.setOptionIndices(List.of(0));
        var r=new SubmitAnswerRequest();r.setUserId(901L);r.setQuestionnaireId(q.getId());r.setAnswers(List.of(a));return r;
    }
    @Test void persistsCalculatedResultWithExactResponseAndImmutableSafeSnapshot() throws Exception {
        var q=published();var result=assessments.submit(response(q)); assertEquals(3,result.getScore());assertNotNull(result.getResponseId());
        var response=session.getMapper(MentalResponseMapper.class).selectById(result.getResponseId());
        assertTrue(response.getQuestionnaireSnapshot().contains("原始A"));assertFalse(response.getQuestionnaireSnapshot().contains("\"score\""));
        var question=questions.listByQuestionnaire(q.getId()).get(0);question.setOptions("[{\"label\":\"新A\",\"score\":3},{\"label\":\"新B\",\"score\":0}]");questions.update(question);
        questionnaires.delete(q.getId());questions.delete(question.getId());
        var detail=assessments.getMyAssessmentDetail(901L,result.getId());assertEquals(true,detail.get("historySnapshotAvailable"));
        var old=(List<Question>)detail.get("questions");assertTrue(old.get(0).getOptions().contains("原始A"));assertFalse(old.get(0).getOptions().contains("新A"));
        assertNull(((Questionnaire)detail.get("questionnaire")).getLevelRules());
        assertEquals("虚构问卷",assessments.myHistory(901L).get(0).getQuestionnaireTitle());
        assertEquals("虚构类型",assessments.historyForStudent(701L).get(0).getQuestionnaireType());
    }
    @Test void emptyMissingRepeatedAndForeignAnswersNeverCreateAnyStoredResponse() throws Exception {
        var q=published();var empty=response(q);empty.setAnswers(List.of());assertThrows(BusinessException.class,()->assessments.submit(empty));
        var foreign=new SubmitAnswerRequest.AnswerItem();foreign.setQuestionId(98765L);foreign.setOptionIndices(List.of(0));var foreignRequest=response(q);foreignRequest.setAnswers(List.of(foreign));assertThrows(BusinessException.class,()->assessments.submit(foreignRequest));
        var repeated=response(q);repeated.setAnswers(List.of(repeated.getAnswers().get(0),repeated.getAnswers().get(0)));assertThrows(BusinessException.class,()->assessments.submit(repeated));
        try(var s=session.getConnection().createStatement();var rows=s.executeQuery("SELECT COUNT(*) FROM mental_response")){rows.next();assertEquals(0,rows.getInt(1));}
    }
    @Test void completionDoesNotJoinUnrelatedHistoricalAssessmentsAndResponseLinkIsUnique() {
        var q=published();var current=assessments.submit(response(q));
        var old=new MentalAssessment();old.setStudentId(701L);old.setQuestionnaireId(q.getId());old.setScore(999);old.setLevel("高危");old.setCreateTime(LocalDateTime.now().minusDays(1));session.getMapper(MentalAssessmentMapper.class).insert(old);
        var rows=assessments.listCompletion(q.getId());assertEquals(1,rows.size());assertEquals("正常",rows.get(0).get("level"));assertEquals(3,((Number)rows.get(0).get("score")).intValue());
        old.setId(null);old.setResponseId(current.getResponseId());assertThrows(Exception.class,()->session.getMapper(MentalAssessmentMapper.class).insert(old));
    }
    @Test void explicitNullDatesPersistButOmittedDatesRemainUntouched() {
        var q=published();var omission=new QuestionnaireUpdateRequest();omission.setId(q.getId());omission.setDescription("已更新");questionnaires.updateMetadata(omission);
        assertEquals(q.getStartTime(),questionnaires.getById(q.getId()).getStartTime());
        var clear=new QuestionnaireUpdateRequest();clear.setId(q.getId());clear.setStartTime(null);clear.setEndTime(null);questionnaires.updateMetadata(clear);
        var updated=questionnaires.getById(q.getId());assertNull(updated.getStartTime());assertNull(updated.getEndTime());
    }
    @Test void exactStudentScopeAndAmbiguousAccountBindingAreNotGuessed() throws Exception {
        var q=published();assessments.submit(response(q));
        assertEquals(701L,assessments.historyForStudent(701L).get(0).getStudentId());assertTrue(assessments.historyForStudent(701L).get(0).getQuestionnaireType().equals("虚构类型"));
        assertThrows(BusinessException.class,()->assessments.historyForStudent(702L));
        try(var s=session.getConnection().createStatement()){s.execute("INSERT INTO student_info(id,user_id,name) VALUES(702,901,'另一个虚构学生')");}
        assertThrows(BusinessException.class,()->assessments.myHistory(901L));assertThrows(BusinessException.class,()->assessments.listForStudent(901L));
    }
    @Test void warningPaginationAndDispositionAndTodayCountUseActualSql() throws Exception {
        var q=published();var r=response(q);r.getAnswers().get(0).setOptionIndices(List.of(1));var warning=assessments.submit(r);
        assertEquals(1,management.warnings(1,1,"OPEN").getTotal());assertEquals(0,management.warnings(2,1,"OPEN").getRecords().size());
        var action=new WarningActionRequest();action.setStatus("RESOLVED");action.setNote("虚构处置说明");management.handleWarning(warning.getId(),action,91L);
        assertEquals(0,management.warnings(1,10,"OPEN").getTotal());assertEquals(1,management.warnings(1,10,"RESOLVED").getTotal());
        LocalDate day=LocalDate.now(ZoneId.of("Asia/Shanghai"));assertEquals(1,session.getMapper(MentalAssessmentMapper.class).countSubmittedBetween(day.atStartOfDay(),day.plusDays(1).atStartOfDay()));
        assertEquals(0,session.getMapper(MentalAssessmentMapper.class).countSubmittedBetween(day.minusDays(1).atStartOfDay(),day.atStartOfDay()));
    }
    @Test void relationalTransactionRollsBackResponseWhenAssessmentInsertFails() throws Exception {
        var q=published();var request=response(q);
        try(var statement=session.getConnection().createStatement()) { statement.execute("ALTER TABLE mental_assessment ADD CHECK (score < 0)"); }
        session.commit();
        var template=new org.mybatis.spring.SqlSessionTemplate(sqlFactory);
        var transactional=new MentalAssessmentServiceImpl(template.getMapper(QuestionnaireMapper.class),template.getMapper(QuestionMapper.class),template.getMapper(MentalResponseMapper.class),template.getMapper(MentalAssessmentMapper.class),template.getMapper(StudentLookupMapper.class));
        var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource));
        assertThrows(Exception.class,()->tx.execute(status -> transactional.submit(request)));
        try(var statement=session.getConnection().createStatement();var rows=statement.executeQuery("SELECT COUNT(*) FROM mental_response")) {rows.next();assertEquals(0,rows.getInt(1));}
    }
    org.springframework.mock.web.MockMultipartFile excel(String score) throws Exception {
        try(var workbook=new org.apache.poi.xssf.usermodel.XSSFWorkbook();var bytes=new java.io.ByteArrayOutputStream()) {
            var sheet=workbook.createSheet("虚构导入");sheet.createRow(0).createCell(0).setCellValue("题号");
            var row=sheet.createRow(1);String[] values={"1","虚构导入选择题","单选","A","B","","","","",score,"2","","","","","是"};
            for(int i=0;i<values.length;i++) row.createCell(i).setCellValue(values[i]);
            var scale=sheet.createRow(2);scale.createCell(0).setCellValue("2");scale.createCell(1).setCellValue("虚构量表");scale.createCell(2).setCellValue("量表");scale.createCell(3).setCellValue("1~5");scale.createCell(4).setCellValue("最低");scale.createCell(5).setCellValue("最高");
            workbook.write(bytes);return new org.springframework.mock.web.MockMultipartFile("file","mock.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",bytes.toByteArray());
        }
    }
    @Test void excelPreviewIsReadOnlyAndInvalidImportDoesNotReplaceExistingQuestions() throws Exception {
        var q=published();var initial=questions.listByQuestionnaire(q.getId()).get(0).getId();
        var preview=questionnaires.previewImport(q.getId(),excel("3"));assertEquals(2,preview.size());assertEquals(5,preview.get(1).getScaleMax());assertTrue(preview.get(0).getOptions().contains("score"));assertEquals(initial,questions.listByQuestionnaire(q.getId()).get(0).getId());
        var bad=excel("invalid");assertThrows(BusinessException.class,()->questionnaires.importQuestionsFromExcel(q.getId(),bad));assertEquals(initial,questions.listByQuestionnaire(q.getId()).get(0).getId());
        questionnaires.importQuestionsFromExcel(q.getId(),excel("3"));assertEquals(2,questions.listByQuestionnaire(q.getId()).size());assertNotEquals(initial,questions.listByQuestionnaire(q.getId()).get(0).getId());assertEquals(2,questionnaires.getById(q.getId()).getQuestions());
    }
    @Test void oldHistoryWithoutSnapshotDoesNotInventCurrentAnswerLabels() {
        var q=published();var old=new MentalAssessment();old.setStudentId(701L);old.setQuestionnaireId(q.getId());old.setScore(3);old.setLevel("正常");session.getMapper(MentalAssessmentMapper.class).insert(old);
        var detail=assessments.getMyAssessmentDetail(901L,old.getId());assertEquals(false,detail.get("historySnapshotAvailable"));assertEquals(List.of(),detail.get("questions"));
        assertNull(((Questionnaire)detail.get("questionnaire")).getLevelRules());
        assertEquals("虚构问卷",assessments.myHistory(901L).get(0).getQuestionnaireTitle());
        assertEquals("虚构类型",assessments.historyForStudent(701L).get(0).getQuestionnaireType());
    }
}
