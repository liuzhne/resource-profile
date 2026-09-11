<template>
  <div class="trace-page">
    <el-card class="trace-card" body-style="padding: 0; height: 100%;">
      <template #header>
        <div class="card-header">
          <span>LLM 全链路追踪（Langfuse）</span>
          <div class="actions">
            <el-link :href="langfuseUrl" target="_blank" type="primary">
              <el-icon><Link /></el-icon>
              新窗口打开
            </el-link>
            <el-button size="small" @click="reload">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>
      <div v-if="!langfuseUrl" class="empty">
        <el-empty description="未配置 Langfuse URL">
          <template #image>
            <el-icon size="60" color="#909399"><InfoFilled /></el-icon>
          </template>
          <div class="hint">
            <p>请在 <code>frontend/.env.local</code> 设置：</p>
            <pre>VITE_LANGFUSE_URL=http://localhost:3001</pre>
            <p class="muted">
              并启动 langfuse 容器：<code>docker-compose --profile langfuse up -d</code>
            </p>
          </div>
        </el-empty>
      </div>
      <iframe
        v-else
        ref="iframeRef"
        :src="langfuseUrl"
        class="trace-iframe"
        sandbox="allow-same-origin allow-scripts allow-forms allow-popups"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { InfoFilled, Link, Refresh } from '@element-plus/icons-vue'

const langfuseUrl = import.meta.env.VITE_LANGFUSE_URL || ''
const iframeRef = ref(null)

const reload = () => {
  if (iframeRef.value) {
    iframeRef.value.src = langfuseUrl
  }
}
</script>

<style scoped>
.trace-page {
  height: calc(100vh - 120px);
  padding: 12px;
}
.trace-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.trace-card :deep(.el-card__body) {
  flex: 1;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.trace-iframe {
  width: 100%;
  height: 100%;
  border: none;
  display: block;
}
.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}
.hint {
  text-align: center;
  margin-top: 12px;
}
.hint pre {
  background: #f5f7fa;
  padding: 8px 12px;
  border-radius: 4px;
  display: inline-block;
  margin: 8px 0;
}
.hint .muted {
  color: #909399;
  font-size: 13px;
  margin-top: 8px;
}
</style>
