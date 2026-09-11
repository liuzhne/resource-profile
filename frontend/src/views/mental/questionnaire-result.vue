<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <div>
            <el-button link @click="goBack">← 返回列表</el-button>
            <span class="title">{{ questionnaire?.title || '完成情况' }}</span>
          </div>
          <div class="stats">
            <span
              >已完成 <strong>{{ rows.length }}</strong> 人</span
            >
            <span v-if="avgScore !== null" v-permission="['psychologist', 'admin']"
              >| 平均分 <strong>{{ avgScore }}</strong></span
            >
          </div>
        </div>
      </template>

      <el-empty v-if="!loading && rows.length === 0" description="暂无学生提交" />
      <el-table v-else v-loading="loading" :data="rows" stripe>
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="studentNo" label="学号" width="120" />
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="deptName" label="学院" min-width="120" show-overflow-tooltip />
        <el-table-column prop="grade" label="年级" width="80" />
        <el-table-column prop="className" label="班级" width="100" />
        <!-- 原始得分仅 psychologist/admin 可见；其它角色看 等级 列即可 -->
        <el-table-column
          v-if="hasRole(['psychologist', 'admin'])"
          prop="score"
          label="得分"
          width="80"
          sortable
        />
        <el-table-column label="等级" width="100">
          <template #default="{ row }">
            <el-tag :type="levelTagColor(row.level)">{{ row.level || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="submitTime" label="提交时间" width="180" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCompletionList, getQuestionnaireDetail } from '@/api/mental'
import { usePermission } from '@/directives/permission'

const { hasRole } = usePermission()

const route = useRoute()
const router = useRouter()
const questionnaireId = Number(route.params.id)

const loading = ref(false)
const rows = ref<any[]>([])
const questionnaire = ref<any>(null)

const avgScore = computed(() => {
  if (rows.value.length === 0) return null
  const sum = rows.value.reduce((acc, r) => acc + (Number(r.score) || 0), 0)
  return Math.round((sum / rows.value.length) * 10) / 10
})

const levelTagColor = (l: string) => {
  return (
    ({ 正常: 'success', 轻度: '', 中度: 'warning', 重度: 'danger', 高危: 'danger' } as any)[l] ||
    'info'
  )
}

const fetchData = async () => {
  loading.value = true
  try {
    const [meta, completion] = await Promise.all([
      getQuestionnaireDetail(questionnaireId),
      getCompletionList(questionnaireId)
    ])
    questionnaire.value = meta.data
    rows.value = completion.data || []
  } catch (e) {
    console.error('加载完成情况失败', e)
  } finally {
    loading.value = false
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
  .stats {
    color: var(--el-text-color-secondary);
    span {
      margin-left: 12px;
    }
  }
}
</style>
