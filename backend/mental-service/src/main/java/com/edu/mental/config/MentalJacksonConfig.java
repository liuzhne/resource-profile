package com.edu.mental.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Never truncate fractional scale values, option indexes, or numeric identifiers. */
@Configuration
public class MentalJacksonConfig {
    @Bean
    Jackson2ObjectMapperBuilderCustomizer mentalIntegerInputContract() {
        return builder -> builder.featuresToDisable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    }
}
