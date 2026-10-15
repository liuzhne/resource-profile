import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login, getUserInfo, refreshToken, logout as revokeSession } from '@/api/auth'
import router from '@/router'
import { getHomePath, getUserRoles } from '@/router/access'

const USER_INFO_KEY = 'userInfo'
const temporaryStorage = () => globalThis.sessionStorage || localStorage
const activeStorage = () =>
  temporaryStorage().getItem('token') ? temporaryStorage() : localStorage

const readCachedUserInfo = () => {
  const raw = activeStorage().getItem(USER_INFO_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    activeStorage().removeItem(USER_INFO_KEY)
    return null
  }
}

export const useUserStore = defineStore('user', () => {
  const token = ref(temporaryStorage().getItem('token') || localStorage.getItem('token') || '')
  const userInfo = ref(readCachedUserInfo())

  const isLoggedIn = computed(() => !!token.value)
  const userRoles = computed(() => getUserRoles(userInfo.value))
  const logoutRequests = new Map()
  const userInfoRequests = new Map()
  let refreshRequest = null
  let remembered = localStorage.getItem('rememberSession') === 'true'

  const setToken = (newToken) => {
    token.value = newToken
    localStorage.removeItem('token')
    temporaryStorage().removeItem('token')
    ;(remembered ? localStorage : temporaryStorage()).setItem('token', newToken)
  }

  const setUserInfo = (info) => {
    userInfo.value = info
    if (info) {
      activeStorage().setItem(USER_INFO_KEY, JSON.stringify(info))
    } else {
      localStorage.removeItem(USER_INFO_KEY)
      temporaryStorage().removeItem(USER_INFO_KEY)
    }
  }

  const loginAction = async (form) => {
    const res = await login(form)
    remembered = form.remember === true
    localStorage.setItem('rememberSession', String(remembered))
    const sessionToken = res.data.token
    setToken(sessionToken)
    setUserInfo(null)
    try {
      await getUserInfoAction()
    } catch (error) {
      if (!token.value || token.value === sessionToken) throw error
      return
    }
    if (token.value === sessionToken)
      await router.replace(getHomePath(userRoles.value, userInfo.value?.permissions))
  }

  const ensureSession = () => {
    if (!remembered || !token.value) return Promise.resolve()
    let expiry
    try {
      expiry = JSON.parse(atob(token.value.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))).exp
    } catch {
      return Promise.resolve()
    }
    if (!expiry || expiry * 1000 > Date.now() + 30000) return Promise.resolve()
    if (refreshRequest) return refreshRequest
    const previous = token.value
    refreshRequest = refreshToken()
      .then((res) => {
        if (token.value === previous) setToken(res.data.token)
      })
      .catch((error) => {
        if (token.value === previous && (error.code === 401 || error.response?.status === 401))
          clearSession()
        throw error
      })
      .finally(() => {
        refreshRequest = null
      })
    return refreshRequest
  }

  const getUserInfoAction = () => {
    const sessionToken = token.value
    if (userInfoRequests.has(sessionToken)) return userInfoRequests.get(sessionToken)
    const userInfoRequest = getUserInfo(sessionToken)
      .then((res) => {
        // 登录身份可能已经切换；旧身份结果不得覆写新身份的角色。
        if (token.value !== sessionToken) return null
        setUserInfo(res.data)
        return res.data
      })
      .finally(() => {
        userInfoRequests.delete(sessionToken)
      })
    userInfoRequests.set(sessionToken, userInfoRequest)
    return userInfoRequest
  }

  // 401 / 身份加载失败只能清本地状态，不能再次请求 logout 造成递归。
  const clearSession = ({ redirect = true } = {}) => {
    token.value = ''
    setUserInfo(null)
    localStorage.removeItem('token')
    temporaryStorage().removeItem('token')
    localStorage.removeItem('rememberSession')
    remembered = false
    if (redirect) return router.replace('/login')
  }

  // 主动退出先撤销 Redis 会话；请求失败时保留状态，允许用户重试。
  const logoutAction = () => {
    const sessionToken = token.value
    if (!sessionToken) return Promise.resolve(clearSession())
    if (logoutRequests.has(sessionToken)) return logoutRequests.get(sessionToken)

    const logoutRequest = revokeSession(sessionToken)
      .then(() => {
        if (token.value === sessionToken) return clearSession()
      })
      .catch((error) => {
        if (error.response?.status === 401 || error.code === 401) {
          if (token.value === sessionToken) return clearSession()
          return
        }
        throw error
      })
      .finally(() => {
        logoutRequests.delete(sessionToken)
      })
    logoutRequests.set(sessionToken, logoutRequest)
    return logoutRequest
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    userRoles,
    loginAction,
    getUserInfoAction,
    ensureSession,
    clearSession,
    logoutAction
  }
})
