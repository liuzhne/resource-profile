package com.edu.data.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.edu.common.exception.BusinessException;
import com.edu.data.mapper.DashboardMapper;
import com.edu.data.service.impl.DashboardServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;

/** Executes the actual XML mapper. H2 emulates MySQL DATE_FORMAT only; not a deployed MySQL test. */
public class DashboardSqlTest {
    SqlSession session; DashboardServiceImpl service;
    @BeforeEach void setup() throws Exception {
        var dataSource=new UnpooledDataSource("org.h2.Driver","jdbc:h2:mem:dashboard_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_UPPER=FALSE","sa","");
        var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);configuration.addMapper(DashboardMapper.class);
        var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(dataSource);factory.setConfiguration(configuration);factory.setMapperLocations(new ClassPathResource("mapper/DashboardMapper.xml"));factory.setTransactionFactory(new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory());
        session=factory.getObject().openSession(false);
        String initial=Files.readString(Path.of(System.getProperty("basedir"),"../../sql/init/01_init.sql"));
        String agent=Files.readString(Path.of(System.getProperty("basedir"),"../../sql/init/03_agent_init.sql"));
        for(String table:List.of("sys_user","teacher_info","student_info","mental_questionnaire","mental_response","mental_assessment","sys_login_log")) create(initial,table);
        create(agent,"agent_task");
        execute("ALTER TABLE mental_assessment ADD warning_status VARCHAR(32) NOT NULL DEFAULT 'OPEN'");
        execute("CREATE ALIAS DATE_FORMAT FOR 'com.edu.data.service.DashboardSqlTest.dateFormat'");
        execute("INSERT INTO sys_user(id,username,password,user_type,status) VALUES(1,'only-account','hash',0,1)");
        execute("INSERT INTO teacher_info(id,user_id,employee_id,name,status,create_time) VALUES (1,1,'T1','张某教师',1,'2026-10-07 08:00:00'),(2,1,'T2','张某另教师',1,'2026-10-06 08:00:00'),(3,1,'T3','离职',0,'2026-10-07 08:00:00'),(4,1,'T4','已删',1,'2026-10-07 08:00:00')");execute("UPDATE teacher_info SET deleted=1 WHERE id=4");
        execute("INSERT INTO student_info(id,user_id,student_id,name,status,create_time) VALUES (1,1,'S1','张某学生',1,'2026-10-07 08:00:00'),(2,1,'S2','其他学生',1,'2026-10-07 09:00:00'),(3,1,'S3','已删学生',1,'2026-10-07 08:00:00'),(4,1,'S4','已毕业',2,'2026-10-07 08:00:00')");execute("UPDATE student_info SET deleted=1 WHERE id=3");
        execute("INSERT INTO mental_questionnaire(id,title,status,deleted) VALUES (1,'有效模板',1,0),(2,'已停用模板',0,0),(3,'已删模板',1,1),(4,'启用但没人提交',1,0),(5,'另一空模板',1,0)");
        execute("INSERT INTO mental_response(id,student_id,questionnaire_id,answers,create_time) VALUES (1,1,1,'[]','2026-10-07 00:00:00'),(2,2,1,'[]','2026-10-07 23:59:59'),(3,1,2,'[]','2026-10-06 23:59:59'),(4,2,2,'[]','2026-10-08 00:00:00'),(5,3,1,'[]','2026-10-07 12:00:00'),(6,1,3,'[]','2026-10-07 12:00:00')");
        execute("INSERT INTO mental_assessment(id,student_id,questionnaire_id,level,score,result,warning_status,create_time) VALUES (1,1,1,'高危',99,'private-mental-result','OPEN','2026-10-07 09:00:00'),(2,1,2,'重度',88,'private-mental-result','IN_PROGRESS','2026-10-07 10:00:00'),(3,2,1,'轻度',10,'private-mental-result','OPEN','2026-10-07 11:00:00'),(4,2,2,'中度',66,'private-mental-result','RESOLVED','2026-10-07 12:00:00'),(5,3,1,'高危',99,'private-mental-result','OPEN','2026-10-07 13:00:00'),(6,2,4,'中度',55,'private-mental-result','OPEN','2026-10-07 14:00:00')");
        execute("INSERT INTO agent_task(id,student_id,status,risk_score,risk_analysis_result,updated_at,deleted) VALUES (1,1,'FAILED',99,'private-analysis','2026-10-07 15:00:00',0),(2,2,'REJECTED',88,'private-analysis','2026-10-07 16:00:00',0),(3,1,'COMPLETED',77,'private-analysis','2026-10-07 17:00:00',0),(4,3,'FAILED',99,'private-analysis','2026-10-07 18:00:00',0),(5,1,'FAILED',99,'private-analysis','2026-10-07 19:00:00',1)");
        Clock clock=Clock.fixed(Instant.parse("2026-10-06T18:30:00Z"),ZoneId.of("Asia/Shanghai"));
        service=new DashboardServiceImpl(session.getMapper(DashboardMapper.class),clock);
    }
    @AfterEach void close(){if(session!=null){session.rollback();session.close();}}
    @Test void todayCountsActualResponsesWithHalfOpenBusinessDayNotTemplates() {
        var stats=service.getStatistics();assertThat(stats.get("date")).isEqualTo("2026-10-07");assertThat(stats.get("timezone")).isEqualTo("Asia/Shanghai");
        assertThat(((Number)stats.get("todayCompletionCount")).longValue()).isEqualTo(2);
        assertThat(((Number)stats.get("teacherCount")).longValue()).isEqualTo(2);assertThat(((Number)stats.get("studentCount")).longValue()).isEqualTo(2);
        assertThat(((Number)stats.get("warningCount")).longValue()).isEqualTo(2);assertThat(stats).doesNotContainKey("questionnaireCount");
    }
    @Test void distributionMatchesActiveProfilePopulationEvenWhenProfilesShareAnAccount() {
        @SuppressWarnings("unchecked") var values=(List<Map<String,Object>>)service.getDistribution().get("data");
        assertThat(values).hasSize(2);assertThat(values).extracting(x->x.get("name")).containsExactly("教师","学生");
        assertThat(values.stream().mapToLong(x->((Number)x.get("value")).longValue()).sum()).isEqualTo(4);
    }
    @Test void todosUseRealUnclosedAndExceptionStatesBoundLimitAndExcludePrivateFields() throws Exception {
        var todos=service.getTodos(2);assertThat(todos.getTotal()).isEqualTo(5);assertThat(todos.getItems()).hasSize(2);
        assertThat(todos.getItems()).extracting(x->x.getStatus()).containsExactly("REJECTED","FAILED");
        assertThat(todos.getItems().get(0).getEventTime()).isEqualTo(LocalDateTime.of(2026,10,8,0,0));
        assertThat(todos.getItems().get(1).getEventTime()).isEqualTo(LocalDateTime.of(2026,10,7,23,0));
        assertThat(service.getTodos(-100).getItems()).hasSize(1);
        var all=service.getTodos(500);assertThat(all.getItems()).hasSize(5);
        String json=new ObjectMapper().findAndRegisterModules().writeValueAsString(all);
        assertThat(json).doesNotContain("private-mental-result","private-analysis","score","riskAnalysisResult","suggestion");
        execute("UPDATE mental_assessment SET warning_status='RESOLVED' WHERE id=1");
        assertThat(service.getTodos(20).getTotal()).isEqualTo(4);
    }
    @Test void searchIsLiteralBoundedMinimalProjectionAndExcludesDeleted() throws Exception {
        var matching=service.search("张某",10);assertThat(matching).hasSize(3);
        assertThat(matching).extracting(x->x.getType()).containsExactly("student","teacher","teacher");
        assertThat(service.search("%%",10)).isEmpty();assertThat(service.search("已删",10)).isEmpty();
        for(int i=10;i<35;i++) execute("INSERT INTO teacher_info(id,user_id,employee_id,name) VALUES ("+i+",1,'T"+i+"','张某教师"+i+"')");
        assertThat(service.search("张某",500)).hasSize(20);assertThat(service.search("张某",-1)).hasSize(1);
        String json=new ObjectMapper().writeValueAsString(matching);assertThat(json).doesNotContain("userId","phone","email","birthDate","gpa","riskScore");
        assertThatThrownBy(()->service.search("张",10)).isInstanceOf(BusinessException.class);assertThatThrownBy(()->service.search("x".repeat(51),10)).isInstanceOf(BusinessException.class);
    }
    @Test void rollingWindowsHaveExactLengthAndExcludeFutureRows() throws Exception {
        execute("INSERT INTO teacher_info(id,user_id,employee_id,name,status,create_time) VALUES (9,1,'FUTURE','未来记录',1,'2026-10-08 00:00:00')");
        var week=service.getTrend("week");var month=service.getTrend("month");var year=service.getTrend("year");
        assertThat((List<?>)week.get("days")).hasSize(7);assertThat((List<?>)month.get("days")).hasSize(30);
        assertThat((List<?>)year.get("days")).hasSize(12).first().isEqualTo("2025-11");
        @SuppressWarnings("unchecked") var teacherCounts=(List<Long>)week.get("teacherData");assertThat(teacherCounts.stream().mapToLong(Long::longValue).sum()).isEqualTo(3); // two active and one former profile; deleted/future excluded
        assertThat((List<?>)year.get("days")).last().isEqualTo("2026-10");
    }
    public static String dateFormat(java.sql.Timestamp timestamp,String mysqlPattern){if(timestamp==null)return null;return timestamp.toLocalDateTime().format(java.time.format.DateTimeFormatter.ofPattern(mysqlPattern.replace("%Y","yyyy").replace("%m","MM").replace("%d","dd").replace("%H","HH").replace("%i","mm")));}
    void execute(String sql) throws Exception{try(var stmt=session.getConnection().createStatement()){stmt.execute(sql);}session.clearCache();}
    void create(String script,String table) throws Exception {var matcher=Pattern.compile("CREATE TABLE IF NOT EXISTS "+table+" \\((.*?)\\) ENGINE\\s*=.*?;",Pattern.DOTALL).matcher(script);if(!matcher.find())throw new IllegalStateException(table);execute("CREATE TABLE "+table+" ("+matcher.group(1).replaceAll(" COMMENT '[^']*'","")+")");}
}
