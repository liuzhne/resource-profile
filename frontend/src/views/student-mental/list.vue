<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>我的问卷</span>
          <el-button link type="primary" @click="$router.push('/student-mental/history')"
            >查看历史评估</el-button
          >
        </div>
      </template>

      <el-empty v-if="!loading && list.length === 0" description="当前没有可作答的问卷" />
      <el-row v-else v-loading="loading" :gutter="16">
        <el-col
          v-for="item in list"
          :key="item.id"
          :xs="24"
          :sm="12"
          :md="8"
          style="margin-bottom: 16px"
        >
          <el-card shadow="hover" class="q-card">
            <div class="q-title">{{ item.title }}</div>
            <div class="q-meta">
              <el-tag size="small">{{ item.type }}</el-tag>
              <span>共 {{ item.questions }} 题</span>
            </div>
            <div class="q-desc">{{ item.description || '（无描述）' }}</div>
            <div class="q-time">{{ item.startTime || '不限' }} ~ {{ item.endTime || '不限' }}</div>
            <div class="q-actions">
              <el-tag v-if="item.answered" type="success">已作答</el-tag>
              <el-tag v-else-if="!item.inWindow" type="info">不在作答时间</el-tag>
              <el-button
                v-if="!item.answered && item.inWindow"
                type="primary"
                size="small"
                @click="goTake(item.id)"
                >去作答</el-button
              >
              <el-button v-if="item.answered" size="small" @click="findAndOpenResult(item.id)"
                >查看结果</el-button
              >
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { studentListQuestionnaires, studentMyHistory } from '@/api/mental'
import { useUserStore } from '@/store/modules/user'

const router = useRouter()
const userStore = useUserStore()
const list = ref<any[]>([])
const loading = ref(false)

const fetchData = async () => {
  loading.value = true
  try {
    const userId = userStore.userInfo?.id
    if (!userId) {
      ElMessage.error('未登录或缺少用户信息')
      return
    }
    const res = await studentListQuestionnaires(userId)
    list.value = res.data || []
  } catch (e) {
    console.error('加载问卷失败', e)
  } finally {
    loading.value = false
  }
}

const goTake = (id: number) => {
  router.push(`/student-mental/take/${id}`)
}

const findAndOpenResult = async (questionnaireId: number) => {
  try {
    const userId = userStore.userInfo?.id
    const res = await studentMyHistory(userId)
    const found = (res.data || []).find((a: any) => a.questionnaireId === questionnaireId)
    if (found) {
      router.push(`/student-mental/result/${found.id}`)
    } else {
      ElMessage.warning('未找到该问卷的评估记录')
    }
  } catch (e) {
    console.error(e)
  }
}

onMounted(fetchData)
</script>

<style scoped lang="scss">
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.q-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  .q-title {
    font-size: 16px;
    font-weight: 600;
    margin-bottom: 8px;
  }
  .q-meta {
    display: flex;
    align-items: center;
    gap: 8px;
    color: var(--el-text-color-secondary);
    font-size: 13px;
    margin-bottom: 8px;
  }
  .q-desc {
    color: var(--el-text-color-regular);
    margin-bottom: 12px;
    min-height: 40px;
  }
  .q-time {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    margin-bottom: 12px;
  }
  .q-actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
  }
}
</style>
