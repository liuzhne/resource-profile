<template>
  <div class="page-container">
    <el-page-header :title="`任务 #${taskId} · 干预报告`" @back="goBack">
      <template #extra>
        <el-button
          v-permission="['admin', 'psychologist']"
          type="primary"
          :icon="Download"
          :loading="exporting"
          :disabled="!canExport"
          @click="handleExport"
          >{{ exporting ? '导出中...' : '导出 PDF' }}</el-button
        >
        <el-button :icon="Refresh" :loading="loading" @click="fetchDetail">刷新</el-button>
      </template>
    </el-page-header>

    <el-card v-loading="loading" class="meta-card">
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="任务ID">{{ task.id }}</el-descriptions-item>
        <el-descriptions-item label="学生ID">{{ task.studentId }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(task.status)" effect="dark">{{
            statusLabel(task.status)
          }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="风险等级">
          <el-tag v-if="task.riskLevel" :type="riskType(task.riskLevel)">{{
            riskLabel(task.riskLevel)
          }}</el-tag>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{
          formatTime(task.createdAt)
        }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{
          formatTime(task.completedAt) || '—'
        }}</el-descriptions-item>
        <el-descriptions-item label="主要风险" :span="2">
          {{ risk.primary_risk_type || '—' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 4 阶段时间线 -->
    <el-card v-loading="loading" class="timeline-card">
      <template #header>
        <span>4-Agent 流水线</span>
      </template>

      <el-timeline>
        <!-- 阶段 1：风险识别 -->
        <el-timeline-item
          :type="stageType(1)"
          :icon="WarningFilled"
          size="large"
          placement="top"
          :hollow="!hasStage(1)"
        >
          <div class="stage-title">阶段 1 · 风险识别</div>
          <div v-if="hasStage(1)" class="stage-body">
            <div class="risk-summary">
              <div class="risk-score">
                <span class="num">{{ risk.risk_score ?? '—' }}</span>
                <span class="lbl">风险分</span>
              </div>
              <div class="risk-level">
                <el-tag :type="riskType(task.riskLevel)" size="large">{{
                  riskLabel(task.riskLevel)
                }}</el-tag>
                <div class="primary-type">{{ risk.primary_risk_type || '' }}</div>
              </div>
            </div>
            <el-alert
              v-if="risk.root_cause_analysis"
              type="warning"
              :closable="false"
              :title="risk.root_cause_analysis"
              show-icon
              class="mt-12"
            />
            <!-- 关键指标可能含心理量表原始分等敏感字段；普通辅导员视角隐藏 -->
            <div
              v-if="risk.key_indicators?.length"
              v-permission="['psychologist', 'admin']"
              class="mt-12"
            >
              <div class="sub-h">关键指标</div>
              <el-tag v-for="(k, i) in risk.key_indicators" :key="i" class="tag-mr">{{ k }}</el-tag>
            </div>
            <div v-if="risk.recommended_intervention_types?.length" class="mt-12">
              <div class="sub-h">推荐干预方向</div>
              <el-tag
                v-for="(t, i) in risk.recommended_intervention_types"
                :key="i"
                type="success"
                effect="plain"
                class="tag-mr"
                >{{ t }}</el-tag
              >
            </div>
            <div v-if="risk.urgency_reason" class="mt-12 urgency">
              <strong>紧迫性：</strong>{{ risk.urgency_reason }}
            </div>
          </div>
          <div v-else class="muted">未执行</div>
        </el-timeline-item>

        <!-- 阶段 2：知识检索 -->
        <el-timeline-item
          :type="stageType(2)"
          :icon="DocumentCopy"
          size="large"
          placement="top"
          :hollow="!hasStage(2)"
        >
          <div class="stage-title">
            阶段 2 · 知识检索
            <el-tag v-if="knowledge.reranked" type="success" size="small" class="ml-8"
              >Cross-Encoder 精排</el-tag
            >
            <el-tag v-else-if="knowledge.fallback" type="info" size="small" class="ml-8"
              >示例数据降级</el-tag
            >
          </div>
          <div v-if="hasStage(2) && knowledge.chunks?.length" class="stage-body">
            <el-table :data="knowledge.chunks" stripe size="small">
              <el-table-column prop="chunk_id" label="ID" width="120" />
              <el-table-column prop="source_db" label="知识库" width="100">
                <template #default="{ row }">
                  <el-tag :type="sourceTagType(row.source_db)" size="small">
                    {{ sourceLabel(row.source_db) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="title" label="标题" min-width="200" />
              <el-table-column prop="content" label="片段" min-width="280" show-overflow-tooltip />
              <el-table-column label="相关度" width="100" align="center">
                <template #default="{ row }">
                  <span>{{ formatScore(row.rerank_score ?? row.score) }}</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <div v-else-if="hasStage(2)" class="muted">无召回结果</div>
          <div v-else class="muted">未执行</div>
        </el-timeline-item>

        <!-- 阶段 3：方案生成 -->
        <el-timeline-item
          :type="stageType(3)"
          :icon="EditPen"
          size="large"
          placement="top"
          :hollow="!hasStage(3)"
        >
          <div class="stage-title">
            阶段 3 · 干预方案
            <span v-if="plan.report_title" class="plan-title">— {{ plan.report_title }}</span>
          </div>
          <div v-if="hasStage(3)" class="stage-body">
            <el-alert
              v-if="plan.summary"
              type="success"
              :closable="false"
              :title="plan.summary"
              show-icon
              class="mb-12"
            />

            <div v-if="plan.immediate_actions?.length" class="block">
              <div class="sub-h">立即行动（7 天内）</div>
              <el-table :data="plan.immediate_actions" size="small" stripe>
                <el-table-column prop="action" label="行动" min-width="280" />
                <el-table-column prop="owner" label="负责人" width="120" />
                <el-table-column label="期限" width="80" align="center">
                  <template #default="{ row }">{{ row.deadline_days || '—' }} 天</template>
                </el-table-column>
                <el-table-column label="引用" width="180">
                  <template #default="{ row }">
                    <el-tag
                      v-for="(r, i) in row.references || []"
                      :key="i"
                      size="small"
                      effect="plain"
                      class="tag-mr"
                      >{{ r }}</el-tag
                    >
                  </template>
                </el-table-column>
              </el-table>
            </div>

            <div v-if="plan.long_term_plan?.length" class="block">
              <div class="sub-h">长期跟进</div>
              <el-collapse>
                <el-collapse-item
                  v-for="(p, i) in plan.long_term_plan"
                  :key="i"
                  :title="p.phase || `阶段 ${i + 1}`"
                  :name="i"
                >
                  <div v-if="p.goals?.length"><strong>目标：</strong></div>
                  <ul>
                    <li v-for="(g, gi) in p.goals || []" :key="gi">{{ g }}</li>
                  </ul>
                  <div v-if="p.metrics?.length"><strong>衡量指标：</strong></div>
                  <ul>
                    <li v-for="(m, mi) in p.metrics || []" :key="mi">{{ m }}</li>
                  </ul>
                </el-collapse-item>
              </el-collapse>
            </div>

            <div v-if="plan.talk_outline" class="block">
              <div class="sub-h">谈话提纲</div>
              <el-input type="textarea" :model-value="plan.talk_outline" :rows="6" readonly />
            </div>

            <div v-if="plan.resources?.length" class="block">
              <div class="sub-h">资源推荐</div>
              <el-table :data="plan.resources" size="small" stripe>
                <el-table-column prop="type" label="类型" width="120" />
                <el-table-column prop="name" label="名称" min-width="220" />
                <el-table-column prop="link_or_contact" label="链接 / 联系" min-width="200" />
              </el-table>
            </div>
          </div>
          <div v-else class="muted">未执行</div>
        </el-timeline-item>

        <!-- 阶段 4：合规审核 -->
        <el-timeline-item
          :type="auditType"
          :icon="auditIcon"
          size="large"
          placement="top"
          :hollow="!hasStage(4)"
        >
          <div class="stage-title">阶段 4 · 合规审核</div>
          <div v-if="hasStage(4)" class="stage-body">
            <el-alert
              :type="audit.audit_passed ? 'success' : 'error'"
              :title="audit.audit_passed ? '审核通过' : '未通过 — 转人工兜底'"
              :closable="false"
              show-icon
              effect="dark"
            />
            <div v-if="audit.audit_items?.length" class="block">
              <div class="sub-h">审核维度</div>
              <el-table :data="audit.audit_items" size="small" stripe>
                <el-table-column label="维度" width="140">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.passed ? 'success' : 'danger'">
                      {{ dimensionLabel(row.dimension) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="结果" width="100" align="center">
                  <template #default="{ row }">
                    <el-icon v-if="row.passed" color="#67c23a"><SuccessFilled /></el-icon>
                    <el-icon v-else color="#f56c6c"><CircleCloseFilled /></el-icon>
                  </template>
                </el-table-column>
                <el-table-column prop="issue" label="问题说明" min-width="300" />
              </el-table>
            </div>
            <div v-if="audit.redacted_suggestions?.length" class="block">
              <div class="sub-h">整改建议</div>
              <ul>
                <li v-for="(s, i) in audit.redacted_suggestions" :key="i">{{ s }}</li>
              </ul>
            </div>
          </div>
          <div v-else class="muted">未执行</div>
        </el-timeline-item>
      </el-timeline>
    </el-card>

    <!-- I-5：干预反馈闭环（方案落地约 1 个月后由辅导员回填） -->
    <el-card v-if="canExport" class="feedback-card">
      <template #header>
        <span>干预效果跟进</span>
        <span class="fb-hint">建议方案落地约 1 个月后回填</span>
      </template>
      <el-form :model="feedback" label-width="92px" class="fb-form">
        <el-form-item label="效果评分">
          <el-rate v-model="feedback.score" :max="5" show-score score-template="{value} 分" />
        </el-form-item>
        <el-form-item label="跟进结果">
          <el-select v-model="feedback.outcome" placeholder="请选择" clearable style="width: 220px">
            <el-option label="有改善" value="improved" />
            <el-option label="无变化" value="unchanged" />
            <el-option label="恶化" value="worsened" />
            <el-option label="已升级处理" value="escalated" />
          </el-select>
        </el-form-item>
        <el-form-item label="文字反馈">
          <el-input
            v-model="feedback.comment"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            placeholder="干预过程、学生反馈、后续建议等"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="feedbackSubmitting"
            :disabled="!feedback.score"
            @click="handleSubmitFeedback"
            >提交反馈</el-button
          >
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Refresh,
  Download,
  WarningFilled,
  DocumentCopy,
  EditPen,
  CircleCheck,
  CircleClose,
  SuccessFilled,
  CircleCloseFilled
} from '@element-plus/icons-vue'
import {
  getAgentTaskDetail,
  exportReport,
  getExportStatus,
  downloadExport,
  submitInterventionFeedback
} from '@/api/agent'

const route = useRoute()
const router = useRouter()
const taskId = route.params.id

// 初始化为 true：组件首次渲染就显示 loading 遮罩
const loading = ref(true)
const task = ref({})

const safeParse = (s) => {
  if (!s) return {}
  try {
    return typeof s === 'string' ? JSON.parse(s) : s
  } catch (e) {
    return {}
  }
}

const risk = computed(() => safeParse(task.value.riskAnalysisResult))
const knowledge = computed(() => safeParse(task.value.retrievedKnowledge))
const plan = computed(() => safeParse(task.value.interventionPlan))
const audit = computed(() => safeParse(task.value.complianceAudit))

const hasStage = (n) => {
  if (n === 1) return !!task.value.riskAnalysisResult
  if (n === 2) return !!task.value.retrievedKnowledge
  if (n === 3) return !!task.value.interventionPlan
  if (n === 4) return !!task.value.complianceAudit
  return false
}

const stageType = (n) => (hasStage(n) ? 'primary' : 'info')
const auditType = computed(() => {
  if (!hasStage(4)) return 'info'
  return audit.value.audit_passed ? 'success' : 'danger'
})
const auditIcon = computed(() => (audit.value.audit_passed ? CircleCheck : CircleClose))

const fetchDetail = async () => {
  loading.value = true
  try {
    const res = await getAgentTaskDetail(taskId)
    task.value = res.data || {}
  } catch (e) {
    console.error('获取任务详情失败', e)
  } finally {
    loading.value = false
  }
}

onMounted(fetchDetail)

const goBack = () => router.back()

// ============== F-1：PDF 导出 ==============
const exporting = ref(false)
const exportJobId = ref(null)
let pollTimer = null
let pollCount = 0
const MAX_POLL = 30 // 30 * 2s = 60s 超时

// 仅当任务为 COMPLETED 或 REJECTED（已有 4 阶段产物）时允许导出
const canExport = computed(() => {
  const s = task.value.status
  return s === 'COMPLETED' || s === 'REJECTED'
})

const stopPoll = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  pollCount = 0
}

const triggerDownload = async (jobId) => {
  try {
    const blob = await downloadExport(jobId)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `report-${taskId}.pdf`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success('PDF 已下载')
  } catch (e) {
    console.error('下载 PDF 失败', e)
    ElMessage.error('下载 PDF 失败')
  }
}

const pollExportStatus = async () => {
  if (!exportJobId.value) return
  pollCount += 1
  if (pollCount > MAX_POLL) {
    stopPoll()
    exporting.value = false
    ElMessage.error('导出超时，请稍后重试')
    return
  }
  try {
    const res = await getExportStatus(exportJobId.value)
    const status = res.data?.status
    if (status === 'DONE') {
      stopPoll()
      const jid = exportJobId.value
      exportJobId.value = null
      exporting.value = false
      await triggerDownload(jid)
    } else if (status === 'FAILED') {
      stopPoll()
      exporting.value = false
      ElMessage.error(res.data?.errMsg || 'PDF 渲染失败')
    }
  } catch (e) {
    stopPoll()
    exporting.value = false
    console.error('查询导出状态失败', e)
  }
}

const handleExport = async () => {
  if (exporting.value) return
  exporting.value = true
  try {
    const res = await exportReport(taskId)
    const jobId = res.data?.jobId
    if (!jobId) throw new Error('未拿到 jobId')
    exportJobId.value = jobId
    pollCount = 0
    pollTimer = setInterval(pollExportStatus, 2000)
  } catch (e) {
    exporting.value = false
    console.error('触发导出失败', e)
    // request.js 拦截器已弹出错误提示，这里不再重复
  }
}

onUnmounted(stopPoll)

// ============== I-5：干预反馈闭环 ==============
const feedback = ref({ score: 0, outcome: '', comment: '' })
const feedbackSubmitting = ref(false)

const handleSubmitFeedback = async () => {
  if (!feedback.value.score) {
    ElMessage.warning('请先打分')
    return
  }
  feedbackSubmitting.value = true
  try {
    await submitInterventionFeedback({
      taskId: Number(taskId),
      score: feedback.value.score,
      outcome: feedback.value.outcome || null,
      comment: feedback.value.comment || null
    })
    ElMessage.success('反馈已提交，感谢跟进')
    feedback.value = { score: 0, outcome: '', comment: '' }
  } catch (e) {
    console.error('提交干预反馈失败', e)
    // request.js 拦截器已弹错误提示
  } finally {
    feedbackSubmitting.value = false
  }
}

const STATUS_LABELS = {
  PENDING: '待处理',
  RISK_ANALYZING: '风险识别中',
  KNOWLEDGE_RETRIEVING: '知识检索中',
  PLAN_GENERATING: '方案生成中',
  COMPLIANCE_CHECKING: '合规审核中',
  COMPLETED: '已完成',
  REJECTED: '合规未通过',
  FAILED: '系统异常'
}
const RISK_LABELS = { NONE: '无风险', LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险' }
const SOURCE_LABELS = { case: '案例库', psychology: '心理学', policy: '政策', success: '成功转化' }
const DIMENSION_LABELS = {
  privacy: '隐私保护',
  necessity: '最小必要',
  ethics: '教育伦理',
  third_party: '第三方风险',
  system: '系统'
}

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
const statusLabel = (s) => STATUS_LABELS[s] || s
const riskType = (r) =>
  ({ NONE: 'info', LOW: 'success', MEDIUM: 'warning', HIGH: 'danger' })[r] || ''
const riskLabel = (r) => RISK_LABELS[r] || r
const sourceTagType = (s) =>
  ({ case: '', psychology: 'success', policy: 'warning', success: 'info' })[s] || ''
const sourceLabel = (s) => SOURCE_LABELS[s] || s
const dimensionLabel = (d) => DIMENSION_LABELS[d] || d

const formatScore = (s) => {
  const n = Number(s)
  return Number.isFinite(n) ? n.toFixed(3) : '—'
}
const formatTime = (s) => (s ? s.replace('T', ' ').slice(0, 19) : '')
</script>

<style scoped lang="scss">
.meta-card,
.timeline-card,
.feedback-card {
  margin-top: 20px;
}
.feedback-card {
  .fb-hint {
    margin-left: 12px;
    font-size: 12px;
    color: rgba(0, 0, 0, 0.45);
  }
  .fb-form {
    max-width: 560px;
    margin-top: 8px;
  }
}
.stage-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
  .plan-title {
    font-weight: 400;
    color: rgba(0, 0, 0, 0.6);
    margin-left: 6px;
  }
}
.stage-body {
  padding: 8px 0 24px;
}
.risk-summary {
  display: flex;
  align-items: center;
  gap: 24px;
  .risk-score {
    text-align: center;
    .num {
      display: block;
      font-size: 36px;
      font-weight: 700;
      color: #f56c6c;
    }
    .lbl {
      color: rgba(0, 0, 0, 0.55);
      font-size: 12px;
    }
  }
  .primary-type {
    margin-top: 6px;
    color: rgba(0, 0, 0, 0.7);
  }
}
.urgency {
  background: #fffbe6;
  padding: 8px 12px;
  border-left: 3px solid #faad14;
}
.sub-h {
  font-weight: 600;
  margin-bottom: 8px;
  color: rgba(0, 0, 0, 0.85);
}
.block {
  margin-top: 16px;
}
.tag-mr {
  margin-right: 6px;
  margin-bottom: 6px;
}
.mt-12 {
  margin-top: 12px;
}
.mb-12 {
  margin-bottom: 12px;
}
.ml-8 {
  margin-left: 8px;
}
.muted {
  color: rgba(0, 0, 0, 0.4);
}
</style>
