<script setup lang="ts">
import { TrendingUp, TrendingDown } from 'lucide-vue-next'

defineProps<{
  label: string
  value: string | number
  hint: string
  trend?: {
    value: number
    isUp: boolean
  }
}>()
</script>

<template>
  <div class="apple-card p-6 flex flex-col justify-between group">
    <div>
      <div class="flex items-center justify-between mb-4">
        <span class="text-xs font-semibold text-apple-secondary-text uppercase tracking-wider">{{ label }}</span>
        <div v-if="trend" :class="trend.isUp ? 'text-apple-green' : 'text-red-500'" class="flex items-center gap-1 text-xs font-bold bg-black/5 dark:bg-white/5 px-2 py-1 rounded-full">
          <TrendingUp v-if="trend.isUp" :size="12" />
          <TrendingDown v-else :size="12" />
          {{ trend.value }}%
        </div>
      </div>
      <div class="text-3xl font-bold tracking-tight text-apple-text group-hover:scale-105 transition-transform origin-left duration-500">
        {{ value }}
      </div>
    </div>
    <div class="mt-4 text-xs text-apple-secondary-text leading-relaxed">
      {{ hint }}
    </div>
  </div>
</template>
