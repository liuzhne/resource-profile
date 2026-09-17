package com.edu.auth.config;

import com.edu.common.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthDependencyWarmupConfigurationTest {
    @Test
    @SuppressWarnings("unchecked")
    void warmsReadOnlyTransportAndSigningWithoutCreatingSession() throws Exception {
        var redis = mock(StringRedisTemplate.class);
        var connection = mock(RedisConnection.class);
        var jwt = mock(JwtUtil.class);
        when(connection.ping()).thenReturn("PONG");
        when(redis.execute(any(RedisCallback.class))).thenAnswer(call ->
                ((RedisCallback<String>) call.getArgument(0)).doInRedis(connection));
        new AuthDependencyWarmupConfiguration().authDependencyWarmup(redis, jwt).run(null);
        verify(connection).ping();
        verifyNoMoreInteractions(connection);
        verify(redis).execute(any(RedisCallback.class));
        verifyNoMoreInteractions(redis);
        verify(jwt).generateAccessToken("0", Map.of());
        verify(jwt).generateRefreshToken("0");
    }
    @Test
    @SuppressWarnings("unchecked")
    void unavailableSessionTransportCannotBecomeReady() {
        var redis = mock(StringRedisTemplate.class);
        var jwt = mock(JwtUtil.class);
        when(redis.execute(any(RedisCallback.class))).thenReturn(null);
        assertThrows(IllegalStateException.class, () ->
                new AuthDependencyWarmupConfiguration().authDependencyWarmup(redis, jwt).run(null));
        verifyNoInteractions(jwt);
    }
}
