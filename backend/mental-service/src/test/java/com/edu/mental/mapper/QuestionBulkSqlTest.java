package com.edu.mental.mapper;

import com.edu.mental.entity.Question;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class QuestionBulkSqlTest {
    @Test
    void actualMybatisSqlBindsJsonNullsAndUnicodeAndRollsBackTogether() throws Exception {
        var source = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:bulk;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        configuration.addMapper(QuestionMapper.class);
        var factory = new SqlSessionFactoryBuilder().build(configuration);
        try (var session = factory.openSession(false); var statement = session.getConnection().createStatement()) {
            statement.execute("""
                CREATE TABLE mental_question (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY, questionnaire_id BIGINT NOT NULL,
                  sort_order INT, content VARCHAR(1000) NOT NULL, question_type VARCHAR(30) NOT NULL,
                  options VARCHAR(1000), scoring_rules VARCHAR(1000), scale_min INT, scale_max INT,
                  scale_labels VARCHAR(1000), required INT, deleted INT,
                  create_time TIMESTAMP, update_time TIMESTAMP)
                """);
            Question choice = question("学生's 选择题", "single_choice");
            choice.setOptions("[{\"label\":\"安全'参数\",\"value\":\"A\"}]");
            choice.setScoringRules("{\"A\":2}");
            Question scale = question("量表题", "scale");
            scale.setScaleMin(1);
            scale.setScaleMax(10);
            scale.setScaleLabels("{\"min\":\"低\",\"max\":\"高\"}");
            assertEquals(2, session.getMapper(QuestionMapper.class).insertBulk(List.of(choice, scale)));
            try (var rows = statement.executeQuery("SELECT * FROM mental_question ORDER BY id")) {
                assertTrue(rows.next());
                assertEquals(choice.getContent(), rows.getString("content"));
                assertEquals(choice.getOptions(), rows.getString("options"));
                assertEquals(0, rows.getInt("deleted"));
                assertNotNull(rows.getTimestamp("create_time"));
                assertTrue(rows.next());
                assertEquals(10, rows.getInt("scale_max"));
                assertNull(rows.getString("options"));
                assertFalse(rows.next());
            }
            session.rollback();
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM mental_question")) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1));
            }
        }
    }
    private Question question(String content, String type) {
        Question question = new Question();
        question.setQuestionnaireId(9L);
        question.setSortOrder(1);
        question.setContent(content);
        question.setQuestionType(type);
        question.setRequired(1);
        question.setCreateTime(LocalDateTime.now());
        question.setUpdateTime(LocalDateTime.now());
        return question;
    }
}
