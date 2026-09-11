<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>AI 预警中心</span>
          <div>
            <el-button :icon="Refresh" :loading="loading" @click="fetchList">刷新</el-button>
            <el-button type="primary" :icon="MagicStick" @click="triggerForm.visible = true"
              >触发分析</el-button
            >
          </div>
        </div>
      </template>

      <!-- 过滤栏 -->
      <el-form :model="searchForm" inline>
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
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
        <el-form-item style="margin-left: auto">
          <el-tag v-if="sseConnected" type="success" effect="plain" class="status-tag">
            <span class="dot dot-live"></span>
            实时推送已连接
          </el-tag>
          <el-tag v-else-if="hasInProgressTask" type="warning" effect="plain" class="status-tag">
            <el-icon class="poll-icon"><Loading /></el-icon>
            轮询中（SSE 未连接）
          </el-tag>
        </el-form-item>
      </el-form>

      <!-- 任务表 -->
      <el-table
        v-loading="loading"
        :data="taskList"
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
    </el-card>

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
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, MagicStick, Loading } from '@element-plus/icons-vue'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { getAgentTaskList, triggerAgentTask } from '@/api/agent'
import { useUserStore } from '@/store/modules/user'

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
// SSE 断开时由 fetch-event-source 自动指数退避重连，期间轮询继续兜底。
let pollTimer = null
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

onMounted(() => {
  fetchList()
  pollTimer = setInterval(() => {
    if (hasInProgressTask.value) fetchList()
  }, 3000)
  connectSse()
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
  if (sseAbortCtrl) sseAbortCtrl.abort()
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

const formatTime = (s) => {
  if (!s) return ''
  return s.replace('T', ' ').slice(0, 19)
}
</script>

<style scoped lang="scss">
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
.muted {
  color: rgba(0, 0, 0, 0.35);
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
  background-color: #fff1f0 !important;
}
</style>
