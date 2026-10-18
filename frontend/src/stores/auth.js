// 登录态（token + 当前用户）。登录即签到/登出即签出由后端记录（A-03），前端只管状态。
import { defineStore } from 'pinia'
import * as authApi from '@/api/auth'
import { TOKEN_KEY, USER_KEY } from '@/api/http'

function readUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: readUser(),
  }),
  getters: {
    isLoggedIn: (s) => Boolean(s.token),
    isAdmin: (s) => s.user?.role === 'admin',
    isDirector: (s) => Boolean(s.user?.isDirector),
    isLibrarian: (s) => Boolean(s.user?.isLibrarian),
  },
  actions: {
    setUser(user) {
      this.user = user
      // 电话、生日等完整档案仅在内存中使用，不落地浏览器缓存。
      const { id, username, name, role, identityType, teacherType, isDirector, isLibrarian } = user
      localStorage.setItem(USER_KEY, JSON.stringify({ id, username, name, role, identityType, teacherType, isDirector, isLibrarian }))
    },
    clearSession() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    },
    async refresh() {
      const user = await authApi.getMe()
      this.setUser(user)
      return user
    },
    async login(username, password) {
      const data = await authApi.login({ username, password })
      this.token = data.token
      localStorage.setItem(TOKEN_KEY, this.token)
      this.setUser(data.user)
      return data.user
    },
    async logout() {
      try {
        await authApi.logout() // 登出=签出，后端记录登出时间
      } catch (e) {
        // 仅已失效令牌可直接清理；网络失败时保留会话，允许用户重试签出。
        if (e.code !== 401) throw e
      }
      this.clearSession()
    },
  },
})
