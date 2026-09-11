<template>
  <el-breadcrumb separator="/">
    <el-breadcrumb-item v-for="(item, index) in breadcrumbs" :key="item.path">
      <span v-if="index === breadcrumbs.length - 1" class="no-redirect">{{ item.title }}</span>
      <a v-else @click.prevent="handleLink(item)">{{ item.title }}</a>
    </el-breadcrumb-item>
  </el-breadcrumb>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

interface Breadcrumb {
  title: string
  path: string
}

const route = useRoute()
const router = useRouter()
const breadcrumbs = ref<Breadcrumb[]>([])

const getBreadcrumb = () => {
  const matched = route.matched.filter((item) => item.meta?.title)
  breadcrumbs.value = matched.map((item) => ({
    title: item.meta.title as string,
    path: item.path
  }))
}

const handleLink = (item: Breadcrumb) => {
  router.push(item.path)
}

watch(() => route.path, getBreadcrumb, { immediate: true })
</script>

<style scoped lang="scss">
:deep(.el-breadcrumb) {
  font-size: 14px;
  line-height: 1;

  .el-breadcrumb__separator {
    color: rgba(60, 60, 67, 0.3);
    margin: 0 7px;
  }

  .el-breadcrumb__inner {
    color: rgba(60, 60, 67, 0.68);
    font-weight: 400;

    a:hover {
      color: var(--primary-color);
    }
  }

  // 末级为当前页：加粗且不可点
  .no-redirect {
    color: var(--text-color);
    font-weight: 600;
    cursor: text;
  }
}
</style>
