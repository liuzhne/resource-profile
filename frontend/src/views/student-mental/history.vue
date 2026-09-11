<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <el-button link @click="$router.push('/student-mental/list')">← 返回</el-button>
          <span class="title">我的评估历史</span>
        </div>
      </template>

      <el-empty v-if="!loading && rows.length === 0" description="暂无评估记录" />
      <el-table v-else v-loading="loading" :data="rows" stripe>
        <el-table-column type="index" width="50" />
        <el-table-column prop="questionnaireId" label="问卷ID" width="100" />
        <el-table-column prop="score" label="得分" width="100" />
        <el-table-column label="等级" width="120">
          <template #default="{ row }">
            <el-tag :type="levelTagColor(row.level)">{{ row.level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="suggestion" label="建议" min-width="240" show-overflow-tooltip />
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { studentMyHistory } from '@/api/mental'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
const loading = ref(false)
const rows = ref<any[]>([])

const levelTagColor = (l: string) =>
  (({ 正常: 'success', 轻度: '', 中度: 'warning', 重度: 'danger', 高危: 'danger' }) as any)[l] ||
  'info'

const fetchData = async () => {
  loading.value = true
  try {
    const res = await studentMyHistory(userStore.userInfo?.id)
    rows.value = res.data || []
  } catch (e) {
    console.error('加载历史失败', e)
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
