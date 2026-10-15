package com.edu.agent.core;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** AGENT-RUNNER-20261013：AgentLoop 实现 AgentRunner，业务层只依赖接口。 */
class AgentRunnerContractTest {

    @Test
    void agentLoopIsAnAgentRunner() {
        assertThat(AgentRunner.class).isAssignableFrom(AgentLoop.class);
    }

    @Test
    void agentTaskServiceDependsOnInterfaceOnly() {
        Field[] fields = com.edu.agent.service.impl.AgentTaskServiceImpl.class.getDeclaredFields();
        assertThat(Arrays.stream(fields).map(Field::getType)).contains(AgentRunner.class)
                .doesNotContain(AgentLoop.class);
    }
}
