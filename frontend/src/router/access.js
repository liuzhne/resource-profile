// 与后端 Roles.STAFF_VIEW 对齐；接口仍独立执行授权，前端仅控制入口。
export const STAFF_VIEW_ROLES = [
  'admin',
  'teacher',
  'counselor',
  'academic_advisor',
  'psychologist'
]
export const MENTAL_WRITE_ROLES = ['admin', 'psychologist']

export const getUserRoles = (userInfo) =>
  Array.isArray(userInfo?.roles) ? userInfo.roles.filter((role) => typeof role === 'string') : []

export const getHomePath = (roles, permissions) => {
  if (roles.some((role) => STAFF_VIEW_ROLES.includes(role)))
    return permitsPath('/dashboard', roles, permissions) ? '/dashboard' : '/profile'
  if (roles.includes('student')) return '/student-mental/list'
  return '/profile'
}

export const matchesRoles = (meta, roles) =>
  !meta?.roles?.length || meta.roles.some((role) => roles.includes(role))

const permitsPath = (path, roles, permissions) => {
  if (roles.includes('admin') || !Array.isArray(permissions)) return true
  // Student self-service uses ownership authorization, independent of staff menu permissions.
  if (path?.startsWith('/student-mental')) return true
  const domain = path === '/dashboard' ? 'data' : path?.split('/')[1]
  return (
    !['data', 'teacher', 'student', 'mental', 'agent'].includes(domain) ||
    permissions.includes(`${domain}:read`)
  )
}

export const canAccessRoute = (route, roles, permissions) => {
  const records = route.matched?.length ? route.matched : [route]
  return records.every(
    (record) => matchesRoles(record.meta, roles) && permitsPath(record.path, roles, permissions)
  )
}

// 递归过滤，避免父菜单可见时泄漏权限更严格的子页面入口。
export const filterMenuRoutes = (routes, roles, permissions) =>
  routes
    .filter(
      (route) =>
        !route.meta?.hidden &&
        matchesRoles(route.meta, roles) &&
        permitsPath(route.path, roles, permissions)
    )
    .map((route) => ({
      ...route,
      ...(route.children ? { children: filterMenuRoutes(route.children, roles, permissions) } : {})
    }))

export const createAuthGuard = (getUserStore) => async (to) => {
  const userStore = getUserStore()
  if (userStore.token && userStore.ensureSession) {
    try {
      await userStore.ensureSession()
    } catch {
      return '/login'
    }
  }
  // 用户信息加载期间可能发生另一次登录；只清理/采用对应会话的结果。
  while (userStore.token && !userStore.userInfo) {
    const sessionToken = userStore.token
    try {
      await userStore.getUserInfoAction()
    } catch {
      if (userStore.token === sessionToken) {
        userStore.clearSession({ redirect: false })
        return '/login'
      }
    }
    if (userStore.token === sessionToken && !userStore.userInfo) {
      userStore.clearSession({ redirect: false })
      return '/login'
    }
  }
  if (!userStore.token) return to.meta.public ? true : '/login'

  const roles = getUserRoles(userStore.userInfo)
  const home = getHomePath(roles, userStore.userInfo?.permissions)
  if (to.path === '/login' || to.path === '/') return home
  return canAccessRoute(to, roles, userStore.userInfo?.permissions) ? true : home
}
