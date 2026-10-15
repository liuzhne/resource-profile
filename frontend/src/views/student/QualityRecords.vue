<template>
  <div class="records-head">
    <span class="section-title">综合素质记录</span
    ><el-button v-if="canWrite" type="primary" size="small" @click="open()">新增记录</el-button>
  </div>
  <el-empty v-if="!records.length" description="暂无综合素质记录" />
  <el-table v-else :data="records" stripe>
    <el-table-column label="类型" width="110"
      ><template #default="{ row }">{{ labels[row.kind] || row.kind }}</template></el-table-column
    >
    <el-table-column prop="title" label="名称" min-width="160" />
    <el-table-column prop="eventDate" label="日期" width="115" />
    <el-table-column prop="description" label="说明" min-width="170" show-overflow-tooltip />
    <el-table-column v-if="canWrite" label="操作" width="110"
      ><template #default="{ row }"
        ><el-button link @click="open(row)">编辑</el-button
        ><el-button link type="danger" @click="remove(row)">删除</el-button></template
      ></el-table-column
    >
  </el-table>
  <el-dialog
    v-model="visible"
    :title="recordId ? '编辑素质记录' : '新增素质记录'"
    width="min(540px,94vw)"
    destroy-on-close
  >
    <el-form label-position="top">
      <el-form-item label="类型" required
        ><el-select v-model="form.kind"
          ><el-option
            v-for="(label, key) in labels"
            :key="key"
            :label="label"
            :value="key" /></el-select
      ></el-form-item>
      <el-form-item label="名称" required
        ><el-input v-model="form.title" maxlength="200"
      /></el-form-item>
      <el-form-item label="日期" required
        ><el-date-picker v-model="form.eventDate" type="date" value-format="YYYY-MM-DD"
      /></el-form-item>
      <el-form-item label="说明"
        ><el-input v-model="form.description" type="textarea" :rows="4" maxlength="2000"
      /></el-form-item>
    </el-form>
    <template #footer
      ><el-button :disabled="saving" @click="visible = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="save">保存</el-button></template
    >
  </el-dialog>
</template>
<script setup>
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { saveStudentQuality, deleteStudentQuality } from '@/api/student'
const props = defineProps({
  studentId: { type: [Number, String], required: true },
  records: { type: Array, default: () => [] },
  canWrite: Boolean
})
const emit = defineEmits(['changed'])
const labels = { COMPETITION: '竞赛', HONOR: '荣誉', VOLUNTEER: '志愿服务' }
const visible = ref(false),
  saving = ref(false),
  recordId = ref(null),
  form = reactive({ kind: 'COMPETITION', title: '', eventDate: null, description: '' })
const open = (record = null) => {
  recordId.value = record?.id ?? null
  Object.assign(form, {
    kind: record?.kind || 'COMPETITION',
    title: record?.title || '',
    eventDate: record?.eventDate || null,
    description: record?.description || ''
  })
  visible.value = true
}
const save = async () => {
  if (!form.title.trim() || !form.eventDate) return ElMessage.warning('请填写名称和日期')
  saving.value = true
  try {
    await saveStudentQuality(props.studentId, recordId.value, {
      ...form,
      title: form.title.trim(),
      description: form.description || null
    })
    visible.value = false
    ElMessage.success('记录已保存')
    emit('changed')
  } catch {
    /* Request error is shown by the interceptor. */
  } finally {
    saving.value = false
  }
}
const remove = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除这条记录吗？', '删除记录', { type: 'warning' })
    await deleteStudentQuality(props.studentId, row.id)
    ElMessage.success('记录已删除')
    emit('changed')
  } catch {
    /* Keep data on cancellation or failed request. */
  }
}
</script>
<style scoped>
.records-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
</style>
