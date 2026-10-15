package com.edu.data.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class DashboardTimeConfig {
    /** DATETIME events use the school business zone, shared with mental-service. */
    @Bean
    public Clock dashboardClock(@Value("${educare.business-zone:Asia/Shanghai}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
