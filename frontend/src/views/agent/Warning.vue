<template>
  <div class="page-container agent-warning-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">AI 预警中心</h1>
        <div class="page-subtitle">
          跟踪学生风险识别任务，查看分析进度、风险等级与干预报告生成状态。
        </div>
      </div>
      <div class="page-actions">
        <el-button :icon="Refresh" :loading="loading" @click="fetchList"> 刷新 </el-button>
        <el-button type="primary" :icon="MagicStick" @click="triggerForm.visible = true">
          触发分析
        </el-button>
      </div>
    </div>

    <div class="overview-grid">
      <div class="overview-card">
        <span>任务总量</span>
        <strong>{{ total }}</strong>
        <p>当前筛选条件下的任务记录</p>
      </div>
      <div class="overview-card is-danger">
        <span>当前页高风险</span>
        <strong>{{ taskSummary.high }}</strong>
        <p>建议优先查看报告与合规结果</p>
      </div>
      <div class="overview-card is-warning">
        <span>当前页处理中</span>
        <strong>{{ taskSummary.processing }}</strong>
        <p>系统将自动轮询刷新进度</p>
      </div>
      <div class="overview-card is-success">
        <span>当前页已完成</span>
        <strong>{{ taskSummary.completed }}</strong>
        <p>可进入干预报告详情</p>
      </div>
    </div>

    <div class="toolbar-card filter-panel">
      <el-form :model="searchForm" inline class="filter-form">
        <el-form-item label="状态">
          <el-select
            v-model="searchForm.status"
            placeholder="全部状态"
            clearable
            style="width: 200px"
          >
            <el-option
              v-for="o in STATUS_OPTIONS"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="风险等级">
          <el-select
            v-model="searchForm.riskLevel"
            placeholder="全部等级"
            clearable
            style="width: 160px"
          >
            <el-option v-for="o in RISK_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch"> 查询 </el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
        <el-form-item style="margin-left: auto">
          <el-tag v-if="sseConnected" type="success" effect="plain" class="status-tag">
            <span class="dot dot-live"></span>
            实时推送已连接
          </el-tag>
          <el-tag v-else-if="hasInProgressTask" type="warning" effect="plain" class="status-tag">
            <el-icon class="poll-icon"><Loading /></el-icon>
            自动刷新中
          </el-tag>
        </el-form-item>
      </el-form>
    </div>

    <el-card class="table-card warning-table-card">
      <template #header>
        <div class="card-header">
          <div>
            <span>预警任务列表</span>
            <span class="result-count">共 {{ total }} 条</span>
          </div>
          <span class="header-hint">异步流程：识别 → 检索 → 方案 → 合规</span>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="taskList"
        stripe
        element-loading-text="加载中..."
        :row-class-name="rowClass"
        empty-text="暂无预警任务"
      >
        <el-table-column label="任务" min-width="150">
          <template #default="{ row }">
            <div class="task-title">任务 #{{ row.id }}</div>
            <div class="task-sub">学生 ID：{{ row.studentId || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="处理状态" min-width="170">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" effect="plain">
              {{ statusLabel(row.status) }}
            </el-tag>
            <div class="state-desc">{{ statusHint(row.status) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="风险判断" min-width="220">
          <template #default="{ row }">
            <div class="risk-line">
              <el-tag v-if="row.riskLevel" :type="riskType(row.riskLevel)" effect="plain">
                {{ riskLabel(row.riskLevel) }}
              </el-tag>
              <span v-else class="muted">未评估</span>
              <span class="risk-type">{{ row.riskType || primaryRiskType(row) || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="时间" min-width="210">
          <template #default="{ row }">
            <div class="time-row"><span>创建</span>{{ formatTime(row.createdAt) || '-' }}</div>
            <div class="time-row"><span>完成</span>{{ formatTime(row.completedAt) || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :icon="View"
              :disabled="!canViewReport(row)"
              @click="viewReport(row)"
            >
              查看报告
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        class="pagination"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="fetchList"
        @size-change="fetchList"
      />
    </el-card>

    <el-dialog
      v-model="triggerForm.visible"
      title="触发学生风险分析"
      width="460px"
      class="trigger-dialog"
    >
      <el-form :model="triggerForm" label-width="82px">
        <el-form-item label="学生ID" required>
          <el-input
            v-model="triggerForm.studentId"
            placeholder="输入学生 ID"
            clearable
            @keyup.enter="handleTrigger"
          />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="任务将进入异步队列，并依次完成风险识别、知识检索、方案生成与合规审核。"
        />
      </el-form>
      <template #footer>
        <el-button @click="triggerForm.visible = false">取消</el-button>
        <el-button type="primary" :loading="triggerForm.submitting" @click="handleTrigger">
          立即触发
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
const userStore = useUserStore()
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, MagicStick, Loading, Search, View } from '@element-plus/icons-vue'
import { getAgentTaskList, triggerAgentTask } from '@/api/agent'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { useUserStore } from '@/store/modules/user'

const router = useRouter()
const loading = ref(true)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const taskList = ref([])

const searchForm = reactive({ status: '', riskLevel: '' })
const triggerForm = reactive({
  visible: false,
  studentId: '',
  submitting: false
})

const STATUS_OPTIONS = [
  { value: 'PENDING', label: '待处理' },
  { value: 'RISK_ANALYZING', label: '风险识别中' },
  { value: 'KNOWLEDGE_RETRIEVING', label: '知识检索中' },
  { value: 'PLAN_GENERATING', label: '方案生成中' },
  { value: 'COMPLIANCE_CHECKING', label: '合规审核中' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'REJECTED', label: '合规未通过' },
  { value: 'FAILED', label: '系统异常' }
]
const RISK_OPTIONS = [
  { value: 'HIGH', label: '高风险' },
  { value: 'MEDIUM', label: '中风险' },
  { value: 'LOW', label: '低风险' },
  { value: 'NONE', label: '无风险' }
]

const IN_PROGRESS_STATUSES = new Set([
  'PENDING',
  'RISK_ANALYZING',
  'KNOWLEDGE_RETRIEVING',
  'PLAN_GENERATING',
  'COMPLIANCE_CHECKING'
])

const taskSummary = computed(() => ({
  high: taskList.value.filter((item) => item.riskLevel === 'HIGH').length,
  processing: taskList.value.filter((item) => IN_PROGRESS_STATUSES.has(item.status)).length,
  completed: taskList.value.filter((item) => item.status === 'COMPLETED').length
}))

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getAgentTaskList({
      page: currentPage.value,
      size: pageSize.value,
      status: searchForm.status || undefined,
      riskLevel: searchForm.riskLevel || undefined
    })
    taskList.value = res.data?.records || []
    total.value = Number(res.data?.total) || 0
  } catch (e) {
    console.error('获取任务列表失败', e)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  fetchList()
}

const handleReset = () => {
  searchForm.status = ''
  searchForm.riskLevel = ''
  currentPage.value = 1
  fetchList()
}

const handleTrigger = async () => {
  const sid = triggerForm.studentId.trim()
  if (!sid) {
    ElMessage.warning('请输入学生 ID')
    return
  }
  triggerForm.submitting = true
  try {
    const res = await triggerAgentTask(sid)
    ElMessage.success(`已触发任务 #${res.data}`)
    triggerForm.visible = false
    triggerForm.studentId = ''
    currentPage.value = 1
    fetchList()
  } catch (e) {
    console.error('触发失败', e)
  } finally {
    triggerForm.submitting = false
  }
}

const hasInProgressTask = computed(() =>
  taskList.value.some((t) => IN_PROGRESS_STATUSES.has(t.status))
)

// ====== F-2：SSE 实时推送 + 轮询兜底 ======
// 设计：SSE 推送 4 阶段流水线终态（COMPLETED / REJECTED / FAILED）；
// 中间状态（RISK_ANALYZING 等）仍靠 3s 轮询，仅在 hasInProgressTask 时打 API。
// SSE 断开时由 fetch-event-source 自动指数退避重连，期间轮询继续兜底。
let sseAbortCtrl = null
const sseConnected = ref(false)

const connectSse = async () => {
  if (sseAbortCtrl) sseAbortCtrl.abort()
  sseAbortCtrl = new AbortController()
  try {
    await fetchEventSource('/api/agent/api/v1/warning/stream', {
      signal: sseAbortCtrl.signal,
      headers: userStore.token ? { Authorization: `Bearer ${userStore.token}` } : {},
      openWhenHidden: true, // 切到后台标签页也保持连接
      onopen: async (resp) => {
        if (resp.ok && resp.headers.get('content-type')?.includes('text/event-stream')) {
          sseConnected.value = true
        } else {
          // 非 200 或非 SSE 内容 —— 直接抛错，让 fetch-event-source 走 onerror
          throw new Error(`SSE 握手失败 status=${resp.status}`)
        }
      },
      onmessage: (ev) => {
        // 后端事件名：hello / warning。心跳是注释行（: ping），onmessage 不会收到
        if (ev.event === 'warning') {
          // 收到任意终态事件 → 刷新列表
          fetchList()
        }
      },
      onerror: (err) => {
        sseConnected.value = false
        // 返回 undefined 让 fetch-event-source 走默认指数退避重连；
        // 抛异常则停止重连（这里我们想自动重连，故不抛）
        console.warn('SSE 连接异常，将自动重连', err?.message || err)
      },
      onclose: () => {
        sseConnected.value = false
      }
    })
  } catch (e) {
    sseConnected.value = false
    // 主动 abort 不算错误
    if (e?.name !== 'AbortError') {
      console.warn('SSE 终止', e)
    }
  }
}

let pollTimer = null
onMounted(() => {
  fetchList()
  pollTimer = setInterval(() => {
    if (hasInProgressTask.value) fetchList()
  }, 3000)
  connectSse()
})
onUnmounted(() => {
  if (sseAbortCtrl) sseAbortCtrl.abort()
  if (pollTimer) clearInterval(pollTimer)
})

const statusType = (status) =>
  ({
    PENDING: 'info',
    RISK_ANALYZING: 'warning',
    KNOWLEDGE_RETRIEVING: 'warning',
    PLAN_GENERATING: 'warning',
    COMPLIANCE_CHECKING: 'warning',
    COMPLETED: 'success',
    REJECTED: 'danger',
    FAILED: 'danger'
  })[status] || 'info'
const statusLabel = (status) =>
  STATUS_OPTIONS.find((item) => item.value === status)?.label || status || '-'
const statusHint = (status) =>
  ({
    PENDING: '等待进入分析队列',
    RISK_ANALYZING: '正在识别风险信号',
    KNOWLEDGE_RETRIEVING: '正在匹配知识库依据',
    PLAN_GENERATING: '正在生成干预方案',
    COMPLIANCE_CHECKING: '正在进行合规审核',
    COMPLETED: '报告已生成',
    REJECTED: '需人工复核后处理',
    FAILED: '请排查任务日志'
  })[status] || '暂无状态说明'

const riskType = (risk) =>
  ({ NONE: 'info', LOW: 'success', MEDIUM: 'warning', HIGH: 'danger' })[risk] || 'info'
const riskLabel = (risk) => RISK_OPTIONS.find((item) => item.value === risk)?.label || risk || '-'

const rowClass = ({ row }) => (row.riskLevel === 'HIGH' ? 'high-risk-row' : '')

const canViewReport = (row) =>
  ['COMPLETED', 'REJECTED', 'FAILED'].includes(row.status) || row.riskAnalysisResult

const viewReport = (row) => router.push(`/agent/report/${row.id}`)

const primaryRiskType = (row) => {
  try {
    const result = JSON.parse(row.riskAnalysisResult || '{}')
    return result.primary_risk_type || ''
  } catch {
    return ''
  }
}

const formatTime = (value) => (value ? String(value).replace('T', ' ').slice(0, 19) : '')
</script>

<style scoped lang="scss">
.agent-warning-page {
  .page-actions {
    display: flex;
    gap: 10px;
    flex-shrink: 0;
  }
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 14px;
}

.overview-card {
  min-height: 118px;
  padding: 18px;
  background: var(--bg-color-container);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-base);
  box-shadow: var(--shadow-1);

  span {
    display: block;
    color: var(--text-color-secondary);
    font-size: 13px;
  }

  strong {
    display: block;
    margin-top: 10px;
    color: var(--text-color);
    font-size: 28px;
    line-height: 1;
  }

  p {
    margin: 12px 0 0;
    color: var(--text-color-muted);
    font-size: 12px;
    line-height: 1.5;
  }

  &.is-danger strong {
    color: #d64545;
  }

  &.is-warning strong {
    color: #d9822b;
  }

  &.is-success strong {
    color: #2f9e44;
  }
}

.filter-panel {
  padding: 16px 16px 4px;
  margin-bottom: 14px;
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;

  .poll-state {
    margin-left: auto;
  }
}

.warning-table-card {
  :deep(.el-card__body) {
    padding-top: 0;
  }
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;

  .result-count {
    margin-left: 10px;
    color: var(--text-color-muted);
    font-size: 12px;
    font-weight: 400;
  }

  .header-hint {
    color: var(--text-color-muted);
    font-size: 12px;
  }
}

.task-title {
  color: var(--text-color);
  font-weight: 650;
  line-height: 1.5;
}

.task-sub,
.state-desc,
.time-row span {
  color: var(--text-color-muted);
  font-size: 12px;
}

.state-desc {
  margin-top: 6px;
}

.risk-line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;

  .risk-type {
    min-width: 0;
    color: var(--text-color-regular);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.time-row {
  color: var(--text-color-regular);
  line-height: 1.7;

  span {
    display: inline-block;
    width: 34px;
    margin-right: 8px;
  }
}

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}

.muted {
  color: var(--text-color-muted);
}

.poll-icon {
  margin-right: 4px;
  animation: spin 1.4s linear infinite;
}
.status-tag {
  display: inline-flex;
  align-items: center;
}
.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
  background: #67c23a;
}
.dot-live {
  animation: pulse 1.6s ease-in-out infinite;
}
@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.35;
  }
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

:deep(.high-risk-row) {
  background-color: #fff7f7 !important;
}

@media (max-width: 1100px) {
  .overview-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .filter-form .poll-state {
    margin-left: 0;
  }
}

@media (max-width: 720px) {
  .page-header,
  .card-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .overview-grid {
    grid-template-columns: 1fr;
  }
}
</style>
