<template>
  <div class="tags-view-container">
    <div class="tags-view-wrapper">
      <router-link
        v-for="tag in visitedViews"
        :key="tag.path"
        :to="tag.path"
        :class="['tags-view-item', { active: isActive(tag) }]"
      >
        {{ tag.title }}
        <el-icon v-if="!isAffix(tag)" @click.prevent.stop="closeSelectedTag(tag)">
          <Close />
        </el-icon>
      </router-link>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const visitedViews = ref([
  { name: 'Dashboard', path: '/dashboard', title: '数据面板', affix: true }
])

const isActive = (tag) => {
  return tag.path === route.path
}

const isAffix = (tag) => {
  return tag.affix
}

const addView = (view) => {
  if (visitedViews.value.some((v) => v.path === view.path)) return

  visitedViews.value.push({
    name: view.name,
    path: view.path,
    title: view.meta.title || 'no-name'
  })
}

const closeSelectedTag = (view) => {
  const index = visitedViews.value.findIndex((v) => v.path === view.path)
  visitedViews.value.splice(index, 1)

  if (isActive(view)) {
    const latestView = visitedViews.value.slice(-1)[0]
    if (latestView) {
      router.push(latestView.path)
    } else {
      router.push('/')
    }
  }
}

watch(
  () => route.path,
  () => {
    addView(route)
  },
  { immediate: true }
)
</script>

<style scoped lang="scss">
.tags-view-container {
  height: 38px;
  background: #fff;
  border-bottom: 1px solid var(--border-color);
  box-shadow: none;
  padding: 0 18px;

  .tags-view-wrapper {
    display: flex;
    height: 100%;
    overflow-x: auto;
    overflow-y: hidden;
    white-space: nowrap;
    scrollbar-width: none;

    &::-webkit-scrollbar {
      display: none;
    }

    .tags-view-item {
      display: inline-flex;
      align-items: center;
      flex: 0 0 auto;
      height: 26px;
      line-height: 26px;
      margin-top: 6px;
      margin-right: 8px;
      padding: 0 10px;
      background: var(--bg-color-subtle);
      border: 1px solid transparent;
      border-radius: 5px;
      color: var(--text-color-secondary);
      font-size: 12px;
      cursor: pointer;
      transition: all 0.3s;
      text-decoration: none;

      &:hover {
        color: var(--primary-color);
        background: var(--primary-color-light);
      }

      &.active {
        background-color: var(--primary-color-light);
        color: var(--primary-color);
        border-color: rgba(31, 95, 191, 0.18);

        &::before {
          content: '';
          background: var(--primary-color);
          display: inline-block;
          width: 6px;
          height: 6px;
          border-radius: 50%;
          margin-right: 6px;
        }
      }

      .el-icon {
        margin-left: 6px;
        border-radius: 50%;
        width: 14px;
        height: 14px;

        &:hover {
          background: rgba(31, 95, 191, 0.1);
        }
      }
    }
  }
}
</style>
