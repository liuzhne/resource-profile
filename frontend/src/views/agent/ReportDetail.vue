<template>
  <div class="page-container report-detail-page">
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

    <div class="page-header">
      <div>
        <h2 class="page-title">干预报告</h2>
        <div class="page-subtitle">
          任务 #{{ taskId }} · 学生 {{ displayValue(task.studentId) }} ·
          {{ formatTime(task.createdAt) || '-' }}
        </div>
      </div>
    </div>

    <div v-loading="loading" class="report-shell">
      <section class="summary-panel">
        <div class="summary-main">
          <div class="summary-label">当前结论</div>
          <div class="summary-title">
            {{ risk.primary_risk_type || '暂无主要风险结论' }}
          </div>
          <div class="summary-desc">
            {{ plan.summary || risk.root_cause_analysis || '等待 AI 流程生成完整报告。' }}
          </div>
        </div>
        <div class="summary-metrics">
          <div class="metric-tile">
            <span>任务状态</span>
            <el-tag :type="statusType(task.status)" effect="plain">
              {{ statusLabel(task.status) || '-' }}
            </el-tag>
          </div>
          <div class="metric-tile">
            <span>风险等级</span>
            <el-tag v-if="task.riskLevel" :type="riskType(task.riskLevel)">
              {{ riskLabel(task.riskLevel) }}
            </el-tag>
            <strong v-else>-</strong>
          </div>
          <div class="metric-tile">
            <span>风险分</span>
            <strong :class="['score-value', riskScoreClass]">
              {{ displayValue(risk.risk_score) }}
            </strong>
          </div>
          <div class="metric-tile">
            <span>合规状态</span>
            <el-tag :type="auditBadge.type" effect="plain">
              {{ auditBadge.label }}
            </el-tag>
          </div>
        </div>
      </section>

      <section class="meta-strip">
        <div>
          <span>任务 ID</span>
          <strong>{{ displayValue(task.id) }}</strong>
        </div>
        <div>
          <span>学生 ID</span>
          <strong>{{ displayValue(task.studentId) }}</strong>
        </div>
        <div>
          <span>创建时间</span>
          <strong>{{ formatTime(task.createdAt) || '-' }}</strong>
        </div>
        <div>
          <span>完成时间</span>
          <strong>{{ formatTime(task.completedAt) || '-' }}</strong>
        </div>
      </section>

      <section class="pipeline-panel">
        <div class="panel-head">
          <div>
            <h3>4-Agent 流程记录</h3>
            <p>风险识别、知识检索、方案生成与合规审核的过程留痕。</p>
          </div>
          <el-tag effect="plain">{{ completedStageCount }}/4 已完成</el-tag>
        </div>

        <el-timeline class="report-timeline">
          <el-timeline-item
            :type="stageType(1)"
            :icon="WarningFilled"
            size="large"
            :hollow="!hasStage(1)"
          >
            <article class="stage-card">
              <div class="stage-head">
                <div>
                  <span class="stage-index">01</span>
                  <h4>风险识别</h4>
                </div>
                <el-tag :type="stageType(1)" effect="plain">
                  {{ hasStage(1) ? '已生成' : '未执行' }}
                </el-tag>
              </div>

              <template v-if="hasStage(1)">
                <div class="risk-layout">
                  <div class="risk-score-card">
                    <strong :class="riskScoreClass">
                      {{ displayValue(risk.risk_score) }}
                    </strong>
                    <span>风险分</span>
                  </div>
                  <div class="risk-copy">
                    <el-tag :type="riskType(task.riskLevel)" effect="light">
                      {{ riskLabel(task.riskLevel) || '-' }}
                    </el-tag>
                    <p>{{ risk.root_cause_analysis || '暂无根因分析。' }}</p>
                  </div>
                </div>

                <div
                  v-if="risk.key_indicators?.length"
                  v-permission="['psychologist', 'admin']"
                  class="tag-block"
                >
                  <div class="block-title">关键指标</div>
                  <el-tag v-for="(k, i) in risk.key_indicators" :key="i" effect="plain">
                    {{ k }}
                  </el-tag>
                </div>

                <div v-if="risk.recommended_intervention_types?.length" class="tag-block">
                  <div class="block-title">推荐干预方向</div>
                  <el-tag
                    v-for="(t, i) in risk.recommended_intervention_types"
                    :key="i"
                    type="success"
                    effect="plain"
                  >
                    {{ t }}
                  </el-tag>
                </div>

                <div v-if="risk.urgency_reason" class="notice warning">
                  <strong>紧迫性</strong>
                  <span>{{ risk.urgency_reason }}</span>
                </div>
              </template>
              <el-empty v-else description="未执行" :image-size="72" />
            </article>
          </el-timeline-item>

          <el-timeline-item
            :type="stageType(2)"
            :icon="DocumentCopy"
            size="large"
            :hollow="!hasStage(2)"
          >
            <article class="stage-card">
              <div class="stage-head">
                <div>
                  <span class="stage-index">02</span>
                  <h4>知识检索</h4>
                </div>
                <div class="stage-tags">
                  <el-tag v-if="knowledge.reranked" type="success" effect="plain">
                    Cross-Encoder 精排
                  </el-tag>
                  <el-tag v-else-if="knowledge.fallback" type="info" effect="plain">
                    示例数据降级
                  </el-tag>
                  <el-tag :type="stageType(2)" effect="plain">
                    {{ hasStage(2) ? '已检索' : '未执行' }}
                  </el-tag>
                </div>
              </div>

              <el-table
                v-if="hasStage(2) && knowledge.chunks?.length"
                :data="knowledge.chunks"
                stripe
                size="small"
              >
                <el-table-column prop="chunk_id" label="ID" width="120" />
                <el-table-column prop="source_db" label="知识库" width="110">
                  <template #default="{ row }">
                    <el-tag :type="sourceTagType(row.source_db)" effect="plain">
                      {{ sourceLabel(row.source_db) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="title" label="标题" min-width="180" />
                <el-table-column
                  prop="content"
                  label="片段"
                  min-width="300"
                  show-overflow-tooltip
                />
                <el-table-column label="相关度" width="100" align="center">
                  <template #default="{ row }">
                    {{ formatScore(row.rerank_score ?? row.score) }}
                  </template>
                </el-table-column>
              </el-table>
              <el-empty
                v-else
                :description="hasStage(2) ? '无召回结果' : '未执行'"
                :image-size="72"
              />
            </article>
          </el-timeline-item>

          <el-timeline-item
            :type="stageType(3)"
            :icon="EditPen"
            size="large"
            :hollow="!hasStage(3)"
          >
            <article class="stage-card">
              <div class="stage-head">
                <div>
                  <span class="stage-index">03</span>
                  <h4>干预方案</h4>
                  <p v-if="plan.report_title">{{ plan.report_title }}</p>
                </div>
                <el-tag :type="stageType(3)" effect="plain">
                  {{ hasStage(3) ? '已生成' : '未执行' }}
                </el-tag>
              </div>

              <template v-if="hasStage(3)">
                <div v-if="plan.summary" class="notice success">
                  <strong>方案摘要</strong>
                  <span>{{ plan.summary }}</span>
                </div>

                <div v-if="plan.immediate_actions?.length" class="content-block">
                  <div class="block-title">立即行动（7 天内）</div>
                  <el-table :data="plan.immediate_actions" size="small" stripe>
                    <el-table-column prop="action" label="行动" min-width="280" />
                    <el-table-column prop="owner" label="负责人" width="120" />
                    <el-table-column label="期限" width="90" align="center">
                      <template #default="{ row }"> {{ row.deadline_days || '-' }} 天 </template>
                    </el-table-column>
                    <el-table-column label="引用" min-width="180">
                      <template #default="{ row }">
                        <el-tag
                          v-for="(r, i) in row.references || []"
                          :key="i"
                          size="small"
                          effect="plain"
                          class="tag-inline"
                        >
                          {{ r }}
                        </el-tag>
                        <span v-if="!(row.references || []).length">-</span>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>

                <div v-if="plan.long_term_plan?.length" class="content-block">
                  <div class="block-title">长期跟进</div>
                  <el-collapse class="report-collapse">
                    <el-collapse-item
                      v-for="(p, i) in plan.long_term_plan"
                      :key="i"
                      :title="p.phase || `阶段 ${i + 1}`"
                      :name="i"
                    >
                      <div class="collapse-grid">
                        <div>
                          <strong>目标</strong>
                          <ul>
                            <li v-for="(g, gi) in p.goals || []" :key="gi">
                              {{ g }}
                            </li>
                          </ul>
                        </div>
                        <div>
                          <strong>衡量指标</strong>
                          <ul>
                            <li v-for="(m, mi) in p.metrics || []" :key="mi">
                              {{ m }}
                            </li>
                          </ul>
                        </div>
                      </div>
                    </el-collapse-item>
                  </el-collapse>
                </div>

                <div v-if="plan.talk_outline" class="content-block">
                  <div class="block-title">谈话提纲</div>
                  <div class="readonly-box">{{ plan.talk_outline }}</div>
                </div>

                <div v-if="plan.resources?.length" class="content-block">
                  <div class="block-title">资源推荐</div>
                  <el-table :data="plan.resources" size="small" stripe>
                    <el-table-column prop="type" label="类型" width="120" />
                    <el-table-column prop="name" label="名称" min-width="220" />
                    <el-table-column prop="link_or_contact" label="链接 / 联系" min-width="220" />
                  </el-table>
                </div>
              </template>
              <el-empty v-else description="未执行" :image-size="72" />
            </article>
          </el-timeline-item>

          <el-timeline-item :type="auditType" :icon="auditIcon" size="large" :hollow="!hasStage(4)">
            <article class="stage-card">
              <div class="stage-head">
                <div>
                  <span class="stage-index">04</span>
                  <h4>合规审核</h4>
                </div>
                <el-tag :type="auditBadge.type" effect="plain">
                  {{ auditBadge.label }}
                </el-tag>
              </div>

              <template v-if="hasStage(4)">
                <div :class="['notice', audit.audit_passed ? 'success' : 'danger']">
                  <strong>{{ audit.audit_passed ? '审核通过' : '转人工兜底' }}</strong>
                  <span>
                    {{
                      audit.audit_passed
                        ? '方案满足隐私、伦理与最小必要原则。'
                        : '当前报告存在合规风险，建议人工复核后再进入干预流程。'
                    }}
                  </span>
                </div>

                <div v-if="audit.audit_items?.length" class="content-block">
                  <div class="block-title">审核维度</div>
                  <el-table :data="audit.audit_items" size="small" stripe>
                    <el-table-column label="维度" width="140">
                      <template #default="{ row }">
                        <el-tag :type="row.passed ? 'success' : 'danger'" effect="plain">
                          {{ dimensionLabel(row.dimension) }}
                        </el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column label="结果" width="90" align="center">
                      <template #default="{ row }">
                        <el-icon v-if="row.passed" color="#2f9e44"><SuccessFilled /></el-icon>
                        <el-icon v-else color="#d64545"><CircleCloseFilled /></el-icon>
                      </template>
                    </el-table-column>
                    <el-table-column prop="issue" label="问题说明" min-width="300" />
                  </el-table>
                </div>

                <div v-if="audit.redacted_suggestions?.length" class="content-block">
                  <div class="block-title">整改建议</div>
                  <ul class="suggestion-list">
                    <li v-for="(s, i) in audit.redacted_suggestions" :key="i">
                      {{ s }}
                    </li>
                  </ul>
                </div>
              </template>
              <el-empty v-else description="未执行" :image-size="72" />
            </article>
          </el-timeline-item>
        </el-timeline>
      </section>

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
            <el-select
              v-model="feedback.outcome"
              placeholder="请选择"
              clearable
              style="width: 220px"
            >
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

const loading = ref(true)
const task = ref({})

const safeParse = (value) => {
  if (!value) return {}
  try {
    return typeof value === 'string' ? JSON.parse(value) : value
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

const completedStageCount = computed(() => [1, 2, 3, 4].filter((stage) => hasStage(stage)).length)

const stageType = (n) => (hasStage(n) ? 'primary' : 'info')
const auditType = computed(() => {
  if (!hasStage(4)) return 'info'
  return audit.value.audit_passed ? 'success' : 'danger'
})
const auditIcon = computed(() => (audit.value.audit_passed ? CircleCheck : CircleClose))
const auditBadge = computed(() => {
  if (!hasStage(4)) return { label: '未执行', type: 'info' }
  return audit.value.audit_passed
    ? { label: '审核通过', type: 'success' }
    : { label: '需人工复核', type: 'danger' }
})

const riskScoreClass = computed(() => {
  const score = Number(risk.value.risk_score)
  if (!Number.isFinite(score)) return ''
  if (score >= 80) return 'is-high'
  if (score >= 60) return 'is-medium'
  return 'is-low'
})

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
    const response = await downloadExport(jobId)
    const blob = response?.data instanceof Blob ? response.data : response
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
    await pollExportStatus()
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
const RISK_LABELS = {
  NONE: '无风险',
  LOW: '低风险',
  MEDIUM: '中风险',
  HIGH: '高风险'
}
const SOURCE_LABELS = {
  case: '案例库',
  psychology: '心理学',
  policy: '政策',
  success: '成功转化'
}
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
  })[s] || 'info'
const statusLabel = (s) => STATUS_LABELS[s] || s
const riskType = (r) =>
  ({ NONE: 'info', LOW: 'success', MEDIUM: 'warning', HIGH: 'danger' })[r] || 'info'
const riskLabel = (r) => RISK_LABELS[r] || r
const sourceTagType = (s) =>
  ({
    case: 'primary',
    psychology: 'success',
    policy: 'warning',
    success: 'info'
  })[s] || 'info'
const sourceLabel = (s) => SOURCE_LABELS[s] || s
const dimensionLabel = (d) => DIMENSION_LABELS[d] || d
const displayValue = (value) => value ?? '-'

const formatScore = (value) => {
  const n = Number(value)
  return Number.isFinite(n) ? n.toFixed(3) : '-'
}
const formatTime = (value) => (value ? String(value).replace('T', ' ').slice(0, 19) : '')
</script>

<style scoped lang="scss">
.report-shell {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.feedback-card {
  .fb-hint {
    margin-left: 12px;
    font-size: 12px;
    color: var(--text-color-secondary);
  }
  .fb-form {
    max-width: 560px;
    margin-top: 8px;
  }
}

.summary-panel,
.meta-strip,
.pipeline-panel {
  background: var(--bg-color-container);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-base);
  box-shadow: var(--shadow-1);
}

.summary-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 520px;
  gap: 20px;
  padding: 22px 24px;
}

.summary-label {
  color: var(--text-color-secondary);
  font-size: 13px;
}

.summary-title {
  margin-top: 8px;
  color: var(--text-color);
  font-size: 22px;
  font-weight: 700;
  line-height: 1.35;
}

.summary-desc {
  margin-top: 10px;
  max-width: 780px;
  color: var(--text-color-regular);
  line-height: 1.7;
}

.summary-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.metric-tile {
  min-height: 74px;
  padding: 14px;
  background: #f8fafc;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-base);

  > span:not(.el-tag) {
    display: block;
    margin-bottom: 10px;
    color: var(--text-color-secondary);
    font-size: 12px;
  }

  strong {
    color: var(--text-color);
    font-size: 20px;
  }
}

.score-value {
  &.is-high {
    color: #d64545;
  }

  &.is-medium {
    color: #d9822b;
  }

  &.is-low {
    color: #2f9e44;
  }
}

.meta-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  padding: 16px 24px;

  > div {
    min-width: 0;
    border-right: 1px solid var(--border-color);

    &:last-child {
      border-right: none;
    }
  }

  span {
    display: block;
    margin-bottom: 6px;
    color: var(--text-color-secondary);
    font-size: 12px;
  }

  strong {
    color: var(--text-color);
    font-weight: 650;
  }
}

.pipeline-panel {
  padding: 20px 24px 4px;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;

  h3 {
    margin: 0;
    color: var(--text-color);
    font-size: 18px;
  }

  p {
    margin: 6px 0 0;
    color: var(--text-color-secondary);
  }
}

.report-timeline {
  padding-right: 4px;
}

.stage-card {
  padding: 16px;
  margin-bottom: 18px;
  background: #fff;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-base);
}

.stage-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;

  h4 {
    display: inline-flex;
    margin: 0;
    color: var(--text-color);
    font-size: 16px;
  }

  p {
    margin: 6px 0 0 36px;
    color: var(--text-color-secondary);
  }
}

.stage-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 22px;
  margin-right: 8px;
  color: var(--primary-color);
  background: var(--primary-color-light);
  border-radius: 4px;
  font-size: 12px;
  font-weight: 700;
}

.stage-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.risk-layout {
  display: grid;
  grid-template-columns: 128px minmax(0, 1fr);
  gap: 16px;
}

.risk-score-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 104px;
  background: #f8fafc;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-base);

  strong {
    font-size: 34px;
    line-height: 1;
  }

  span {
    margin-top: 8px;
    color: var(--text-color-secondary);
    font-size: 12px;
  }
}

.risk-copy {
  p {
    margin: 12px 0 0;
    color: var(--text-color-regular);
    line-height: 1.7;
  }
}

.content-block,
.tag-block {
  margin-top: 16px;
}

.block-title {
  margin-bottom: 10px;
  color: var(--text-color);
  font-weight: 650;
}

.tag-block {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;

  .block-title {
    width: 100%;
    margin-bottom: 2px;
  }
}

.tag-inline {
  margin-right: 6px;
  margin-bottom: 4px;
}

.notice {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 12px;
  padding: 12px 14px;
  border-radius: var(--radius-base);
  line-height: 1.7;

  strong {
    color: var(--text-color);
  }

  span {
    color: var(--text-color-regular);
  }

  &.warning {
    background: #fff7ed;
    border: 1px solid #fed7aa;
  }

  &.success {
    background: #f0fdf4;
    border: 1px solid #bbf7d0;
  }

  &.danger {
    background: #fef2f2;
    border: 1px solid #fecaca;
  }
}

.report-collapse {
  --el-collapse-border-color: var(--border-color);
}

.collapse-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;

  ul {
    margin: 8px 0 0;
    padding-left: 18px;
    color: var(--text-color-regular);
    line-height: 1.8;
  }
}

.readonly-box {
  min-height: 96px;
  padding: 12px;
  color: var(--text-color-regular);
  background: #f8fafc;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-base);
  line-height: 1.8;
  white-space: pre-wrap;
}

.suggestion-list {
  margin: 0;
  padding-left: 18px;
  color: var(--text-color-regular);
  line-height: 1.8;
}

@media (max-width: 1100px) {
  .summary-panel {
    grid-template-columns: 1fr;
  }

  .summary-metrics,
  .meta-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .meta-strip > div {
    padding: 8px 0;
    border-right: none;
  }
}

@media (max-width: 720px) {
  .page-header,
  .stage-head,
  .panel-head {
    flex-direction: column;
  }

  .summary-metrics,
  .meta-strip,
  .risk-layout,
  .collapse-grid {
    grid-template-columns: 1fr;
  }

  .notice {
    grid-template-columns: 1fr;
  }
}
</style>
