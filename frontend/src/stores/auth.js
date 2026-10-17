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
    async login(username, password) {
      const data = await authApi.login({ username, password })
      this.token = data.token
      this.user = data.user
      localStorage.setItem(TOKEN_KEY, this.token)
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
      return data.user
    },
    async logout() {
      try {
        await authApi.logout() // 登出=签出，后端记录登出时间
      } catch {
        // 后端不可达也清本地登录态，避免卡死
      }
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    },
  },
})
