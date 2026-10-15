<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="trace-header">
          <span>LLM 追踪（Langfuse）</span>
          <el-link
            v-if="langfuseUrl"
            :href="langfuseUrl"
            target="_blank"
            rel="noopener noreferrer"
            type="primary"
          >
            Langfuse 控制台
          </el-link>
        </div>
      </template>

      <p class="trace-description">直接查看已上报的调用记录、模型、耗时和 token 用量。</p>
      <p class="trace-hint">
        每行是一条观测记录，并非完整调用树；观测级别不代表业务任务是否成功。
        上报数据可能延迟，未显示不代表没有调用。时间统一按北京时间（UTC+8）显示。 “循环完成”只表示
        Agent 循环产出，最终审核结果请查看任务报告。
      </p>
      <p v-if="traceId" class="trace-window">
        关联追踪：<code>{{ traceId }}</code>
        <el-button link type="primary" @click="clearTrace">显示全部追踪</el-button>
      </p>

      <div class="trace-toolbar">
        <el-radio-group :model-value="rangeDays" aria-label="追踪时间范围" @change="changeRange">
          <el-radio-button :value="1">最近 24 小时</el-radio-button>
          <el-radio-button :value="7">最近 7 天</el-radio-button>
          <el-radio-button :value="30">最近 30 天</el-radio-button>
        </el-radio-group>
        <div class="trace-actions">
          <el-select :model-value="pageSize" aria-label="每页条数" @change="changeSize">
            <el-option :value="20" label="每页 20 条" />
            <el-option :value="50" label="每页 50 条" />
          </el-select>
          <el-button :loading="loading" :disabled="loading" @click="refresh">刷新</el-button>
        </div>
      </div>
      <p v-if="from && to" class="trace-window">
        查询范围：{{ formatObservationTime(from) }} 至 {{ formatObservationTime(to) }}
      </p>

      <div
        v-if="loading"
        v-loading="loading"
        class="trace-loading"
        role="status"
        aria-live="polite"
      >
        正在读取追踪数据…
      </div>
      <div v-else-if="loadError" class="trace-error" role="alert">
        <el-alert :title="errorMessage" type="error" :closable="false" />
        <el-button type="primary" plain @click="retry">重试</el-button>
      </div>
      <el-empty v-else-if="records.length === 0" description="当前时间范围内暂无已上报的观测记录" />
      <template v-else>
        <p class="trace-page-count">
          当前第 {{ currentPage }} 页，本页 {{ records.length }} 条观测记录
        </p>
        <el-table :data="records" row-key="id" stripe class="trace-table">
          <el-table-column label="开始时间" min-width="170">
            <template #default="{ row }">{{ formatObservationTime(row.startTime) }}</template>
          </el-table-column>
          <el-table-column label="观测名称" min-width="170">
            <template #default="{ row }">
              <span class="trace-name">{{ row.name || '未记录' }}</span>
              <div class="trace-hint">{{ row.type || '未记录' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="模型" min-width="170">
            <template #default="{ row }">{{ row.model || '未记录' }}</template>
          </el-table-column>
          <el-table-column label="Agent 状态" min-width="150">
            <template #default="{ row }">
              <el-tag :type="observationAgentStatusType(row.agentStatus)">{{
                observationAgentStatusLabel(row.agentStatus)
              }}</el-tag>
              <div v-if="row.iterations != null" class="trace-hint">{{ row.iterations }} 轮</div>
            </template>
          </el-table-column>
          <el-table-column label="失败诊断" min-width="200">
            <template #default="{ row }">
              <template v-if="row.errorCode">
                <div>{{ failureCodeLabel(row.errorCode) }}</div>
                <div class="trace-hint">
                  {{ failureStageLabel(row.errorStage) }} · {{ failureHttpLabel(row.httpStatus) }}
                </div>
              </template>
              <span v-else>未记录</span>
            </template>
          </el-table-column>
          <el-table-column label="耗时" min-width="120" align="right">
            <template #default="{ row }">{{ formatObservationLatency(row.latencyMs) }}</template>
          </el-table-column>
          <el-table-column label="输入 token" min-width="115" align="right">
            <template #default="{ row }">{{ formatObservationTokens(row.inputTokens) }}</template>
          </el-table-column>
          <el-table-column label="输出 token" min-width="115" align="right">
            <template #default="{ row }">{{ formatObservationTokens(row.outputTokens) }}</template>
          </el-table-column>
          <el-table-column label="总 token" min-width="115" align="right">
            <template #default="{ row }">{{ formatObservationTokens(row.totalTokens) }}</template>
          </el-table-column>
          <el-table-column label="观测级别" min-width="120">
            <template #default="{ row }">
              <el-tag :type="observationLevelType(row.level)">{{
                observationLevelLabel(row.level)
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="traceId" label="追踪标识" min-width="220" show-overflow-tooltip />
        </el-table>
      </template>

      <div class="trace-pagination">
        <el-button :disabled="!canPrevious" @click="previousPage">上一页</el-button>
        <span>第 {{ currentPage }} 页</span>
        <el-button :disabled="!canNext" @click="nextPage">下一页</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { failureCodeLabel, failureStageLabel, failureHttpLabel } from '@/views/agent/failureState'
import { normalizeConsoleUrl } from './traceConsole'
import {
  useObservations,
  formatObservationTime,
  formatObservationTokens,
  formatObservationLatency,
  observationLevelLabel,
  observationLevelType,
  observationAgentStatusLabel,
  observationAgentStatusType
} from './observationState'

const langfuseUrl = normalizeConsoleUrl(import.meta.env.VITE_LANGFUSE_URL)
const route = useRoute()
const router = useRouter()
const {
  rangeDays,
  pageSize,
  from,
  to,
  traceId,
  records,
  loading,
  loadError,
  errorMessage,
  currentPage,
  canPrevious,
  canNext,
  refresh,
  changeRange,
  changeSize,
  changeTraceId,
  nextPage,
  previousPage,
  retry
} = useObservations(undefined, undefined, route.query.traceId)
const clearTrace = () => router.replace({ path: route.path, query: {} })
watch(
  () => route.query.traceId,
  (value) => void changeTraceId(value)
)

onMounted(refresh)
</script>

<style scoped>
.trace-header,
.trace-toolbar,
.trace-actions,
.trace-pagination {
  display: flex;
  align-items: center;
  gap: 12px;
}
.trace-header,
.trace-toolbar {
  justify-content: space-between;
  flex-wrap: wrap;
}
.trace-description {
  margin: 0 0 8px;
}
.trace-hint,
.trace-window {
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
}
.trace-hint {
  margin: 0 0 20px;
}
.trace-actions .el-select {
  width: 130px;
}
.trace-window {
  margin: 12px 0;
}
.trace-loading {
  min-height: 220px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #606266;
}
.trace-error .el-button {
  margin-top: 16px;
}
.trace-page-count {
  font-size: 13px;
  color: #606266;
}
.trace-name {
  overflow-wrap: anywhere;
}
.trace-table .trace-hint {
  margin: 4px 0 0;
}
.trace-pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
@media (max-width: 600px) {
  .trace-toolbar,
  .trace-actions {
    align-items: flex-start;
  }
  .trace-pagination {
    justify-content: center;
  }
}
</style>
