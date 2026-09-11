import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login, getUserInfo } from '@/api/auth'
import router from '@/router'

const USER_INFO_KEY = 'userInfo'

const readCachedUserInfo = () => {
  const raw = localStorage.getItem(USER_INFO_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    localStorage.removeItem(USER_INFO_KEY)
    return null
  }
}

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(readCachedUserInfo())

  const isLoggedIn = computed(() => !!token.value)
  const userRoles = computed(() => userInfo.value?.roles || [])

  const setToken = (newToken) => {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  const setUserInfo = (info) => {
    userInfo.value = info
    if (info) {
      localStorage.setItem(USER_INFO_KEY, JSON.stringify(info))
    } else {
      localStorage.removeItem(USER_INFO_KEY)
    }
  }

  const loginAction = async (form) => {
    const res = await login(form)
    setToken(res.data.token)
    await getUserInfoAction()
    router.push('/')
  }

  const getUserInfoAction = async () => {
    const res = await getUserInfo()
    setUserInfo(res.data)
    return res.data
  }

  const logout = () => {
    token.value = ''
    setUserInfo(null)
    localStorage.removeItem('token')
    router.push('/login')
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    userRoles,
    loginAction,
    getUserInfoAction,
    logout
  }
})
