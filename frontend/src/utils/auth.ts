import { ref } from 'vue'
import axios from 'axios'
import router from '@/router'

const token = ref(localStorage.getItem('token'))

export function useAuth() {
  const login = async (username, password) => {
    try {
      const response = await axios.post('http://localhost:8081/api/auth/login', {
        username,
        password
      })
      const newToken = response.data.token
      localStorage.setItem('token', newToken)
      token.value = newToken
      router.push('/dashboard')
    } catch (err) {
      console.error('Login failed', err)
      throw err
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
