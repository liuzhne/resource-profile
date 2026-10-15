package com.edu.student.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.edu.student.config.MyBatisPlusConfig;
import com.edu.student.mapper.*;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.session.SqlSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

/** Real MyBatis queries over the repository DDL in H2 MySQL mode; not a MySQL migration execution. */
final class PortraitSqlSupport {
    static SqlSession open() throws Exception {
        var dataSource = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:portrait_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "");
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(StudentMapper.class);
        configuration.addMapper(StudentQualityRecordMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource); factory.setConfiguration(configuration);
        factory.setTransactionFactory(new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory());
        factory.setPlugins(new MyBatisPlusConfig().mybatisPlusInterceptor());
        SqlSession session = factory.getObject().openSession(false);
        Path sql = Path.of(System.getProperty("basedir"), "../../sql/init");
        String initial = Files.readString(sql.resolve("01_init.sql"));
        String additive = Files.readString(sql.resolve("06_portrait_contract.sql"));
        createTable(session, initial, "sys_user");
        createTable(session, initial, "student_info");
        execute(session, "ALTER TABLE student_info ALTER COLUMN gpa SET DEFAULT NULL");
        execute(session, "ALTER TABLE student_info ALTER COLUMN credits SET DEFAULT NULL");
        createTable(session, additive, "student_quality_record");
        createTable(session, additive, "student_quality_record");
        return session;
    }
    static void execute(SqlSession session, String sql) throws Exception {
        try (var statement = session.getConnection().createStatement()) { statement.execute(sql); }
        session.clearCache();
    }
    private static void createTable(SqlSession session, String script, String table) throws Exception {
        var matcher = Pattern.compile("CREATE TABLE IF NOT EXISTS " + table + " \\((.*?)\\) ENGINE=.*?;", Pattern.DOTALL).matcher(script);
        if (!matcher.find()) throw new IllegalStateException("Missing repository schema for " + table);
        String body = matcher.group(1).replaceAll(" COMMENT '[^']*'", "");
        execute(session, "CREATE TABLE IF NOT EXISTS " + table + " (" + body + ")");
    }
}
