<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>用户管理</span>
          <el-button type="primary">新增用户</el-button>
        </div>
      </template>

      <!-- 搜索 -->
      <el-form :model="searchForm" inline>
        <el-form-item label="用户名">
          <el-input v-model="searchForm.username" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="searchForm.role" placeholder="请选择角色" clearable>
            <el-option label="管理员" value="admin" />
            <el-option label="教师" value="teacher" />
            <el-option label="学生" value="student" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
            <el-option label="启用" value="1" />
            <el-option label="禁用" value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary">查询</el-button>
          <el-button>重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="userList" stripe>
        <el-table-column type="index" width="50" />
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column prop="role" label="角色">
          <template #default="{ row }">
            <el-tag
              :type="row.role === 'admin' ? 'danger' : row.role === 'teacher' ? 'success' : ''"
            >
              {{ row.roleText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="dept" label="部门/学院" />
        <el-table-column prop="phone" label="电话" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.status" :active-value="1" :inactive-value="0" />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default>
            <el-button link type="primary">编辑</el-button>
            <el-button link type="danger">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        class="pagination"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'

const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(100)

const searchForm = reactive({
  username: '',
  role: '',
  status: ''
})

const userList = ref([
  {
    id: 1,
    username: 'admin',
    nickname: '管理员',
    role: 'admin',
    roleText: '管理员',
    dept: '信息中心',
    phone: '13800138000',
    status: 1,
    createTime: '2025-01-01 00:00:00'
  },
  {
    id: 2,
    username: 'zhangsan',
    nickname: '张三',
    role: 'teacher',
    roleText: '教师',
    dept: '计算机学院',
    phone: '13800138001',
    status: 1,
    createTime: '2025-01-02 00:00:00'
  },
  {
    id: 3,
    username: '2025010001',
    nickname: '李四',
    role: 'student',
    roleText: '学生',
    dept: '数学学院',
    phone: '13800138002',
    status: 1,
    createTime: '2025-09-01 00:00:00'
  }
])
</script>

<style scoped lang="scss">
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
</style>
