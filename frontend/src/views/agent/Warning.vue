<template>
  <div class="glass-panel list-panel">
    <div class="page-head">
      <div>
        <div class="head-title">AI 预警中心</div>
        <div class="head-sub">
          共 <b>{{ total }}</b> 个分析任务
        </div>
        <div class="head-sub">时间：北京时间（UTC+8）</div>
      </div>
      <div class="head-actions">
        <el-button class="btn-reset" :icon="Refresh" :loading="loading" @click="fetchList">
          刷新
        </el-button>
        <el-button
          type="primary"
          class="head-action"
          :icon="MagicStick"
          @click="triggerForm.visible = true"
        >
          触发分析
        </el-button>
      </div>
    </div>

    <!-- 过滤栏 -->
    <div class="filter-bar">
      <el-select v-model="searchForm.status" placeholder="全部状态" clearable>
        <el-option v-for="o in STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-select v-model="searchForm.riskLevel" placeholder="全部等级" clearable>
        <el-option v-for="o in RISK_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-button class="btn-query" @click="handleSearch">查询</el-button>
      <el-button class="btn-reset" @click="handleReset">重置</el-button>

      <span class="conn-state">
        <span v-if="sseConnected" class="badge conn-live">
          <span class="dot dot-live"></span>实时推送已连接
        </span>
        <span v-else-if="hasInProgressTask" class="badge conn-poll">
          <el-icon class="poll-icon"><Loading /></el-icon>轮询中（SSE 未连接）
        </span>
      </span>
    </div>

    <div class="table-wrap">
      <!-- 任务表 -->
      <el-table
        v-loading="loading"
        :data="taskList"
        class="warning-table"
        stripe
        element-loading-text="加载中..."
        :row-class-name="rowClass"
        empty-text="暂无任务，点击右上角「触发分析」开始"
      >
        <el-table-column prop="id" label="任务ID" width="80" />
        <el-table-column prop="studentId" label="学生ID" width="100" />
        <el-table-column label="状态" width="160">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" effect="dark">{{
              statusLabel(row.status)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="风险等级" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.riskLevel" :type="riskType(row.riskLevel)" effect="plain">
              {{ riskLabel(row.riskLevel) }}
            </el-tag>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="riskType" label="主要风险" min-width="160">
          <template #default="{ row }">
            <span v-if="row.riskType">{{ row.riskType }}</span>
            <span v-else-if="primaryRiskType(row)">{{ primaryRiskType(row) }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="完成时间" width="170">
          <template #default="{ row }">{{ formatTime(row.completedAt) || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="!canViewReport(row)" @click="viewReport(row)">
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
    </div>

    <!-- 触发对话框 -->
    <el-dialog v-model="triggerForm.visible" title="触发学生风险分析" width="420px">
      <el-form :model="triggerForm" label-width="80px">
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
          title="任务异步执行：风险识别 → 知识检索 → 方案生成 → 合规审核"
        />
      </el-form>
      <template #footer>
        <el-button @click="triggerForm.visible = false">取消</el-button>
        <el-button type="primary" :loading="triggerForm.submitting" @click="handleTrigger"
          >立即触发</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, MagicStick, Loading } from '@element-plus/icons-vue'
import { getAgentTaskList, triggerAgentTask } from '@/api/agent'
import { useUserStore } from '@/store/modules/user'
import { formatAgentTime as formatTime } from './agentTime'
import { createWarningStream } from './warningStream'

const userStore = useUserStore()

const router = useRouter()
// 初始化为 true：组件首次渲染就显示 loading，避免"空白页"闪烁
const loading = ref(true)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const taskList = ref([])

const searchForm = reactive({ status: '', riskLevel: '' })
const triggerForm = reactive({ visible: false, studentId: '', submitting: false })

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
// SSE 断开时指数退避重连（见 sseBackoff），期间轮询继续兜底。
let pollTimer = null
const sseConnected = ref(false)

// fetch-event-source 默认固定 1s 重连、并不退避。agent-service 休眠或唤醒失败时，
// 实测（2026-09-14）一个标签页 30 分钟向网关重连 930 次，网关因此无法休眠、白耗免费实例时长。
const sseBase = (import.meta.env.VITE_SSE_BASE_URL || '/api').replace(/\/$/, '')
const warningStream = createWarningStream({
  url: `${sseBase}/agent/api/v1/warning/stream`,
  getToken: () => userStore.token,
  onConnected: (value) => {
    sseConnected.value = value
  },
  onWarning: fetchList
})
watch(
  () => userStore.token,
  () => void warningStream.start(),
  { flush: 'sync' }
)

onMounted(() => {
  fetchList()
  pollTimer = setInterval(() => {
    if (hasInProgressTask.value) fetchList()
  }, 3000)
  void warningStream.start()
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
  warningStream.dispose()
})

const statusType = (s) =>
  ({
    PENDING: 'info',
    RISK_ANALYZING: 'warning',
    KNOWLEDGE_RETRIEVING: 'warning',
    PLAN_GENERATING: 'warning',
    COMPLIANCE_CHECKING: 'warning',
    COMPLETED: 'success',
    REJECTED: 'danger',
    FAILED: 'danger'
  })[s] || ''
const statusLabel = (s) => STATUS_OPTIONS.find((o) => o.value === s)?.label || s

const riskType = (r) =>
  ({ NONE: 'info', LOW: 'success', MEDIUM: 'warning', HIGH: 'danger' })[r] || ''
const riskLabel = (r) => RISK_OPTIONS.find((o) => o.value === r)?.label || r

const rowClass = ({ row }) => (row.riskLevel === 'HIGH' ? 'high-risk-row' : '')

const canViewReport = (row) =>
  ['COMPLETED', 'REJECTED', 'FAILED'].includes(row.status) || row.riskAnalysisResult

const viewReport = (row) => router.push(`/agent/report/${row.id}`)

const primaryRiskType = (row) => {
  try {
    const r = JSON.parse(row.riskAnalysisResult || '{}')
    return r.primary_risk_type || ''
  } catch {
    return ''
  }
}
</script>

<style scoped lang="scss">
.list-panel {
  display: flex;
  flex-direction: column;
}

.head-actions {
  display: flex;
  gap: 8px;

  .el-button {
    height: 34px;
    padding: 0 15px;
    font-size: 13px;
    border-radius: var(--radius-md);
    margin: 0;
  }
}

.head-action {
  font-weight: 600;
  border: none;
  box-shadow: 0 5px 14px -8px rgba(0, 122, 255, 0.7);
}

.filter-bar {
  :deep(.el-select) {
    width: 190px;
  }

  .conn-state {
    margin-left: auto;
  }
}

.conn-live {
  background: var(--success-tint);
  color: var(--success-deep);
}

.conn-poll {
  background: var(--warning-tint);
  color: var(--warning-deep);
}

.table-wrap {
  padding: 0 20px 18px;
  min-width: 0;
}

// The glass theme makes header/row cells transparent. Sticky operation cells
// must be opaque so horizontally scrolling dates cannot show through them.
:deep(.warning-table) {
  --el-table-fixed-box-shadow: 0 0 8px rgba(0, 0, 0, 0.08);
  --el-table-fixed-left-column: #fff;
  --el-table-fixed-right-column: #fff;
  --el-table-header-bg-color: #f5f7fa;

  th.el-table__cell {
    background-color: #f5f7fa;
  }
  td.el-table-fixed-column--right {
    background-color: #fff;
  }
  .el-table__row--striped td.el-table-fixed-column--right {
    background-color: #fafafa;
  }
  .el-table__body tr:hover > td.el-table-fixed-column--right {
    background-color: #f5f7fa;
  }
  .high-risk-row td.el-table-fixed-column--right {
    background-color: #fff0f0;
  }
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.muted {
  color: var(--text-color-disabled);
}

.poll-icon {
  margin-right: 4px;
  animation: spin 1.4s linear infinite;
}

.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
  background: var(--success-color);
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

// 高危行底色改用新色板的危险色浅底
:deep(.high-risk-row) {
  background-color: var(--error-tint) !important;
}
</style>
