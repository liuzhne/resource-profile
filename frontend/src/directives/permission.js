/**
 * v-permission 指令 + usePermission 组合式 API
 *
 * <p><b>语义（G-2.2 后）</b>：这是一个 UX 隐藏指令，<b>不是</b>安全防线。
 * 实际的字段级权限由后端 `FieldPermissionAdvice`（common 模块）在响应序列化前
 * 把无权限字段置 null。本指令的作用仅限于：
 *   - 不渲染"明显属于其他角色"的 UI 区块（按钮/卡片），避免出现 null/空值占位
 *   - 让无权限用户看不到"按钮但点不动"等令人困惑的状态
 *
 * <p>用法：
 *   <span v-permission="['psychologist','admin']">原始得分: {{ row.score }}</span>
 *   <el-table-column v-if="hasRole(['psychologist','admin'])" prop="score" label="得分" />
 *
 * <p>命中策略：
 *   - 取 useUserStore().userRoles（由后端 /auth/user-info 返回的 roles），与 binding 数组取交集
 *   - 没命中则 detach DOM（而非 display:none），让 devtools 也看不到模板
 *   - binding 为空 / 非数组：警告并默认拒绝（保守降级，与后端 advice 行为一致）
 *
 * <p>验收：见 docs/educare/FIELD_PERMISSION_VERIFY.md 的 5 角色矩阵测试。
 */
import { useUserStore } from '@/store/modules/user'

const PRIVILEGED_FALLBACK = ['admin']

const normalizeRoles = (binding) => {
  const v = binding.value
  if (Array.isArray(v) && v.length > 0) return v
  if (typeof v === 'string' && v.trim()) return [v.trim()]
  console.warn('[v-permission] 缺少角色数组，默认仅 admin 可见：', binding)
  return PRIVILEGED_FALLBACK
}

const detach = (el) => {
  if (el && el.parentNode) {
    // 用注释占位便于后续 update 复位（Vue 3 nextTick 时插入节点更稳定）
    const placeholder = document.createComment('v-permission')
    el.parentNode.replaceChild(placeholder, el)
    el._permissionPlaceholder = placeholder
  }
}

const reattach = (el) => {
  const ph = el._permissionPlaceholder
  if (ph && ph.parentNode) {
    ph.parentNode.replaceChild(el, ph)
    el._permissionPlaceholder = null
  }
}

const hasAnyRole = (allowed) => {
  const store = useUserStore()
  const roles = store.userRoles || []
  return allowed.some((r) => roles.includes(r))
}

export const permission = {
  mounted(el, binding) {
    const allowed = normalizeRoles(binding)
    if (!hasAnyRole(allowed)) detach(el)
  },
  updated(el, binding) {
    const allowed = normalizeRoles(binding)
    const ok = hasAnyRole(allowed)
    if (ok) reattach(el)
    else detach(el)
  },
  unmounted(el) {
    el._permissionPlaceholder = null
  }
}

/** 在 setup 中用 v-if / 计算属性的场景。 */
export const usePermission = () => {
  const hasRole = (allowed) => {
    const list = Array.isArray(allowed) ? allowed : allowed ? [allowed] : PRIVILEGED_FALLBACK
    return hasAnyRole(list)
  }
  return { hasRole }
}

export default {
  install(app) {
    app.directive('permission', permission)
  }
}
