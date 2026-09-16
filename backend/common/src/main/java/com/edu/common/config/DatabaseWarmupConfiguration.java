package com.edu.common.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

import javax.sql.DataSource;

/** Establish the TLS/database connection before ApplicationReady/readiness accepts traffic. */
@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(name = "educare.performance.database-warmup", havingValue = "true")
public class DatabaseWarmupConfiguration {
    @Bean
    @Lazy(false)
    public ApplicationRunner databaseWarmup(DataSource dataSource) {
        return args -> {
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.setQueryTimeout(5);
                statement.execute("SELECT 1");
            }
            // Failure propagates: a service that cannot reach its DB must not become ready.
        };
    }
}
