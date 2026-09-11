package com.edu.agent.core;

import com.edu.agent.sse.WarningPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * J-3.2 / J-3.3：StreamingHook + CheckpointHook 行为单测。
 */
class StreamingAndCheckpointHookTest {

    private AgentTrace trace() {
        return new AgentTrace(1, "raw", "想一想", "get_attendance", "{}", "obs", 1L, null);
    }

    // ---- J-3.2 StreamingHook ----

    @Test
    void streamingEnabledPublishesProgress() {
        WarningPublisher wp = mock(WarningPublisher.class);
        StreamingHook hook = new StreamingHook(wp);
        ReflectionTestUtils.setField(hook, "enabled", true);
        hook.onIteration("task-1", trace());
        verify(wp).publishProgress(eq("task-1"), eq(1), anyString(), eq("get_attendance"), anyInt());
    }

    @Test
    void streamingDisabledNoPublish() {
        WarningPublisher wp = mock(WarningPublisher.class);
        StreamingHook hook = new StreamingHook(wp);
        ReflectionTestUtils.setField(hook, "enabled", false);
        hook.onIteration("task-1", trace());
        verify(wp, never()).publishProgress(anyString(), anyInt(), anyString(), anyString(), anyInt());
    }

    // ---- J-3.3 CheckpointHook ----

    @Test
    void checkpointEnabledRpushesTrace() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ListOperations<String, String> listOps = mock(ListOperations.class);
        when(redis.opsForList()).thenReturn(listOps);
        CheckpointHook hook = new CheckpointHook(redis);
        ReflectionTestUtils.setField(hook, "enabled", true);
        ReflectionTestUtils.setField(hook, "ttlSeconds", 3600L);

        hook.onIteration("task-7", trace());

        verify(listOps).rightPush(eq("edu:agent:loop:ckpt:task-7"), anyString());
        verify(redis).expire(eq("edu:agent:loop:ckpt:task-7"), anyLong(), any());
    }

    @Test
    void checkpointDisabledNoop() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        CheckpointHook hook = new CheckpointHook(redis);
        ReflectionTestUtils.setField(hook, "enabled", false);
        hook.onIteration("task-7", trace());
        verify(redis, never()).opsForList();
    }

    private static java.util.concurrent.TimeUnit any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
