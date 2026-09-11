<template>
  <div class="error-container">
    <div class="error-content">
      <h1 class="error-code">404</h1>
      <div class="error-desc">抱歉，你访问的页面不存在</div>
      <div class="error-actions">
        <el-button type="primary" @click="goHome">返回首页</el-button>
        <el-button @click="goBack">返回上一页</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const goHome = () => {
  router.push('/')
}

const goBack = () => {
  router.back()
}
</script>

<style scoped lang="scss">
.error-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f2f5;

  .error-content {
    text-align: center;
    padding: 48px 32px;

    .error-code {
      font-size: 120px;
      font-weight: 600;
      color: var(--primary-color);
      margin-bottom: 24px;
      line-height: 1;
    }

    .error-desc {
      font-size: 18px;
      color: rgba(0, 0, 0, 0.45);
      margin-bottom: 32px;
    }

    .error-actions {
      .el-button {
        margin: 0 8px;
      }
    }
  }
}
</style>
