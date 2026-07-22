<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  status: string
}>()

const statusMap: Record<string, { label: string; class: string }> = {
  SUCCESS: { label: '已完成', class: 'bg-apple-green/10 text-apple-green' },
  STRONG: { label: '强关联', class: 'bg-apple-green/10 text-apple-green' },
  PARTIAL_SUCCESS: { label: '部分成功', class: 'bg-amber-500/10 text-amber-500' },
  WEAK: { label: '弱关联', class: 'bg-amber-500/10 text-amber-500' },
  FAILED: { label: '失败', class: 'bg-red-500/10 text-red-500' },
  DUPLICATE: { label: '重复', class: 'bg-apple-secondary-text/10 text-apple-secondary-text' },
  CANDIDATE: { label: '候选', class: 'bg-apple-blue/10 text-apple-blue' },
  RUNNING: { label: '运行中', class: 'bg-apple-blue/10 text-apple-blue' },
  PENDING: { label: '排队中', class: 'bg-apple-secondary-text/10 text-apple-secondary-text' },
}

const config = computed(() => statusMap[props.status] || { label: props.status, class: 'bg-apple-secondary-text/10 text-apple-secondary-text' })
</script>

<template>
  <span
    class="inline-flex items-center rounded-full px-2.5 py-0.5 text-[10px] font-bold tracking-wider uppercase"
    :class="config.class"
  >
    {{ config.label }}
  </span>
</template>
