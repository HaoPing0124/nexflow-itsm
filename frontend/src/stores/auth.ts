import { defineStore } from 'pinia'
import { authApi } from '@/api/modules'
import { TOKEN_KEY } from '@/api/http'
import { demoUsers } from '@/mock/demoData'
import type { UserDto } from '@/types'

interface AuthState {
  token: string | null
  user: UserDto | null
  demo: boolean
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: localStorage.getItem(TOKEN_KEY),
    user: null,
    demo: false,
  }),
  getters: {
    isLoggedIn: (s) => !!s.token,
    displayName: (s) => s.user?.displayName ?? s.user?.username ?? '',
    permissions: (s) => s.user?.permissions ?? [],
    roles: (s) => s.user?.roles ?? [],
  },
  actions: {
    /**
     * 真实后端优先：后端在跑时走 JWT 登录（前后端联调）。
     * 仅当后端不可达时，才回退到本地演示账号，保证离线也能完整演示。
     */
    async login(username: string, password: string) {
      try {
        const res = await authApi.login(username, password)
        this.token = res.token
        this.user = res.user
        this.demo = false
        localStorage.setItem(TOKEN_KEY, res.token)
        return
      } catch (e) {
        const demo = demoUsers[username]
        if (demo && demo.password === password) {
          this.token = `demo-${username}`
          this.user = demo.user
          this.demo = true
          localStorage.setItem(TOKEN_KEY, this.token)
          return
        }
        throw e
      }
    },
    async fetchMe() {
      if (!this.token) return
      // 刷新后从 demo token 恢复用户（离线演示）
      if (this.token.startsWith('demo-')) {
        const username = this.token.slice(5)
        const demo = demoUsers[username]
        if (demo) {
          this.user = demo.user
          this.demo = true
        }
        return
      }
      this.user = await authApi.me()
    },
    hasPermission(perm: string) {
      return this.user?.permissions.includes(perm) ?? false
    },
    hasRole(role: string) {
      return this.user?.roles.includes(role) ?? false
    },
    logout() {
      this.token = null
      this.user = null
      this.demo = false
      localStorage.removeItem(TOKEN_KEY)
    },
  },
})