<script setup lang="ts">
import { Database } from 'lucide-vue-next'

defineProps<{
  email: string
  apiKey: string
}>()

const emit = defineEmits<{
  'update:email': [value: string]
  'update:apiKey': [value: string]
}>()
</script>

<template>
  <div class="p-6 rounded-apple bg-apple-background/50 border border-apple-border space-y-4">
    <div class="flex items-center gap-2">
      <Database :size="14" class="text-apple-blue" />
      <h3 class="text-xs font-bold text-apple-text">NCBI 凭据 (可选 - 用于提高请求频率上限)</h3>
    </div>
    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
      <div class="space-y-1.5">
        <label class="text-[9px] font-bold text-apple-secondary-text uppercase tracking-widest ml-1">NCBI 注册邮箱</label>
        <input 
          :value="email"
          @input="emit('update:email', ($event.target as HTMLInputElement).value)"
          type="email" 
          placeholder="your-email@example.com" 
          class="apple-input py-2 text-xs"
        />
      </div>
      <div class="space-y-1.5">
        <label class="text-[9px] font-bold text-apple-secondary-text uppercase tracking-widest ml-1">NCBI API Key</label>
        <input 
          :value="apiKey"
          @input="emit('update:apiKey', ($event.target as HTMLInputElement).value)"
          type="password" 
          placeholder="请输入您的 API Key" 
          class="apple-input py-2 text-xs"
        />
      </div>
    </div>
    <p class="text-[10px] text-apple-secondary-text italic">注：提供凭据可将 NCBI 请求频率从 3次/秒 提升至 10次/秒，推荐在批量导入时使用。</p>
  </div>
</template>
