<template>
  <div class="records-head">
    <span class="section-title">{{ title }}</span
    ><el-button v-if="canWrite" type="primary" size="small" @click="open()">{{
      evaluation ? '录入评价' : '新增记录'
    }}</el-button>
  </div>
  <el-empty v-if="!records.length" :description="evaluation ? '暂无教学评价' : '暂无记录'" />
  <el-table v-else :data="records" stripe>
    <el-table-column prop="title" label="名称" min-width="170" />
    <el-table-column prop="eventDate" label="日期" width="115" />
    <el-table-column v-if="evaluation" prop="score" label="评分（100分）" width="125" />
    <el-table-column v-if="evaluation" label="满意" width="90"
      ><template #default="{ row }">{{
        row.satisfied === true ? '满意' : row.satisfied === false ? '不满意' : '未回答'
      }}</template></el-table-column
    >
    <el-table-column
      :prop="evaluation ? 'comment' : 'description'"
      label="说明"
      min-width="170"
      show-overflow-tooltip
    />
    <el-table-column v-if="canWrite" label="操作" width="110"
      ><template #default="{ row }"
        ><el-button link @click="open(row)">编辑</el-button
        ><el-button link type="danger" @click="remove(row)">删除</el-button></template
      ></el-table-column
    >
  </el-table>
  <el-dialog
    v-model="visible"
    :title="recordId ? '编辑记录' : evaluation ? '录入教学评价' : '新增记录'"
    width="min(540px,94vw)"
    destroy-on-close
  >
    <el-form label-position="top">
      <el-form-item label="名称" required
        ><el-input v-model="form.title" maxlength="200"
      /></el-form-item>
      <el-form-item label="日期" required
        ><el-date-picker v-model="form.eventDate" type="date" value-format="YYYY-MM-DD"
      /></el-form-item>
      <el-form-item v-if="evaluation" label="评价分数（0–100）" required
        ><el-input-number v-model="form.score" :min="0" :max="100" :precision="2"
      /></el-form-item>
      <el-form-item v-if="evaluation" label="是否满意"
        ><el-select v-model="form.satisfied" clearable placeholder="未回答"
          ><el-option label="满意" :value="true" /><el-option
            label="不满意"
            :value="false" /></el-select
      ></el-form-item>
      <el-form-item label="说明"
        ><el-input v-model="form.text" type="textarea" :rows="4" maxlength="2000"
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
import {
  saveTeacherRecord,
  deleteTeacherRecord,
  saveTeacherEvaluation,
  deleteTeacherEvaluation
} from '@/api/teacher'
const props = defineProps({
  teacherId: { type: [Number, String], required: true },
  kind: { type: String, default: 'TEACHING' },
  title: { type: String, required: true },
  records: { type: Array, default: () => [] },
  canWrite: Boolean,
  evaluation: Boolean
})
const emit = defineEmits(['changed'])
const visible = ref(false),
  saving = ref(false),
  recordId = ref(null),
  form = reactive({ title: '', eventDate: null, score: null, satisfied: null, text: '' })
const open = (record = null) => {
  recordId.value = record?.id ?? null
  Object.assign(form, {
    title: record?.title || '',
    eventDate: record?.eventDate || null,
    score: record?.score ?? null,
    satisfied: record?.satisfied ?? null,
    text: record?.[props.evaluation ? 'comment' : 'description'] || ''
  })
  visible.value = true
}
const save = async () => {
  if (!form.title.trim() || !form.eventDate || (props.evaluation && form.score == null))
    return ElMessage.warning('请填写名称、日期和所需分数')
  saving.value = true
  try {
    const data = { title: form.title.trim(), eventDate: form.eventDate }
    if (props.evaluation)
      await saveTeacherEvaluation(props.teacherId, recordId.value, {
        ...data,
        score: form.score,
        satisfied: form.satisfied === '' ? null : form.satisfied,
        comment: form.text || null
      })
    else
      await saveTeacherRecord(props.teacherId, recordId.value, {
        ...data,
        kind: props.kind,
        description: form.text || null
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
    if (props.evaluation) await deleteTeacherEvaluation(props.teacherId, row.id)
    else await deleteTeacherRecord(props.teacherId, row.id)
    ElMessage.success('记录已删除')
    emit('changed')
  } catch {
    /* Cancellation and API errors do not change the displayed data. */
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
