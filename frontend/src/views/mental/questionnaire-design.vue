<template>
  <div class="page-container questionnaire-design-page">
    <div class="page-header">
      <div>
        <el-button link :icon="ArrowLeft" class="back-link" @click="goBack">
          返回问卷列表
        </el-button>
        <h1 class="page-title">{{ questionnaire?.title || '问卷设计' }}</h1>
        <div class="page-subtitle">
          {{ questionnaire?.description || '配置问卷题目、选项分值与等级规则。' }}
        </div>
      </div>
      <div class="page-actions">
        <el-button :icon="Setting" @click="rulesDialogVisible = true"> 编辑等级规则 </el-button>
        <el-button type="primary" :icon="Plus" @click="openAddDialog"> 新增题目 </el-button>
      </div>
    </div>

    <div class="design-summary">
      <div class="summary-card">
        <span>问卷类型</span>
        <strong>{{ questionnaire?.type || '-' }}</strong>
        <p>用于区分测评场景</p>
      </div>
      <div class="summary-card is-primary">
        <span>题目总数</span>
        <strong>{{ questions.length }}</strong>
        <p>当前已配置题目</p>
      </div>
      <div class="summary-card is-success">
        <span>选择题</span>
        <strong>{{ designSummary.choice }}</strong>
        <p>包含单选、多选、量表</p>
      </div>
      <div class="summary-card is-warning">
        <span>等级规则</span>
        <strong>{{ editingRules.length }}</strong>
        <p>按总分映射风险等级</p>
      </div>
    </div>

    <el-card class="table-card question-table-card">
      <template #header>
        <div class="card-header">
          <div>
            <span>题目配置</span>
            <span class="result-count">共 {{ questions.length }} 题</span>
          </div>
          <span class="header-hint">建议按测评流程排序并校准分值</span>
        </div>
      </template>

      <el-empty v-if="!loading && questions.length === 0" description="尚无题目，请先新增题目" />
      <el-table v-else v-loading="loading" :data="questions" stripe>
        <el-table-column type="index" label="#" width="56" />
        <el-table-column label="题目内容" min-width="320">
          <template #default="{ row }">
            <div class="question-content">{{ row.content || '-' }}</div>
            <div class="question-meta">
              排序 {{ row.sortOrder || '-' }} ·
              {{ row.required ? '必答' : '选答' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="题型" width="120">
          <template #default="{ row }">
            <el-tag :type="typeTagColor(row.questionType)" effect="plain">
              {{ typeLabel(row.questionType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="选项数" width="90" align="center">
          <template #default="{ row }">
            <strong class="option-count">{{ parseOpts(row.options).length }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="EditPen" @click="openEditDialog(row)">
              编辑
            </el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑题目' : '新增题目'" width="680px">
      <el-form :model="form" label-width="86px">
        <el-form-item label="题型">
          <el-select v-model="form.questionType" :disabled="isEdit">
            <el-option label="单选题" value="single_choice" />
            <el-option label="多选题" value="multiple_choice" />
            <el-option label="简答题" value="text" />
          </el-select>
        </el-form-item>
        <el-form-item label="题干">
          <el-input v-model="form.content" type="textarea" :rows="3" placeholder="请输入题目内容" />
        </el-form-item>
        <el-form-item label="必答">
          <el-switch v-model="form.required" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="1" />
        </el-form-item>

        <template v-if="form.questionType !== 'text'">
          <el-divider>选项与分值</el-divider>
          <div class="option-list">
            <div v-for="(opt, idx) in form.optionList" :key="idx" class="option-row">
              <el-input v-model="opt.label" placeholder="选项内容" />
              <el-input-number v-model="opt.score" :min="0" :max="100" controls-position="right" />
              <el-button
                link
                type="danger"
                :disabled="form.optionList.length <= 1"
                @click="removeOption(idx)"
              >
                删除
              </el-button>
            </div>
          </div>
          <el-button link type="primary" :icon="Plus" @click="addOption"> 添加选项 </el-button>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rulesDialogVisible" title="等级规则" width="720px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="总分命中不低于 minScore 的最高等级，建议从高风险到低风险依次配置。"
        class="rules-alert"
      />
      <div class="rule-list">
        <div v-for="(rule, idx) in editingRules" :key="idx" class="rule-row">
          <el-input v-model="rule.level" placeholder="等级名" style="width: 120px" />
          <el-input-number v-model="rule.minScore" :min="0" :max="9999" controls-position="right" />
          <el-input v-model="rule.suggestion" placeholder="处理建议" />
          <el-button link type="danger" @click="editingRules.splice(idx, 1)"> 删除 </el-button>
        </div>
      </div>
      <el-button
        link
        type="primary"
        :icon="Plus"
        @click="editingRules.push({ level: '', minScore: 0, suggestion: '' })"
      >
        添加规则
      </el-button>
      <template #footer>
        <el-button @click="rulesDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveLevelRules">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Delete, EditPen, Plus, Setting } from '@element-plus/icons-vue'
import {
  getQuestionnaireFull,
  addQuestion,
  updateQuestion,
  deleteQuestion,
  updateQuestionnaire
} from '@/api/mental'

const route = useRoute()
const router = useRouter()
const questionnaireId = Number(route.params.id)

const loading = ref(false)
const questionnaire = ref(null)
const questions = ref([])
const editingRules = ref([])

const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(null)
const form = reactive({
  questionType: 'single_choice',
  content: '',
  required: 1,
  sortOrder: 1,
  optionList: [{ label: '', score: 0 }]
})

const rulesDialogVisible = ref(false)

const designSummary = computed(() => ({
  choice: questions.value.filter((item) => item.questionType !== 'text').length
}))

const typeLabel = (type) =>
  ({
    single_choice: '单选',
    multiple_choice: '多选',
    text: '简答',
    scale: '量表'
  })[type] ||
  type ||
  '-'
const typeTagColor = (type) =>
  ({
    single_choice: 'primary',
    multiple_choice: 'success',
    text: 'info',
    scale: 'warning'
  })[type] || 'info'

const parseOpts = (raw) => {
  if (!raw) return []
  if (Array.isArray(raw)) return raw
  try {
    return JSON.parse(raw)
  } catch {
    return []
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getQuestionnaireFull(questionnaireId)
    const data = res.data || {}
    questionnaire.value = data.questionnaire || null
    questions.value = data.questions || []
    editingRules.value = (data.levelRules || []).map((rule) => ({ ...rule }))
  } catch (e) {
    console.error('加载问卷失败', e)
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  form.questionType = 'single_choice'
  form.content = ''
  form.required = 1
  form.sortOrder = questions.value.length + 1
  form.optionList = [{ label: '', score: 0 }]
}

const openAddDialog = () => {
  isEdit.value = false
  editId.value = null
  resetForm()
  dialogVisible.value = true
}

const openEditDialog = (row) => {
  isEdit.value = true
  editId.value = row.id
  form.questionType = row.questionType
  form.content = row.content || ''
  form.required = row.required
  form.sortOrder = row.sortOrder || 1
  const opts = parseOpts(row.options)
  form.optionList = opts.length
    ? opts.map((option) => ({
        label: option.label,
        score: option.score ?? 0
      }))
    : [{ label: '', score: 0 }]
  dialogVisible.value = true
}

const addOption = () => {
  form.optionList.push({ label: '', score: 0 })
}

const removeOption = (idx) => {
  form.optionList.splice(idx, 1)
}

const handleSubmit = async () => {
  if (!form.content.trim()) {
    ElMessage.warning('请输入题干')
    return
  }
  const payload = {
    content: form.content,
    questionType: form.questionType,
    required: form.required,
    sortOrder: form.sortOrder
  }
  if (form.questionType !== 'text') {
    const cleanOpts = form.optionList.filter((option) => option.label && option.label.trim())
    if (cleanOpts.length < 2) {
      ElMessage.warning('选择题至少需要 2 个选项')
      return
    }
    payload.options = JSON.stringify(cleanOpts)
  } else {
    payload.options = null
  }
  try {
    if (isEdit.value && editId.value) {
      await updateQuestion(editId.value, payload)
      ElMessage.success('已更新')
    } else {
      await addQuestion(questionnaireId, payload)
      ElMessage.success('已添加')
    }
    dialogVisible.value = false
    fetchData()
  } catch (e) {
    console.error('保存题目失败', e)
  }
}

const handleDelete = (row) => {
  ElMessageBox.confirm(`删除后不可恢复：${(row.content || '').slice(0, 24)}…`, '确认删除题目？', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    customClass: 'app-confirm-dialog',
    showClose: false
  })
    .then(async () => {
      await deleteQuestion(row.id)
      ElMessage.success('已删除')
      fetchData()
    })
    .catch(() => {})
}

const saveLevelRules = async () => {
  const cleaned = editingRules.value
    .filter((rule) => rule.level && rule.level.trim())
    .map((rule) => ({
      level: rule.level.trim(),
      minScore: Number(rule.minScore) || 0,
      suggestion: rule.suggestion || ''
    }))
  try {
    await updateQuestionnaire(questionnaireId, {
      ...questionnaire.value,
      levelRules: JSON.stringify(cleaned)
    })
    ElMessage.success('等级规则已保存')
    rulesDialogVisible.value = false
    fetchData()
  } catch (e) {
    console.error('保存等级规则失败', e)
  }
}

const goBack = () => router.push('/mental/questionnaire')

onMounted(fetchData)
</script>

<style scoped lang="scss">
.questionnaire-design-page {
  .back-link {
    padding: 0;
    margin-bottom: 8px;
  }

  .page-actions {
    display: flex;
    gap: 10px;
    flex-shrink: 0;
  }
}

.design-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 14px;
}

.summary-card {
  min-height: 112px;
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
    font-size: 26px;
    line-height: 1;
  }

  p {
    margin: 12px 0 0;
    color: var(--text-color-muted);
    font-size: 12px;
  }

  &.is-primary strong {
    color: var(--primary-color);
  }

  &.is-success strong {
    color: #2f9e44;
  }

  &.is-warning strong {
    color: #d9822b;
  }
}

.question-table-card {
  :deep(.el-card__body) {
    padding-top: 0;
  }
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;

  .result-count,
  .header-hint {
    color: var(--text-color-muted);
    font-size: 12px;
    font-weight: 400;
  }

  .result-count {
    margin-left: 10px;
  }
}

.question-content {
  color: var(--text-color);
  font-weight: 650;
  line-height: 1.5;
}

.question-meta {
  color: var(--text-color-muted);
  font-size: 12px;
}

.option-count {
  color: var(--primary-color);
}

.option-list,
.rule-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 8px;
}

.option-row,
.rule-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 140px 56px;
  gap: 8px;
  align-items: center;
}

.rule-row {
  grid-template-columns: 120px 140px minmax(0, 1fr) 56px;
}

.rules-alert {
  margin-bottom: 14px;
}

@media (max-width: 1100px) {
  .design-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .page-header {
    flex-direction: column;
  }

  .design-summary,
  .option-row,
  .rule-row {
    grid-template-columns: 1fr;
  }
}
</style>
