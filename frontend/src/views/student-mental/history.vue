<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <el-button link @click="$router.push('/student-mental/list')">← 返回</el-button>
          <span class="title">我的评估历史</span>
        </div>
      </template>

      <el-alert v-if="loadError" type="error" :closable="false" :title="loadError"
        ><el-button link @click="fetchData">重新加载</el-button></el-alert
      >
      <el-empty v-else-if="!loading && rows.length === 0" description="暂无评估记录" />
      <el-table v-else v-loading="loading" :data="rows" stripe>
        <el-table-column type="index" width="50" />
        <el-table-column prop="questionnaireTitle" label="问卷" min-width="160"
          ><template #default="{ row }">{{
            row.questionnaireTitle || `历史问卷 #${row.questionnaireId}`
          }}</template></el-table-column
        >
        <el-table-column prop="questionnaireType" label="类型" width="100" />
        <el-table-column v-if="canSeeRaw" prop="score" label="得分" width="100" />
        <el-table-column v-if="canSeeLevel" label="等级" width="120">
          <template #default="{ row }">
            <el-tag :type="levelTagColor(row.level)">{{ row.level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column
          v-if="canSeeRaw"
          prop="suggestion"
          label="建议"
          min-width="240"
          show-overflow-tooltip
        />
        <el-table-column prop="createTime" label="评估时间" width="180" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="$router.push(`/student-mental/result/${row.id}`)"
              >查看</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <el-alert
      v-if="!canSeeRaw"
      type="info"
      :closable="false"
      title="原始分数及专业建议按角色权限提供。不可见不代表零分、未测评或无建议。"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { studentMyHistory } from '@/api/mental'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
const canSeeRaw = computed(() =>
  userStore.userRoles.some((role: string) => ['admin', 'psychologist'].includes(role))
)
const canSeeLevel = computed(() =>
  userStore.userRoles.some((role: string) =>
    ['admin', 'psychologist', 'teacher', 'counselor', 'academic_advisor'].includes(role)
  )
)
const loading = ref(false)
const loadError = ref('')
const rows = ref<any[]>([])

const levelTagColor = (l: string) =>
  (({ 正常: 'success', 轻度: '', 中度: 'warning', 重度: 'danger', 高危: 'danger' }) as any)[l] ||
  'info'

const fetchData = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const res = await studentMyHistory(userStore.userInfo?.id)
    rows.value = res.data || []
  } catch (e) {
    loadError.value = (e as any)?.message || '加载历史记录失败，请重试'
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)
</script>

<style scoped lang="scss">
.card-header {
  display: flex;
  align-items: center;
  .title {
    font-size: 16px;
    font-weight: 600;
    margin-left: 8px;
  }
}
</style>
