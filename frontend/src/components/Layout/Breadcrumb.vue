<template>
  <el-breadcrumb separator="/">
    <el-breadcrumb-item v-for="(item, index) in breadcrumbs" :key="item.path">
      <span v-if="index === breadcrumbs.length - 1" class="no-redirect">{{ item.title }}</span>
      <a v-else @click.prevent="handleLink(item)">{{ item.title }}</a>
    </el-breadcrumb-item>
  </el-breadcrumb>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const breadcrumbs = ref([])

const getBreadcrumb = () => {
  const matched = route.matched.filter((item) => item.meta?.title)
  breadcrumbs.value = matched.map((item) => ({
    title: item.meta.title,
    path: item.path
  }))
}

const handleLink = (item) => {
  router.push(item.path)
}

watch(() => route.path, getBreadcrumb, { immediate: true })
</script>

<style scoped lang="scss">
:deep(.el-breadcrumb) {
  margin-left: 16px;
  font-size: 14px;

  .no-redirect {
    color: #97a8be;
    cursor: text;
  }
}
</style>
