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
  <div class="apple-card relative overflow-hidden p-6 flex flex-col justify-between group bg-[linear-gradient(180deg,rgba(7,11,23,0.42),rgba(7,11,23,0.18))]">
    <div class="pointer-events-none absolute inset-x-0 top-0 h-20 bg-gradient-to-b from-apple-blue/6 via-apple-green/3 to-transparent opacity-60"></div>
    <div>
      <div class="flex items-center justify-between mb-4">
        <span class="text-xs font-semibold text-apple-secondary-text uppercase tracking-wider">{{ label }}</span>
        <div v-if="trend" :class="trend.isUp ? 'text-apple-green' : 'text-red-500'" class="apple-soft-strip flex items-center gap-1 text-xs font-bold px-2 py-1 rounded-full">
          <TrendingUp v-if="trend.isUp" :size="12" />
          <TrendingDown v-else :size="12" />
          {{ trend.value }}%
        </div>
      </div>
      <div class="relative text-3xl font-bold tracking-tight text-apple-text transition-colors duration-300 group-hover:text-white">
        {{ value }}
      </div>
    </div>
    <div class="mt-4 text-xs text-apple-secondary-text leading-relaxed">
      {{ hint }}
    </div>
  </div>
</template>
