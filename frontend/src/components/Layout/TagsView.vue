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
.tags-view-container {
  height: 40px;
  background: #fff;
  border-bottom: 1px solid #d8dce5;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.12);
  padding: 0 16px;

  .tags-view-wrapper {
    display: flex;
    height: 100%;

    .tags-view-item {
      display: inline-flex;
      align-items: center;
      height: 28px;
      line-height: 28px;
      margin-top: 6px;
      margin-right: 8px;
      padding: 0 12px;
      background: #fff;
      border: 1px solid #d8dce5;
      color: #495060;
      font-size: 12px;
      cursor: pointer;
      transition: all 0.3s;
      text-decoration: none;

      &:hover {
        background: #f0f0f0;
      }

      &.active {
        background-color: var(--primary-color);
        color: #fff;
        border-color: var(--primary-color);

        &::before {
          content: '';
          background: #fff;
          display: inline-block;
          width: 8px;
          height: 8px;
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
          background: rgba(0, 0, 0, 0.1);
        }
      }
    }
  }
}
</style>
