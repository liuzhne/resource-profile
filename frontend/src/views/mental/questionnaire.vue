<template>
  <div class="page-container questionnaire-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">问卷管理</h1>
        <div class="page-subtitle">
          维护心理测评问卷、题目设计与完成情况，用于支撑学生心理状态动态评估。
        </div>
      </div>
      <el-button type="primary" :icon="Plus" @click="handleAdd"> 新建问卷 </el-button>
    </div>

    <div class="questionnaire-summary">
      <div class="summary-card">
        <span>问卷总量</span>
        <strong>{{ total }}</strong>
        <p>系统内已配置问卷</p>
      </div>
      <div class="summary-card is-success">
        <span>当前页进行中</span>
        <strong>{{ questionnaireSummary.active }}</strong>
        <p>可供学生填写</p>
      </div>
      <div class="summary-card is-warning">
        <span>当前页未开始</span>
        <strong>{{ questionnaireSummary.pending }}</strong>
        <p>等待开放填写</p>
      </div>
      <div class="summary-card is-muted">
        <span>当前页已结束</span>
        <strong>{{ questionnaireSummary.ended }}</strong>
        <p>可查看完成结果</p>
      </div>
    </div>

    <el-card class="table-card questionnaire-table-card">
      <template #header>
        <div class="card-header">
          <div>
            <span>问卷列表</span>
            <span class="result-count">共 {{ total }} 条</span>
          </div>
          <span class="header-hint">设计题目后即可进入测评流程</span>
        </div>
      </template>

      <el-table v-loading="loading" :data="questionnaires" stripe empty-text="暂无问卷数据">
        <el-table-column type="index" width="56" />
        <el-table-column prop="title" label="问卷" min-width="240">
          <template #default="{ row }">
            <div class="questionnaire-title">{{ row.title || '-' }}</div>
            <div class="questionnaire-desc">
              {{ row.description || '暂无描述' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="type" label="类型" width="120">
          <template #default="{ row }">
            <el-tag effect="plain">{{ row.type || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="questions" label="题目数" width="100" align="center">
          <template #default="{ row }">
            <strong class="question-count">{{ row.questions || 0 }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="plain">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开放周期" min-width="210">
          <template #default="{ row }">
            <div class="period-row"><span>开始</span>{{ row.startTime || '-' }}</div>
            <div class="period-row"><span>结束</span>{{ row.endTime || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="380" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)"> 查看 </el-button>
            <el-button link type="primary" :icon="EditPen" @click="handleDesign(row)">
              设计题目
            </el-button>
            <el-button link type="primary" :icon="DataAnalysis" @click="handleResult(row)">
              完成情况
            </el-button>
            <el-button link type="primary" @click="handleEdit(row)"> 编辑 </el-button>
            <el-button link type="danger" @click="handleDelete(row)"> 删除 </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > 0"
        class="pagination"
        :current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="handlePageChange"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑问卷' : '新建问卷'" width="560px">
      <el-form :model="formData" label-width="86px">
        <el-form-item label="问卷标题">
          <el-input v-model="formData.title" placeholder="请输入问卷标题" />
        </el-form-item>
        <el-form-item label="问卷类型">
          <el-select v-model="formData.type" placeholder="请选择类型">
            <el-option label="普查" value="普查" />
            <el-option label="焦虑" value="焦虑" />
            <el-option label="抑郁" value="抑郁" />
            <el-option label="专题调查" value="专题调查" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="3"
            placeholder="请输入问卷用途或适用范围"
          />
        </el-form-item>
        <el-form-item label="题目数量">
          <el-input-number v-model="formData.questions" :min="1" />
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker
            v-model="formData.startTime"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择开始日期"
          />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker
            v-model="formData.endTime"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择结束日期"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="formData.status">
            <el-option label="未开始" :value="0" />
            <el-option label="进行中" :value="1" />
            <el-option label="已结束" :value="2" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="viewDialogVisible" title="问卷详情" width="500px">
      <el-descriptions v-if="viewData" :column="1" border>
        <el-descriptions-item label="问卷标题">{{ viewData.title }}</el-descriptions-item>
        <el-descriptions-item label="问卷类型">{{ viewData.type }}</el-descriptions-item>
        <el-descriptions-item label="描述">{{ viewData.description }}</el-descriptions-item>
        <el-descriptions-item label="题目数量">{{ viewData.questions }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ viewData.startTime }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ viewData.endTime }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusLabel(viewData.status) }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, EditPen, DataAnalysis } from '@element-plus/icons-vue'
import {
  getQuestionnaireList,
  getQuestionnaireDetail,
  createQuestionnaire,
  updateQuestionnaire,
  deleteQuestionnaire
} from '@/api/mental'

const router = useRouter()
const handleDesign = (row) => router.push(`/mental/questionnaire/design/${row.id}`)
const handleResult = (row) => router.push(`/mental/questionnaire/result/${row.id}`)

const loading = ref(false)
const questionnaires = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(null)
const formData = reactive({
  title: '',
  type: '',
  description: '',
  questions: 20,
  startTime: '',
  endTime: '',
  status: 0
})

const questionnaireSummary = computed(() => ({
  active: questionnaires.value.filter((item) => item.status === 1).length,
  pending: questionnaires.value.filter((item) => item.status === 0).length,
  ended: questionnaires.value.filter((item) => item.status === 2).length
}))

const statusLabel = (status) => {
  const map = { 0: '未开始', 1: '进行中', 2: '已结束' }
  return map[status] ?? '未知'
}

const statusTagType = (status) => {
  const map = { 0: 'info', 1: 'success', 2: 'warning' }
  return map[status] ?? 'info'
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getQuestionnaireList({
      page: currentPage.value,
      size: pageSize.value
    })
    const data = res.data || {}
    questionnaires.value = data.records || []
    total.value = Number(data.total) || 0
  } catch (e) {
    console.error('获取问卷列表失败', e)
  } finally {
    loading.value = false
  }
}

const handlePageChange = (page) => {
  currentPage.value = page
  fetchData()
}

const resetForm = () => {
  formData.title = ''
  formData.type = ''
  formData.description = ''
  formData.questions = 20
  formData.startTime = ''
  formData.endTime = ''
  formData.status = 0
}

const viewDialogVisible = ref(false)
const viewData = ref(null)

const handleView = async (row) => {
  try {
    const res = await getQuestionnaireDetail(row.id)
    viewData.value = res.data
    viewDialogVisible.value = true
  } catch (e) {
    console.error('获取问卷详情失败', e)
  }
}

const handleAdd = () => {
  isEdit.value = false
  editId.value = null
  resetForm()
  dialogVisible.value = true
}

const handleEdit = async (row) => {
  isEdit.value = true
  editId.value = row.id
  try {
    const res = await getQuestionnaireDetail(row.id)
    const data = res.data || {}
    formData.title = data.title || ''
    formData.type = data.type || ''
    formData.description = data.description || ''
    formData.questions = data.questions || 20
    formData.startTime = data.startTime || ''
    formData.endTime = data.endTime || ''
    formData.status = data.status ?? 0
    dialogVisible.value = true
  } catch (e) {
    console.error('获取问卷详情失败', e)
  }
}

const handleDelete = (row) => {
  ElMessageBox.confirm('删除后该问卷将不可恢复，请确认是否继续。', '确认删除问卷？', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    customClass: 'app-confirm-dialog',
    showClose: false
  })
    .then(async () => {
      try {
        await deleteQuestionnaire(row.id)
        ElMessage.success('删除成功')
        fetchData()
      } catch (e) {
        console.error('删除问卷失败', e)
      }
    })
    .catch(() => {})
}

const handleSubmit = async () => {
  try {
    if (isEdit.value && editId.value) {
      await updateQuestionnaire(editId.value, { ...formData })
      ElMessage.success('更新成功')
    } else {
      await createQuestionnaire({ ...formData })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchData()
  } catch (e) {
    console.error('提交问卷失败', e)
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped lang="scss">
.questionnaire-summary {
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
    font-size: 28px;
    line-height: 1;
  }

  p {
    margin: 12px 0 0;
    color: var(--text-color-muted);
    font-size: 12px;
  }

  &.is-success strong {
    color: #2f9e44;
  }

  &.is-warning strong {
    color: #d9822b;
  }

  &.is-muted strong {
    color: #64748b;
  }
}

.questionnaire-table-card {
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

.questionnaire-title {
  color: var(--text-color);
  font-weight: 650;
  line-height: 1.5;
}

.questionnaire-desc {
  max-width: 520px;
  color: var(--text-color-muted);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.question-count {
  color: var(--primary-color);
  font-size: 16px;
}

.period-row {
  color: var(--text-color-regular);
  line-height: 1.7;

  span {
    display: inline-block;
    width: 34px;
    margin-right: 8px;
    color: var(--text-color-muted);
    font-size: 12px;
  }
}

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}

@media (max-width: 1100px) {
  .questionnaire-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .questionnaire-summary {
    grid-template-columns: 1fr;
  }
}
</style>
