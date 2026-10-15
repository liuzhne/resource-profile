package com.edu.agent.service.impl;

import com.edu.agent.entity.AgentTask;
import com.edu.agent.enums.TaskStatus;
import com.edu.agent.mapper.AgentTaskMapper;
import com.edu.agent.sse.WarningPublisher;
import com.edu.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentTaskAdmissionTest {
    @Test void rejectedQueueMarksPersistedTaskFailedAndClearsIdempotency() {
        AgentTaskMapper mapper = mock(AgentTaskMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        WarningPublisher publisher = mock(WarningPublisher.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);
        AgentTask[] persisted = {null};
        when(mapper.insert(any(AgentTask.class))).thenAnswer(inv -> {
            persisted[0] = inv.getArgument(0); persisted[0].setId(42L); return 1;
        });
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "admission"), AgentTask.class);
        when(mapper.update(isNull(), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenAnswer(inv -> {
            persisted[0].setStatus(TaskStatus.FAILED); return 1;
        });
        when(mapper.selectById(42L)).thenAnswer(inv -> persisted[0]);
        Executor full = task -> { throw new RejectedExecutionException(); };
        AgentTaskServiceImpl service = new AgentTaskServiceImpl(mapper, null, null, null, null, redis,
                publisher, null, null, null, null, full);
        ReflectionTestUtils.setField(service, "triggerIdempotencyWindowSeconds", 30L);
        BusinessException error = assertThrows(BusinessException.class, () -> service.triggerTask(7L));
        assertEquals(503, error.getCode());
        assertEquals(TaskStatus.FAILED, persisted[0].getStatus());
        verify(redis).delete("edu:agent:trigger:7");
        verify(publisher).publishTerminal(42L, "FAILED", null, 7L);
    }
}
