<template>
  <div class="page-container">
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
          <div>
            <el-button @click="rulesDialogVisible = true">编辑等级规则</el-button>
            <el-button type="primary" @click="openAddDialog">+ 新增题目</el-button>
          </div>
        </div>
      </template>

      <el-empty v-if="!loading && questions.length === 0" description="尚无题目，点击右上角新增" />
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
        <el-table-column label="操作" width="180" fixed="right">
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

        <template v-if="form.questionType !== 'text'">
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
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
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
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
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
  optionList: [{ label: '', score: 0 }] as any[]
})

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
  loading.value = true
  try {
    const res = await getQuestionnaireFull(questionnaireId)
    const d = res.data
    questionnaire.value = d.questionnaire
    questions.value = d.questions || []
    editingRules.value = (d.levelRules || []).map((r: any) => ({ ...r }))
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

const openEditDialog = (row: any) => {
  isEdit.value = true
  editId.value = row.id
  form.questionType = row.questionType
  form.content = row.content
  form.required = row.required
  form.sortOrder = row.sortOrder
  const opts = parseOpts(row.options)
  form.optionList = opts.length
    ? opts.map((o: any) => ({ label: o.label, score: o.score ?? 0 }))
    : [{ label: '', score: 0 }]
  dialogVisible.value = true
}

const addOption = () => {
  form.optionList.push({ label: '', score: 0 })
}

const removeOption = (idx: number) => {
  form.optionList.splice(idx, 1)
}

const handleSubmit = async () => {
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
  if (form.questionType !== 'text') {
    const cleanOpts = form.optionList.filter((o: any) => o.label && o.label.trim())
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

const handleDelete = (row: any) => {
  ElMessageBox.confirm(`确认删除题目"${row.content.slice(0, 20)}…"？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteQuestion(row.id)
      ElMessage.success('已删除')
      fetchData()
    })
    .catch(() => {})
}

const saveLevelRules = async () => {
  const cleaned = editingRules.value
    .filter((r: any) => r.level && r.level.trim())
    .map((r: any) => ({
      level: r.level.trim(),
      minScore: Number(r.minScore) || 0,
      suggestion: r.suggestion || ''
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
