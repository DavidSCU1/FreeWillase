<script setup lang="ts">
import { ref } from 'vue'
import { useAuth } from '@/utils/auth'
import { Loader2, AlertCircle } from 'lucide-vue-next'
import BrandMark from '@/components/BrandMark.vue'

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
    error.value = err instanceof Error ? err.message : '登录失败，请检查凭据'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="relative min-h-screen overflow-hidden bg-apple-background flex items-center justify-center p-6">
    <div class="pointer-events-none absolute inset-x-0 top-0 h-80 bg-[radial-gradient(circle_at_top,rgba(56,189,248,0.2),transparent_48%)]"></div>
    <div class="pointer-events-none absolute left-[-6rem] top-24 h-80 w-80 rounded-full bg-apple-green/12 blur-3xl"></div>
    <div class="pointer-events-none absolute right-[-8rem] bottom-8 h-96 w-96 rounded-full bg-apple-blue/12 blur-3xl"></div>

    <div class="relative w-full max-w-md space-y-8">
      <div class="text-center space-y-3">
        <div class="mx-auto flex h-16 w-16 items-center justify-center rounded-apple-xl border border-apple-border/70 bg-[linear-gradient(135deg,rgba(9,9,11,0.98),rgba(24,24,27,0.94)_66%,rgba(56,189,248,0.88))] shadow-apple">
          <BrandMark class="h-10 w-10" />
        </div>
        <div class="space-y-2 pt-4">
          <p class="text-[10px] font-bold uppercase tracking-[0.32em] text-apple-secondary-text">平台登录</p>
          <h1 class="text-3xl font-bold tracking-tight text-apple-text">FreeWillase</h1>
          <p class="text-sm leading-7 text-apple-secondary-text">酶信息管理、文献证据整理与结构预测统一入口。</p>
        </div>
      </div>

      <div class="apple-card space-y-6 p-8">
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
        &copy; 2026 FreeWillase. 面向酶研究流程的统一工作界面。
      </p>
    </div>
  </div>
</template>
