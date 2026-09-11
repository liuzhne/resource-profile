package com.edu.agent.schedule;

import com.edu.agent.feign.StudentServiceClient;
import com.edu.agent.service.AgentTaskService;
import com.edu.common.result.Result;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * E-1：定时扫描调度。
 * 每日 02:00 拉取所有在读学生 id，逐个调用 AgentTaskService#triggerTask 入异步流水线。
 * 多实例部署时由 Redis SETNX 分布式锁保证只有一个实例执行；
 * Nacos / 环境变量开关 {@code educare.schedule.enabled} 默认关闭，避免开发环境误触发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyScanScheduler {

    private final Environment environment;
    private final StringRedisTemplate redisTemplate;
    private final StudentServiceClient studentServiceClient;
    private final AgentTaskService agentTaskService;
    private final MeterRegistry meterRegistry;

    private static final String LOCK_KEY = "edu:agent:schedule:daily-scan";
    private static final long LOCK_TTL_SECONDS = 60L * 60L;
    private static final String UNLOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    // G-3.2：Micrometer 指标。预创建避免首次抓取缺指标。
    private Counter triggeredCounter;
    private Counter failedCounter;
    private Timer scanTimer;

    @PostConstruct
    void initMetrics() {
        triggeredCounter = Counter.builder("educare.daily_scan.triggered")
                .description("Number of successful per-student task triggers during daily scan")
                .register(meterRegistry);
        failedCounter = Counter.builder("educare.daily_scan.failed")
                .description("Number of per-student triggers that threw during daily scan")
                .register(meterRegistry);
        scanTimer = Timer.builder("educare.daily_scan.duration")
                .description("Wall-clock duration of one daily scan invocation")
                .register(meterRegistry);
    }

    @Scheduled(cron = "${educare.schedule.cron:0 0 2 * * ?}")
    public void scanAll() {
        // 通过 Environment 实时读取，便于 Nacos 热更新生效（无需 @RefreshScope）
        boolean enabled = Boolean.parseBoolean(environment.getProperty("educare.schedule.enabled", "false"));
        if (!enabled) {
            log.debug("educare.schedule.enabled=false，跳过定时扫描");
            return;
        }

        String lockValue = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY, lockValue, LOCK_TTL_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("已有实例持有定时扫描锁，跳过本次");
            return;
        }

        long start = System.currentTimeMillis();
        int triggered = 0;
        int failed = 0;
        try {
            Result<List<Long>> result = studentServiceClient.listActiveIds();
            List<Long> ids = (result != null && result.getData() != null)
                    ? result.getData() : Collections.emptyList();
            log.info("开始定时扫描，目标在读学生数: {}", ids.size());

            for (Long studentId : ids) {
                try {
                    agentTaskService.triggerTask(studentId);
                    triggered++;
                    triggeredCounter.increment();
                } catch (Exception e) {
                    failed++;
                    failedCounter.increment();
                    log.warn("定时扫描触发失败：studentId={}", studentId, e);
                }
            }
        } catch (Exception e) {
            log.error("定时扫描整体异常", e);
        } finally {
            long elapsedMs = System.currentTimeMillis() - start;
            scanTimer.record(elapsedMs, TimeUnit.MILLISECONDS);
            redisTemplate.execute(new DefaultRedisScript<>(UNLOCK_LUA, Long.class),
                    Collections.singletonList(LOCK_KEY), lockValue);
            log.info("定时扫描结束：成功触发 {}，失败 {}，耗时 {} ms", triggered, failed, elapsedMs);
        }
    }
}
