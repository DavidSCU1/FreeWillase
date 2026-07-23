import { ref } from 'vue'
import axios from 'axios'
import router from '@/router'

const token = ref(localStorage.getItem('token'))
const authClient = axios.create({
  baseURL: '/api',
})

export function useAuth() {
  const login = async (username, password) => {
    try {
      const response = await authClient.post('/auth/login', {
        username,
        password
      })
      const newToken = response.data.token
      localStorage.setItem('token', newToken)
      token.value = newToken
      router.push('/dashboard')
    } catch (err) {
      console.error('Login failed', err)
      if (axios.isAxiosError(err)) {
        const message = err.response?.data?.message
        if (typeof message === 'string' && message.trim()) {
          throw new Error(message)
        }
        if (err.code === 'ERR_NETWORK') {
          throw new Error('登录服务不可达，请确认后端已启动，或检查 Vite 代理与 8081 端口配置')
        }
      }
      throw new Error('登录失败，请稍后重试')
    }
  }

  const logout = () => {
    localStorage.removeItem('token')
    token.value = null
    router.push('/login')
  }

  const isAuthenticated = () => !!token.value

  return {
    token,
    login,
    logout,
    isAuthenticated
  }
}
