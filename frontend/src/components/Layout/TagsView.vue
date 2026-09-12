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
        <el-icon v-if="!isAffix(tag)" :size="11" @click.prevent.stop="closeSelectedTag(tag)">
          <Close />
        </el-icon>
      </router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { RouteLocationNormalized } from 'vue-router'

interface TagView {
  name: string
  path: string
  title: string
  affix?: boolean
}

const route = useRoute()
const router = useRouter()
const visitedViews = ref<TagView[]>([
  { name: 'Dashboard', path: '/dashboard', title: '数据面板', affix: true }
])

const isActive = (tag: TagView) => {
  return tag.path === route.path
}

const isAffix = (tag: TagView) => {
  return tag.affix
}

const addView = (view: RouteLocationNormalized) => {
  if (visitedViews.value.some((v) => v.path === view.path)) return

  visitedViews.value.push({
    name: view.name as string,
    path: view.path,
    title: (view.meta.title as string) || 'no-name'
  })
}

const closeSelectedTag = (view: TagView) => {
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
// 对齐原型：药丸标签条，无边框无分隔线，直接浮在内容区之上
.tags-view-container {
  flex-shrink: 0;
  padding: 10px 20px 0;
  position: relative;
  z-index: 9;

  .tags-view-wrapper {
    display: flex;
    gap: 6px;
    overflow-x: auto;
    scrollbar-width: none;

    &::-webkit-scrollbar {
      display: none;
    }

    .tags-view-item {
      display: inline-flex;
      align-items: center;
      gap: 7px;
      height: 28px;
      padding: 0 12px;
      border-radius: var(--radius-sm);
      background: rgba(255, 255, 255, 0.42);
      color: var(--text-color-secondary);
      font-size: 12px;
      font-weight: 500;
      white-space: nowrap;
      flex-shrink: 0;
      cursor: pointer;
      text-decoration: none;
      transition: all 0.16s ease;

      &:hover {
        background: rgba(255, 255, 255, 0.7);
        color: var(--text-color);
      }

      // 当前页：白底 + 轻微投影抬起（原型 THUMB）
      &.active {
        background: #fff;
        color: var(--text-color);
        box-shadow: var(--shadow-thumb);
      }

      .el-icon {
        opacity: 0.55;
        border-radius: 50%;
        transition: opacity 0.16s;

        &:hover {
          opacity: 1;
          background: var(--fill-hover);
        }
      }
    }
  }
}
</style>
