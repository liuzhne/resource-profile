package com.edu.user.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.edu.user.config.MyBatisPlusConfig;
import com.edu.user.entity.User;
import com.edu.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Uses the production interceptor and real SQL, rather than mocking Page.total. */
class UserPaginationSqlTest {

    private SqlSession session;
    private UserServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        var source = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:users_" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(UserMapper.class);

        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(configuration);
        try (var context = new AnnotationConfigApplicationContext(MyBatisPlusConfig.class)) {
            factory.setPlugins(context.getBean(MybatisPlusInterceptor.class));
        }
        session = factory.getObject().openSession(false);
        try (var statement = session.getConnection().createStatement()) {
            statement.execute("""
                    CREATE TABLE sys_user (
                      id BIGINT PRIMARY KEY, username VARCHAR(50), password VARCHAR(100),
                      nickname VARCHAR(50), avatar VARCHAR(255), email VARCHAR(100),
                      phone VARCHAR(30), dept_name VARCHAR(100), bio VARCHAR(1000), user_type INT, status INT, deleted INT,
                      create_time TIMESTAMP, update_time TIMESTAMP)
                    """);
            statement.execute("""
                    INSERT INTO sys_user
                      (id, username, password, nickname, user_type, status, deleted, create_time)
                    VALUES
                      (1, 'page_admin', 'private-password-hash', 'Admin', 1, 1, 0, '2026-10-01 10:00:00'),
                      (2, 'page_teacher', 'private-password-hash', 'Teacher', 2, 1, 0, '2026-10-02 10:00:00'),
                      (3, 'page_student', 'private-password-hash', 'Student', 3, 0, 0, '2026-10-03 10:00:00'),
                      (4, 'page_deleted', 'private-password-hash', 'Deleted', 3, 1, 1, '2026-10-04 10:00:00')
                    """);
        }
        try (var statement = session.getConnection().createStatement()) {
            statement.execute("CREATE TABLE sys_role(id BIGINT PRIMARY KEY,code VARCHAR(50),status INT,deleted INT)");
            statement.execute("CREATE TABLE sys_user_role(user_id BIGINT,role_id BIGINT)");
            statement.execute("INSERT INTO sys_role VALUES(7,'teacher',1,0),(8,'student',1,0)");
            statement.execute("INSERT INTO sys_user_role VALUES(2,7),(3,8),(4,7)");
        }
        service = new UserServiceImpl(session.getMapper(UserMapper.class));
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.rollback();
            session.close();
        }
    }

    @Test
    void countsActiveRowsAndLimitsDisjointPages() {
        Page<User> first = service.list(1, 2, null, null, null);
        Page<User> second = service.list(2, 2, null, null, null);

        assertThat(first.getTotal()).isEqualTo(3);
        assertThat(second.getTotal()).isEqualTo(3);
        assertThat(first.getPages()).isEqualTo(2);
        assertThat(first.getRecords()).extracting(User::getId).containsExactly(3L, 2L);
        assertThat(second.getRecords()).extracting(User::getId).containsExactly(1L);
    }

    @Test
    void countAndPageApplyTheSameFiltersAndLogicDelete() {
        Page<User> filtered = service.list(1, 1, "page_", null, 1);
        Page<User> matchingUsername = service.list(1, 2, "teacher", null, null);
        Page<User> deleted = service.list(1, 2, "deleted", null, null);

        assertThat(filtered.getTotal()).isEqualTo(2);
        assertThat(filtered.getRecords()).extracting(User::getId).containsExactly(2L);
        assertThat(matchingUsername.getTotal()).isEqualTo(1);
        assertThat(matchingUsername.getRecords()).extracting(User::getId).containsExactly(2L);
        assertThat(deleted.getTotal()).isZero();
        assertThat(deleted.getRecords()).isEmpty();
    }

    @Test
    void outOfRangePageRetainsTotalAndHasNoRecords() {
        Page<User> outOfRange = service.list(3, 2, null, null, null);

        assertThat(outOfRange.getTotal()).isEqualTo(3);
        assertThat(outOfRange.getRecords()).isEmpty();
    }

    @Test
    void serializingRealPaginatedRecordsDoesNotExposePasswords() throws Exception {
        Page<User> page = service.list(1, 2, null, null, null);
        // Confirm a real secret exists in the SQL result so the serialization check is meaningful.
        assertThat(page.getRecords()).allSatisfy(user -> assertThat(user.getPassword()).isNotBlank());
        var json = new ObjectMapper().findAndRegisterModules().valueToTree(page);

        assertThat(json.path("total").asLong()).isEqualTo(3);
        assertThat(json.path("records").size()).isEqualTo(2);
        json.path("records").forEach(user -> assertThat(user.has("password")).isFalse());
        assertThat(json.toString()).doesNotContain("private-password-hash");
    }

    @Test void roleFilterUsesAssignedRoleAndPreservesCount() {
        var teachers=service.list(1,10,null,7,null);
        assertThat(teachers.getTotal()).isEqualTo(1);
        assertThat(teachers.getRecords()).extracting(User::getId).containsExactly(2L);
        assertThat(service.list(1,10,null,99,null).getTotal()).isZero();
    }
}
