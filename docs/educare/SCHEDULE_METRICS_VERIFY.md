# 定时扫描 + Prometheus 指标验收（G-3.4）

> **目标**：把 `educare.schedule.enabled` 置 true，让 `DailyScanScheduler` 真跑一次，然后 `/actuator/prometheus` 抓到 G-3.2 三个指标的非零值。
>
> **前置**：G-3.1（actuator + micrometer-registry-prometheus）+ G-3.2（DailyScanScheduler 三个 meter 预创建）已合入。
> 
> 验收通过：2026-05-13，执行人：Apple

---

## 1. 启动依赖

```bash
cd docker
docker-compose up -d mysql redis nacos    # 至少三个；若 ai-inference 需要也起 milvus
```

确认：
```bash
docker-compose ps   # mysql/redis/nacos 状态都是 Up
```

---

## 2. 配置开关

两种方式任选其一：

### A. 环境变量（最快）
```bash
export EDUCARE_SCHEDULE_ENABLED=true
# 把 cron 改为每分钟触发，避免等到凌晨 02:00
export EDUCARE_SCHEDULE_CRON="0 */1 * * * ?"
```

### B. Nacos 配置中心
登 `http://localhost:8848/nacos`，编辑 `agent-service.yml`，加：
```yaml
educare:
  schedule:
    enabled: true
    cron: "0 */1 * * * ?"
```
发布后无需重启。

---

## 3. 启动 agent-service

```bash
cd backend
mvn -pl agent-service -am package -DskipTests -q
java -jar agent-service/target/agent-service-1.0.0-SNAPSHOT.jar
```

启动日志里检查：
- `Tomcat started on port 8087`
- 没有 `educare.schedule.enabled=false，跳过定时扫描` 这条 debug 行（如果有说明开关没生效）

---

## 4. 触发 + 验证

等到下一个整分钟（每分钟 cron 触发），观察日志：

```
开始定时扫描，目标在读学生数: N
... triggerTask 调用日志 ...
定时扫描结束：成功触发 X，失败 Y，耗时 Z ms
```

> 注意：测试库可能没有"在读学生"。如果 `listActiveIds` 返回空集合，loop 不会进 counter，但 timer 仍会 record（耗时 ~5ms）。要看到非零 counter，先往 `student_info` 表塞 2-3 条 `status=1 AND deleted=0` 的记录。

抓 prometheus exposition：

```bash
curl -s http://localhost:8087/actuator/prometheus | grep educare_daily_scan
```

期望输出（命名规约见下方）：
```
# HELP educare_daily_scan_triggered_total Number of successful per-student task triggers during daily scan
# TYPE educare_daily_scan_triggered_total counter
educare_daily_scan_triggered_total{application="agent-service",} 3.0

# HELP educare_daily_scan_failed_total Number of per-student triggers that threw during daily scan
# TYPE educare_daily_scan_failed_total counter
educare_daily_scan_failed_total{application="agent-service",} 0.0

# HELP educare_daily_scan_duration_seconds Wall-clock duration of one daily scan invocation
# TYPE educare_daily_scan_duration_seconds summary
educare_daily_scan_duration_seconds_count{application="agent-service",} 1.0
educare_daily_scan_duration_seconds_sum{application="agent-service",} 0.45
educare_daily_scan_duration_seconds_max{application="agent-service",} 0.45
```

**通过标准**：上面三个指标都出现，且至少 `_count` ≥ 1（timer 跑过一次）。`triggered_total` 取决于测试库有多少在读学生。

---

## 5. 故障排查

| 现象 | 原因 | 修复 |
|------|------|------|
| `curl /actuator/prometheus` 404 | actuator endpoint 没暴露 | 检查 application.yml 的 `management.endpoints.web.exposure.include` 是否含 `prometheus` |
| `curl` 200 但里面没 `educare_daily_scan_*` | 调度器从未跑过 → meter 也未注册 | meter 是 `@PostConstruct` 预创建的，启动就应该有 0 值；若仍没有 → MeterRegistry bean 未注入（检查 actuator 依赖是否真的解析） |
| 调度器一直不触发 | `educare.schedule.enabled` 未生效 | 看启动日志找 "educare.schedule.enabled=false" debug 行；用 `curl -s http://localhost:8848/nacos/v1/cs/configs?dataId=agent-service.yml&group=DEFAULT_GROUP` 确认配置 |
| Redis 锁一直拿不到 | 上次跑遗留的锁未释放 | `redis-cli DEL edu:agent:schedule:daily-scan` |
| `triggered_total = 0`，但日志显示 listActiveIds 返回若干 | 每个 student 的 triggerTask 都抛了 | 看 "定时扫描触发失败" warn 行；可能是幂等/限流（30s 窗口）；改不同 studentId 重试 |
| `metrics.tags.application` 标签缺失 | application.yml 的 `management.metrics.tags.application` 没生效（拼写错） | 检查 yml 缩进与字段名 |

---

## 6. 清理

```bash
# 把开关复位
export EDUCARE_SCHEDULE_ENABLED=false
unset EDUCARE_SCHEDULE_CRON  # 恢复默认 0 0 2 * * ?
# 或在 Nacos 上把临时配置删了
```

测试残留：`agent_task` 表里会留几条 PENDING / COMPLETED 的任务记录，正常业务态，无需清理。

---

## 7. 通过后

在 `docs/educare/EXECUTION_PLAN.md` 把 G-3.4 勾选；并把本文件顶部加 "验收通过：YYYY-MM-DD，执行人：xxx"。

---

## 8. 后续接入（不阻塞本步）

- **Prometheus 抓取**：把 agent-service 的 `8087/actuator/prometheus` 加进 prometheus.yml 的 scrape target；常规 15s interval 足够（调度是分钟级触发）
- **Grafana 看板**：建议三个面板 —— triggered rate (counter `rate(educare_daily_scan_triggered_total[5m])`) / failure ratio (`educare_daily_scan_failed_total / educare_daily_scan_triggered_total`) / duration p95 (`histogram_quantile(0.95, sum by (le) (rate(educare_daily_scan_duration_seconds_bucket[5m])))`)
- **告警规则**：连续 N 分钟 `failed_total` 增量 > 0 或 `duration_seconds_max` > 阈值 → 告警
