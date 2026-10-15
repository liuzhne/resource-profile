<template>
  <div class="page-container">
    <el-alert v-if="loadError" type="error" :closable="false" :title="loadError"
      ><el-button link @click="fetchData">重新加载</el-button></el-alert
    >
    <el-card>
      <template #header>
        <div class="card-header">
          <div>
            <el-button link @click="goBack">← 返回列表</el-button>
            <span class="title">{{ questionnaire?.title || '问卷设计' }}</span>
            <el-tag v-if="questionnaire?.type" style="margin-left: 8px">{{
              questionnaire.type
            }}</el-tag>
          </div>
          <div v-if="canWrite">
            <el-button @click="importVisible = true">导入 Excel</el-button>
            <el-button @click="rulesDialogVisible = true">编辑等级规则</el-button>
            <el-button type="primary" @click="openAddDialog">+ 新增题目</el-button>
          </div>
        </div>
      </template>

      <el-empty
        v-if="!loading && !loadError && questions.length === 0"
        :description="canWrite ? '尚无题目，点击右上角新增' : '尚无题目'"
      />
      <el-table v-else v-loading="loading" :data="questions" stripe>
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="content" label="题干" min-width="280" show-overflow-tooltip />
        <el-table-column label="题型" width="120">
          <template #default="{ row }">
            <el-tag :type="typeTagColor(row.questionType)">{{
              typeLabel(row.questionType)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="选项数" width="80">
          <template #default="{ row }">{{ parseOpts(row.options).length }}</template>
        </el-table-column>
        <el-table-column label="必答" width="80">
          <template #default="{ row }">{{ row.required ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column v-if="canWrite" label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑题目对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑题目' : '新增题目'" width="640px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="题型">
          <el-select v-model="form.questionType" :disabled="isEdit">
            <el-option label="单选题" value="single_choice" />
            <el-option label="多选题" value="multiple_choice" />
            <el-option label="简答题" value="text" />
            <el-option label="量表题" value="scale" />
          </el-select>
        </el-form-item>
        <el-form-item label="题干">
          <el-input v-model="form.content" type="textarea" :rows="2" placeholder="请输入题目内容" />
        </el-form-item>
        <el-form-item label="必答">
          <el-switch v-model="form.required" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="1" />
        </el-form-item>

        <template
          v-if="form.questionType === 'single_choice' || form.questionType === 'multiple_choice'"
        >
          <el-divider>选项（每个选项可设分值）</el-divider>
          <div v-for="(opt, idx) in form.optionList" :key="idx" class="option-row">
            <el-input v-model="opt.label" placeholder="选项内容" style="flex: 1" />
            <el-input-number v-model="opt.score" :min="0" :max="100" controls-position="right" />
            <el-button
              link
              type="danger"
              :disabled="form.optionList.length <= 1"
              @click="removeOption(idx)"
              >×</el-button
            >
          </div>
          <el-button link type="primary" @click="addOption">+ 添加选项</el-button>
        </template>
        <template v-if="form.questionType === 'scale'">
          <el-form-item label="最小值"
            ><el-input-number v-model="form.scaleMin" :min="-1000" :max="form.scaleMax"
          /></el-form-item>
          <el-form-item label="最大值"
            ><el-input-number v-model="form.scaleMax" :min="form.scaleMin" :max="1000"
          /></el-form-item>
          <el-form-item label="最小标签"
            ><el-input v-model="form.scaleMinLabel" maxlength="200"
          /></el-form-item>
          <el-form-item label="最大标签"
            ><el-input v-model="form.scaleMaxLabel" maxlength="200"
          /></el-form-item>
          <el-form-item label="计分映射"
            ><el-input
              v-model="form.scaleRules"
              type="textarea"
              placeholder='留空时数值直接计分；可填写 {"1":5,"2":4,...}，必须覆盖范围内每个整数'
          /></el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="importVisible" title="导入题目（替换当前题库）" width="760px">
      <el-alert
        type="info"
        :closable="false"
        title="第一行为表头；列顺序：题号、题干、题型、选项A–F、分值A–F、必答。量表的选项A填写1~10范围、B/C填写两端标签，默认按数值计分。最多500题、10MB。"
      />
      <input type="file" accept=".xlsx,.xls" @change="chooseImport" />
      <el-button :disabled="!importFile" :loading="importing" @click="previewImport"
        >校验并预览</el-button
      >
      <p v-if="importPreview.length">
        校验通过，共 {{ importPreview.length }} 题。确认后替换当前题库；已有答卷保留提交时快照。
      </p>
      <el-table :data="importPreview" max-height="360">
        <el-table-column prop="sortOrder" label="题号" width="80" /><el-table-column
          prop="content"
          label="题干"
        />
        <el-table-column label="题型" width="100"
          ><template #default="{ row }">{{
            typeLabel(row.questionType)
          }}</template></el-table-column
        >
      </el-table>
      <template #footer
        ><el-button @click="importVisible = false">取消</el-button
        ><el-button
          type="primary"
          :disabled="!importPreview.length"
          :loading="importing"
          @click="applyImport"
          >确认替换题库</el-button
        ></template
      >
    </el-dialog>

    <!-- 等级规则编辑 -->
    <el-dialog v-model="rulesDialogVisible" title="等级规则（按总分映射）" width="640px">
      <el-alert type="info" :closable="false" style="margin-bottom: 12px">
        总分 ≥ 该等级 minScore 的最大者命中。规则越靠上分数越高（"正常"在最上）。
      </el-alert>
      <div v-for="(rule, idx) in editingRules" :key="idx" class="rule-row">
        <el-input v-model="rule.level" placeholder="等级名" style="width: 100px" />
        <el-input-number v-model="rule.minScore" :min="0" :max="9999" controls-position="right" />
        <el-input v-model="rule.suggestion" placeholder="建议" style="flex: 1" />
        <el-button link type="danger" @click="editingRules.splice(idx, 1)">×</el-button>
      </div>
      <el-button
        link
        type="primary"
        @click="editingRules.push({ level: '', minScore: 0, suggestion: '' })"
        >+ 添加规则</el-button
      >
      <template #footer>
        <el-button @click="rulesDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveLevelRules">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import { MENTAL_WRITE_ROLES } from '@/router/access'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getQuestionnaireFull,
  addQuestion,
  updateQuestion,
  deleteQuestion,
  updateQuestionnaire,
  previewQuestionImport,
  importQuestionFile
} from '@/api/mental'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const canWrite = computed(() =>
  userStore.userRoles.some((role) => MENTAL_WRITE_ROLES.includes(role))
)
const questionnaireId = computed(() => Number(route.params.id))
let loadSequence = 0

const loading = ref(false)
const loadError = ref('')
const questionnaire = ref<any>(null)
const questions = ref<any[]>([])
const editingRules = ref<any[]>([])

const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const form = reactive({
  questionType: 'single_choice',
  content: '',
  required: 1,
  sortOrder: 1,
  optionList: [{ label: '', value: '0', score: 0 }] as any[],
  scaleMin: 1,
  scaleMax: 10,
  scaleMinLabel: '',
  scaleMaxLabel: '',
  scaleRules: ''
})
const importVisible = ref(false)
const importFile = ref<File | null>(null)
const importPreview = ref<any[]>([])
const importing = ref(false)
const chooseImport = (event: Event) => {
  importFile.value = (event.target as HTMLInputElement).files?.[0] || null
  importPreview.value = []
}
const previewImport = async () => {
  if (!importFile.value) return
  const id = questionnaireId.value
  importing.value = true
  try {
    const response = await previewQuestionImport(id, importFile.value)
    if (id === questionnaireId.value) importPreview.value = response.data || []
  } finally {
    importing.value = false
  }
}
const applyImport = async () => {
  if (!canWrite.value || !importFile.value || !importPreview.value.length) return
  const id = questionnaireId.value
  importing.value = true
  try {
    await importQuestionFile(id, importFile.value)
    if (id === questionnaireId.value) {
      importVisible.value = false
      await fetchData()
      ElMessage.success('题库已导入')
    }
  } finally {
    importing.value = false
  }
}

const rulesDialogVisible = ref(false)

const typeLabel = (t: string) =>
  (({ single_choice: '单选', multiple_choice: '多选', text: '简答', scale: '量表' }) as any)[t] || t
const typeTagColor = (t: string) =>
  (({ single_choice: '', multiple_choice: 'success', text: 'info', scale: 'warning' }) as any)[t] ||
  ''

const parseOpts = (raw: any): any[] => {
  if (!raw) return []
  if (Array.isArray(raw)) return raw
  try {
    return JSON.parse(raw)
  } catch {
    return []
  }
}

const fetchData = async () => {
  const id = questionnaireId.value
  const sequence = ++loadSequence
  loading.value = true
  loadError.value = ''
  try {
    const res = await getQuestionnaireFull(id)
    if (sequence !== loadSequence || id !== questionnaireId.value) return
    const d = res.data
    questionnaire.value = d.questionnaire
    questions.value = d.questions || []
    editingRules.value = (d.levelRules || []).map((r: any) => ({ ...r }))
  } catch (e) {
    if (sequence === loadSequence) loadError.value = (e as any)?.message || '数据加载失败，请重试'
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

const resetForm = () => {
  form.questionType = 'single_choice'
  form.content = ''
  form.required = 1
  form.sortOrder = questions.value.length + 1
  form.optionList = [{ label: '', value: '0', score: 0 }]
  form.scaleMin = 1
  form.scaleMax = 10
  form.scaleMinLabel = ''
  form.scaleMaxLabel = ''
  form.scaleRules = ''
}

const openAddDialog = () => {
  isEdit.value = false
  editId.value = null
  resetForm()
  dialogVisible.value = true
}

const openEditDialog = (row: any) => {
  isEdit.value = true
  editId.value = row.id
  form.questionType = row.questionType
  form.content = row.content
  form.required = row.required
  form.sortOrder = row.sortOrder
  const opts = parseOpts(row.options)
  let rules: any = {}
  let labels: any = {}
  try {
    rules = JSON.parse(row.scoringRules || '{}')
    labels = JSON.parse(row.scaleLabels || '{}')
  } catch {
    ElMessage.error('题目配置格式无效，请先修正')
    return
  }
  form.optionList = opts.length
    ? opts.map((o: any, index: number) => ({
        label: o.label,
        value: String(o.value ?? index),
        score: o.score ?? rules[String(o.value ?? index)] ?? null
      }))
    : [{ label: '', value: '0', score: 0 }]
  form.scaleMin = row.scaleMin ?? 1
  form.scaleMax = row.scaleMax ?? 10
  form.scaleMinLabel = labels.min || ''
  form.scaleMaxLabel = labels.max || ''
  form.scaleRules = row.scoringRules || ''
  dialogVisible.value = true
}

const addOption = () => {
  form.optionList.push({ label: '', value: String(form.optionList.length), score: 0 })
}

const removeOption = (idx: number) => {
  form.optionList.splice(idx, 1)
}

const handleSubmit = async () => {
  if (!canWrite.value || !questionnaire.value || questionnaire.value.id !== questionnaireId.value)
    return
  const id = questionnaireId.value
  if (!form.content.trim()) {
    ElMessage.warning('请输入题干')
    return
  }
  const payload: any = {
    content: form.content,
    questionType: form.questionType,
    required: form.required,
    sortOrder: form.sortOrder
  }
  if (form.questionType === 'single_choice' || form.questionType === 'multiple_choice') {
    const cleanOpts = form.optionList.filter((o: any) => o.label && o.label.trim())
    if (cleanOpts.length < 2) {
      ElMessage.warning('选择题至少需要 2 个选项')
      return
    }
    if (cleanOpts.some((option: any) => !Number.isInteger(option.score))) {
      ElMessage.warning('每个选项需要整数分值')
      return
    }
    payload.options = JSON.stringify(
      cleanOpts.map((option: any, index: number) => ({ ...option, value: String(index) }))
    )
    payload.scoringRules = null
    payload.scaleMin = null
    payload.scaleMax = null
    payload.scaleLabels = null
  } else if (form.questionType === 'scale') {
    payload.options = null
    payload.scaleMin = form.scaleMin
    payload.scaleMax = form.scaleMax
    payload.scaleLabels = JSON.stringify({ min: form.scaleMinLabel, max: form.scaleMaxLabel })
    try {
      if (form.scaleRules.trim()) JSON.parse(form.scaleRules)
      payload.scoringRules = form.scaleRules.trim() || null
    } catch {
      ElMessage.warning('计分映射需要合法JSON对象')
      return
    }
  } else {
    payload.options = null
    payload.scoringRules = null
    payload.scaleMin = null
    payload.scaleMax = null
    payload.scaleLabels = null
  }
  try {
    if (isEdit.value && editId.value) {
      await updateQuestion(editId.value, payload)
      ElMessage.success('已更新')
    } else {
      await addQuestion(id, payload)
      ElMessage.success('已添加')
    }
    if (id === questionnaireId.value) {
      dialogVisible.value = false
      fetchData()
    }
  } catch (e) {
    console.error('保存题目失败', e)
  }
}

const handleDelete = (row: any) => {
  const id = questionnaireId.value
  ElMessageBox.confirm(`确认删除题目"${row.content.slice(0, 20)}…"？`, '提示', { type: 'warning' })
    .then(async () => {
      if (id !== questionnaireId.value || !canWrite.value) return
      await deleteQuestion(row.id)
      if (id !== questionnaireId.value) return
      ElMessage.success('已删除')
      fetchData()
    })
    .catch(() => {})
}

const saveLevelRules = async () => {
  const id = questionnaireId.value
  if (!canWrite.value || questionnaire.value?.id !== id) return
  const cleaned = editingRules.value
    .filter((r: any) => r.level && r.level.trim())
    .map((r: any) => ({
      level: r.level.trim(),
      minScore: Number(r.minScore) || 0,
      suggestion: r.suggestion || ''
    }))
  try {
    await updateQuestionnaire(id, {
      ...questionnaire.value,
      levelRules: JSON.stringify(cleaned)
    })
    if (id !== questionnaireId.value) return
    ElMessage.success('等级规则已保存')
    rulesDialogVisible.value = false
    fetchData()
  } catch (e) {
    console.error('保存等级规则失败', e)
  }
}

const goBack = () => router.push('/mental/questionnaire')

watch(
  questionnaireId,
  () => {
    dialogVisible.value = false
    rulesDialogVisible.value = false
    importVisible.value = false
    importFile.value = null
    importPreview.value = []
    questionnaire.value = null
    questions.value = []
    editingRules.value = []
    resetForm()
    fetchData()
  },
  { immediate: true }
)
</script>

<style scoped lang="scss">
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  .title {
    font-size: 16px;
    font-weight: 600;
    margin-left: 8px;
  }
}
.option-row,
.rule-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}
</style>
