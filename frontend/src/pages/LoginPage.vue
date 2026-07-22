<script setup lang="ts">
import { ref } from 'vue'
import { useAuth } from '@/utils/auth'
import { FlaskConical, Loader2, AlertCircle } from 'lucide-vue-next'

const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

const { login } = useAuth()

const handleLogin = async () => {
  if (!username.value || !password.value) {
    error.value = '请输入用户名和密码'
    return
  }

  loading.value = true
  error.value = ''
  try {
    await login(username.value, password.value)
  } catch (err) {
    error.value = '登录失败，请检查凭据'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen bg-apple-background flex items-center justify-center p-6">
    <div class="w-full max-w-md space-y-8">
      <div class="text-center space-y-2">
        <div class="w-16 h-16 bg-apple-blue rounded-apple-xl flex items-center justify-center text-white mx-auto shadow-lg shadow-apple-blue/20">
          <FlaskConical :size="32" />
        </div>
        <h1 class="text-2xl font-bold tracking-tight text-apple-text pt-4">FreeWillase</h1>
        <p class="text-sm text-apple-secondary-text">酶信息管理与智能预测平台</p>
      </div>

      <div class="apple-card p-8 space-y-6">
        <div class="space-y-4">
          <div class="space-y-2">
            <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest ml-1">用户名</label>
            <input 
              v-model="username"
              type="text" 
              placeholder="admin"
              class="apple-input"
              @keyup.enter="handleLogin"
            />
          </div>
          <div class="space-y-2">
            <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest ml-1">密码</label>
            <input 
              v-model="password"
              type="password" 
              placeholder="••••••••"
              class="apple-input"
              @keyup.enter="handleLogin"
            />
          </div>
        </div>

        <div v-if="error" class="p-3 rounded-apple bg-red-500/5 border border-red-500/10 flex items-center gap-2 text-xs text-red-500">
          <AlertCircle :size="14" />
          {{ error }}
        </div>

        <button 
          @click="handleLogin"
          :disabled="loading"
          class="apple-button-primary w-full flex items-center justify-center gap-2 h-12"
        >
          <Loader2 v-if="loading" :size="18" class="animate-spin" />
          <span v-else>登录</span>
        </button>

        <div class="text-center">
          <p class="text-[10px] text-apple-secondary-text uppercase tracking-widest font-bold">
            默认凭据: admin / admin123
          </p>
        </div>
      </div>
      
      <p class="text-center text-xs text-apple-secondary-text">
        &copy; 2026 FreeWillase. Apple Style Design System.
      </p>
    </div>
  </div>
</template>
