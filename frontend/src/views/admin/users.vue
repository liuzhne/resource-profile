<template>
  <div class="glass-panel list-panel">
    <div class="page-head">
      <div>
        <div class="head-title">用户管理</div>
        <div class="head-sub">
          共 <b>{{ userList.length }}</b> 个账号
        </div>
      </div>
      <el-button type="primary" class="head-action" :icon="Plus" @click="notReady">
        新增用户
      </el-button>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="searchForm.username"
        placeholder="请输入用户名"
        clearable
        :prefix-icon="Search"
      />
      <el-select v-model="searchForm.role" placeholder="请选择角色" clearable>
        <el-option label="管理员" value="admin" />
        <el-option label="教师" value="teacher" />
        <el-option label="学生" value="student" />
      </el-select>
      <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
        <el-option label="启用" value="1" />
        <el-option label="禁用" value="0" />
      </el-select>
      <el-button class="btn-query" @click="notReady">查询</el-button>
      <el-button class="btn-reset" @click="handleReset">重置</el-button>
    </div>

    <p class="no-source mock-note">
      <el-icon :size="14"><WarningFilled /></el-icon>
      <span>
        本页为静态演示数据：`src/api/` 下没有用户管理模块，后端也未提供 `/user` 的增删改查接口。
        查询、新增、编辑、删除、启停均未接线，接口就绪后再接入。
      </span>
    </p>

    <div class="table-scroll">
      <table class="plain-table users-table">
        <thead>
          <tr>
            <th>用户名</th>
            <th>昵称</th>
            <th>角色</th>
            <th>部门/学院</th>
            <th>电话</th>
            <th>状态</th>
            <th>创建时间</th>
            <th class="col-right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, i) in userList" :key="row.id">
            <td class="strong">{{ row.username }}</td>
            <td>
              <span class="name-cell">
                <span class="avatar-sm" :style="{ background: avatarBg(i) }">
                  {{ initialOf(row.nickname) }}
                </span>
                <span>{{ row.nickname }}</span>
              </span>
            </td>
            <td>
              <span class="badge" :style="roleStyle(row.role)">{{ row.roleText }}</span>
            </td>
            <td>{{ row.dept }}</td>
            <td class="tnum">{{ row.phone }}</td>
            <td>
              <el-switch v-model="row.status" :active-value="1" :inactive-value="0" disabled />
            </td>
            <td class="tnum">{{ row.createTime }}</td>
            <td class="col-right">
              <span class="row-action" @click="notReady">编辑</span>
              <span class="row-action danger" @click="notReady">删除</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="table-foot">
      <span class="foot-count">
        共 <b>{{ userList.length }}</b> 条（演示数据）
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search, WarningFilled } from '@element-plus/icons-vue'
import { avatarBg, initialOf } from '@/utils/avatar'

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

const roleStyle = (role) =>
  ({
    admin: { background: 'var(--error-tint)', color: 'var(--error-deep)' },
    teacher: { background: 'var(--success-tint)', color: 'var(--success-deep)' },
    student: { background: 'var(--primary-tint)', color: 'var(--primary-color)' }
  })[role] || { background: 'var(--fill-grey)', color: 'var(--fill-grey-fg)' }

const handleReset = () => {
  searchForm.username = ''
  searchForm.role = ''
  searchForm.status = ''
}

// 用户管理接口尚未提供，所有操作先占位
const notReady = () => {
  ElMessage.info('用户管理接口尚未提供，该操作暂未开放')
}
</script>

<style scoped lang="scss">
.list-panel {
  display: flex;
  flex-direction: column;
}

.head-action {
  height: 34px;
  padding: 0 15px;
  font-size: 13px;
  font-weight: 600;
  border-radius: var(--radius-md);
  border: none;
  box-shadow: 0 5px 14px -8px rgba(0, 122, 255, 0.7);
}

.filter-bar {
  :deep(.el-input) {
    width: 190px;
  }

  :deep(.el-select) {
    width: 150px;
  }
}

.mock-note {
  margin: 0 20px 14px;
}

.users-table {
  min-width: 900px;
}

.row-action.danger {
  color: var(--error-deep);
}
</style>
