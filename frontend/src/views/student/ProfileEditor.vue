<template>
  <el-dialog
    v-model="visible"
    :title="recordId ? '编辑学生' : '新增学生'"
    width="min(700px, 94vw)"
    destroy-on-close
  >
    <el-form label-position="top" class="portrait-form" @submit.prevent="save">
      <el-form-item
        v-for="f in fields"
        :key="f.key"
        :label="f.label"
        :required="required.includes(f.key)"
      >
        <el-select
          v-if="f.type === 'account'"
          v-model="form[f.key]"
          filterable
          :loading="accountsLoading"
          placeholder="选择关联账号"
        >
          <el-option
            v-for="account in accounts"
            :key="account.id"
            :label="`${account.username}（${account.id}）`"
            :value="account.id"
          />
        </el-select>
        <el-select
          v-else-if="f.type === 'gender'"
          v-model="form[f.key]"
          clearable
          placeholder="未记录"
        >
          <el-option label="女" :value="0" /><el-option label="男" :value="1" />
        </el-select>
        <el-select v-else-if="f.type === 'status'" v-model="form[f.key]">
          <el-option label="在读" :value="1" /><el-option label="退学" :value="0" /><el-option
            label="毕业"
            :value="2"
          />
        </el-select>
        <el-date-picker
          v-else-if="f.type === 'date'"
          v-model="form[f.key]"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择日期"
        />
        <el-input-number
          v-else-if="f.type === 'number'"
          v-model="form[f.key]"
          :min="0"
          :max="f.key === 'gpa' ? 4 : undefined"
          :precision="f.key === 'gpa' ? 2 : 0"
          controls-position="right"
        />
        <el-input
          v-else
          v-model="form[f.key]"
          :maxlength="f.key === 'researchArea' ? 500 : f.key === 'name' ? 50 : 100"
        />
      </el-form-item>
    </el-form>
    <template #footer
      ><el-button :disabled="saving" @click="visible = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="save">保存</el-button></template
    >
  </el-dialog>
</template>
<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { createStudent, updateStudent, getStudentAccounts } from '@/api/student'
const emit = defineEmits(['saved'])
const user = useUserStore()
const visible = ref(false),
  saving = ref(false),
  accountsLoading = ref(false),
  recordId = ref(null),
  accounts = ref([])
const form = reactive({})
const specification = [
  { key: 'userId', label: '关联账号', type: 'account' },
  { key: 'studentId', label: '学号', type: 'text' },
  { key: 'name', label: '姓名', type: 'text' },
  { key: 'gender', label: '性别', type: 'gender' },
  { key: 'deptName', label: '学院', type: 'text' },
  { key: 'majorName', label: '专业', type: 'text' },
  { key: 'grade', label: '年级', type: 'text' },
  { key: 'className', label: '班级', type: 'text' },
  { key: 'enrollmentDate', label: '入学日期', type: 'date' },
  { key: 'expectedGraduation', label: '预计毕业', type: 'date' },
  { key: 'gpa', label: 'GPA（0–4）', type: 'number' },
  { key: 'credits', label: '已修学分', type: 'number' },
  { key: 'birthDate', label: '出生日期', type: 'date' },
  { key: 'status', label: '状态', type: 'status' }
]
const fields = computed(() =>
  specification.filter((f) => f.key !== 'birthDate' || user.userRoles.includes('admin'))
)
const required = ['userId', 'studentId', 'name']
const open = async (record = null) => {
  recordId.value = record?.id ?? null
  for (const field of specification)
    form[field.key] =
      record?.[field.key] ?? (field.key === 'status' ? 1 : field.type === 'text' ? '' : null)
  visible.value = true
  accountsLoading.value = true
  try {
    accounts.value = (await getStudentAccounts()).data || []
  } catch {
    accounts.value = []
  } finally {
    accountsLoading.value = false
  }
}
const save = async () => {
  if (required.some((k) => form[k] == null || String(form[k]).trim() === ''))
    return ElMessage.warning('请填写关联账号、编号和姓名')
  saving.value = true
  const data = Object.fromEntries(
    fields.value.map((f) => [f.key, form[f.key] === '' ? null : form[f.key]])
  )
  try {
    if (recordId.value) await updateStudent(recordId.value, data)
    else await createStudent(data)
    visible.value = false
    ElMessage.success('档案已保存')
    emit('saved')
  } catch {
    /* API interceptor displays the error; dialog stays open. */
  } finally {
    saving.value = false
  }
}
defineExpose({ open })
</script>
<style scoped>
.portrait-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 20px;
}
.portrait-form :deep(.el-select),
.portrait-form :deep(.el-input-number),
.portrait-form :deep(.el-date-editor) {
  width: 100%;
}
@media (max-width: 600px) {
  .portrait-form {
    grid-template-columns: 1fr;
  }
}
</style>
