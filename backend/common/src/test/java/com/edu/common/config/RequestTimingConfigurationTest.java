package com.edu.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestTimingConfigurationTest {
    @Configuration(proxyBeanMethods = false)
    @Import(RequestTimingConfiguration.TimingAdvice.class)
    static class ScannedAdvice { }

    @Test
    void adviceIsRegisteredOnceWithAndWithoutComponentScanning() {
        var runner = new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RequestTimingConfiguration.class));
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(1, context.getBeansOfType(RequestTimingConfiguration.TimingAdvice.class).size());
        });
        runner.withUserConfiguration(ScannedAdvice.class).run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(1, context.getBeansOfType(RequestTimingConfiguration.TimingAdvice.class).size());
        });
    }
}
