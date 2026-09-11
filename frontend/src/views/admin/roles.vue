<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>角色权限管理</span>
          <el-button type="primary">新增角色</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="roleList" stripe>
        <el-table-column type="index" width="50" />
        <el-table-column prop="name" label="角色名称" />
        <el-table-column prop="code" label="角色编码" />
        <el-table-column prop="description" label="描述" min-width="200" />
        <el-table-column prop="userCount" label="用户数量" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handlePermission(row)">权限设置</el-button>
            <el-button link type="primary">编辑</el-button>
            <el-button link type="danger">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 权限设置对话框 -->
    <el-dialog v-model="permissionDialogVisible" title="权限设置" width="600px">
      <el-tree
        ref="permissionTree"
        :data="permissionData"
        show-checkbox
        default-expand-all
        node-key="id"
        :props="{ label: 'name', children: 'children' }"
      />
      <template #footer>
        <el-button @click="permissionDialogVisible = false">取消</el-button>
        <el-button type="primary">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const loading = ref(false)
const permissionDialogVisible = ref(false)

const roleList = ref([
  {
    id: 1,
    name: '系统管理员',
    code: 'admin',
    description: '拥有系统所有权限',
    userCount: 2,
    createTime: '2025-01-01 00:00:00'
  },
  {
    id: 2,
    name: '教师',
    code: 'teacher',
    description: '可以查看教师画像和学生信息',
    userCount: 128,
    createTime: '2025-01-01 00:00:00'
  },
  {
    id: 3,
    name: '学生',
    code: 'student',
    description: '可以查看自己的画像信息',
    userCount: 2456,
    createTime: '2025-01-01 00:00:00'
  },
  {
    id: 4,
    name: '心理健康教师',
    code: 'mental_teacher',
    description: '可以管理心理健康相关功能',
    userCount: 5,
    createTime: '2025-01-01 00:00:00'
  }
])

const permissionData = ref([
  {
    id: 1,
    name: '系统管理',
    children: [
      { id: 11, name: '用户管理' },
      { id: 12, name: '角色管理' },
      { id: 13, name: '系统配置' }
    ]
  },
  {
    id: 2,
    name: '教师画像',
    children: [
      { id: 21, name: '教师列表' },
      { id: 22, name: '教师详情' }
    ]
  },
  {
    id: 3,
    name: '学生画像',
    children: [
      { id: 31, name: '学生列表' },
      { id: 32, name: '学生详情' }
    ]
  },
  {
    id: 4,
    name: '心理健康',
    children: [
      { id: 41, name: '心理概览' },
      { id: 42, name: '问卷管理' },
      { id: 43, name: '分析报告' }
    ]
  }
])

const handlePermission = (_row: any) => {
  permissionDialogVisible.value = true
}
</script>

<style scoped lang="scss">
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
