<script setup lang="ts">
import { computed } from 'vue'
import type { ImportTask } from '@/types'
import { Database, CheckCircle2, XCircle, AlertCircle, Clock, Loader2 } from 'lucide-vue-next'

const props = defineProps<{
  task: ImportTask | null
}>()

const statusConfig = {
  SUCCESS: { icon: CheckCircle2, color: 'text-apple-green', label: '已完成', bgColor: 'bg-apple-green/10' },
  FAILED: { icon: XCircle, color: 'text-red-500', label: '任务失败', bgColor: 'bg-red-500/10' },
  RUNNING: { icon: Loader2, color: 'text-apple-blue', label: '实时处理中', bgColor: 'bg-apple-blue/10' },
  PENDING: { icon: Clock, color: 'text-apple-secondary-text', label: '排队等待', bgColor: 'bg-black/5 dark:bg-white/5' },
  PARTIAL_SUCCESS: { icon: AlertCircle, color: 'text-amber-500', label: '部分成功', bgColor: 'bg-amber-500/10' },
}

const progressPercentage = computed(() => {
  if (!props.task || props.task.totalCount === 0) return 0
  const processed = (props.task.successCount || 0) + (props.task.failedCount || 0) + (props.task.duplicateCount || 0)
  return Math.min(Math.round((processed / props.task.totalCount) * 100), 100)
})

const isProcessing = computed(() => props.task?.status === 'RUNNING')
</script>

<template>
  <div class="apple-card overflow-hidden transition-all duration-500" :class="{ 'ring-2 ring-apple-blue/30': isProcessing }">
    <!-- Header: Status and Task Info -->
    <div class="p-6 flex items-center justify-between border-b border-apple-border bg-white/50 dark:bg-black/20 backdrop-blur-sm">
      <div class="flex items-center gap-4">
        <div class="w-10 h-10 rounded-apple bg-apple-blue/10 text-apple-blue flex items-center justify-center shadow-inner">
          <Database :size="20" :class="{ 'animate-pulse': isProcessing }" />
        </div>
        <div>
          <div class="flex items-center gap-2">
            <h2 class="text-sm font-bold text-apple-text tracking-tight">{{ task?.taskName || '导入任务状态' }}</h2>
            <span v-if="isProcessing" class="flex h-2 w-2 rounded-full bg-apple-blue animate-ping"></span>
          </div>
          <p class="text-[10px] text-apple-secondary-text uppercase tracking-widest font-bold mt-0.5">
            {{ isProcessing ? 'Live Stream' : 'Task Result' }}
          </p>
        </div>
      </div>
      
      <div v-if="task" class="flex items-center gap-2 px-4 py-1.5 rounded-full transition-all duration-300" :class="statusConfig[task.status]?.bgColor">
        <component 
          :is="statusConfig[task.status]?.icon" 
          :size="14" 
          :class="[statusConfig[task.status]?.color, { 'animate-spin': task.status === 'RUNNING' }]" 
        />
        <span class="text-[10px] font-bold" :class="statusConfig[task.status]?.color">{{ statusConfig[task.status]?.label }}</span>
      </div>
    </div>

    <!-- Progress Bar (Only show when running) -->
    <div v-if="isProcessing" class="h-1.5 w-full bg-black/5 dark:bg-white/5 overflow-hidden">
      <div 
        class="h-full bg-apple-blue transition-all duration-700 ease-out shadow-[0_0_10px_rgba(0,113,227,0.5)]"
        :style="{ width: `${progressPercentage}%` }"
      ></div>
    </div>

    <div v-if="task" class="p-6">
      <!-- Metrics Grid -->
      <div class="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <div class="p-5 rounded-apple bg-apple-background dark:bg-white/5 border border-apple-border shadow-sm group hover:border-apple-blue/30 transition-colors">
          <p class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest mb-2 flex items-center justify-between">
            Total
            <span class="text-apple-blue opacity-0 group-hover:opacity-100 transition-opacity">总数</span>
          </p>
          <p class="text-3xl font-bold text-apple-text tracking-tighter">{{ task.totalCount }}</p>
        </div>
        
        <div class="p-5 rounded-apple bg-apple-green/5 border border-apple-green/20 shadow-sm group hover:bg-apple-green/10 transition-colors">
          <p class="text-[10px] font-bold text-apple-green uppercase tracking-widest mb-2 flex items-center justify-between">
            Success
            <span class="opacity-0 group-hover:opacity-100 transition-opacity">成功</span>
          </p>
          <div class="flex items-baseline gap-1">
            <p class="text-3xl font-bold text-apple-green tracking-tighter">{{ task.successCount }}</p>
            <span class="text-[10px] font-bold text-apple-green opacity-50" v-if="isProcessing">...ing</span>
          </div>
        </div>

        <div class="p-5 rounded-apple bg-red-500/5 border border-red-500/20 shadow-sm group hover:bg-red-500/10 transition-colors">
          <p class="text-[10px] font-bold text-red-500 uppercase tracking-widest mb-2 flex items-center justify-between">
            Failed
            <span class="opacity-0 group-hover:opacity-100 transition-opacity">失败</span>
          </p>
          <p class="text-3xl font-bold text-red-500 tracking-tighter">{{ task.failedCount }}</p>
        </div>

        <div class="p-5 rounded-apple bg-apple-background dark:bg-white/5 border border-apple-border shadow-sm group hover:border-apple-blue/30 transition-colors">
          <p class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest mb-2 flex items-center justify-between">
            Duplicate
            <span class="text-apple-blue opacity-0 group-hover:opacity-100 transition-opacity">重复</span>
          </p>
          <p class="text-3xl font-bold text-apple-text tracking-tighter">{{ task.duplicateCount }}</p>
        </div>
      </div>

      <!-- Live Stream Table -->
      <div class="overflow-hidden rounded-apple border border-apple-border bg-white/50 dark:bg-black/20 backdrop-blur-sm shadow-sm">
        <div class="px-4 py-3 border-b border-apple-border bg-black/5 dark:bg-white/5 flex items-center justify-between">
          <span class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">任务执行详情 (Live)</span>
          <span class="text-[10px] font-bold text-apple-blue" v-if="isProcessing">实时滚动中</span>
        </div>
        <div class="max-h-[300px] overflow-y-auto no-scrollbar">
          <table class="w-full text-left border-collapse">
            <thead class="sticky top-0 bg-white/80 dark:bg-black/80 backdrop-blur-md z-10 shadow-sm">
              <tr class="text-[9px] uppercase tracking-widest font-bold text-apple-secondary-text">
                <th class="px-6 py-3">Accession</th>
                <th class="px-6 py-3 text-center">Status</th>
                <th class="px-6 py-3">Message</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-apple-border/50">
              <transition-group name="list">
                <tr v-for="item in task.items" :key="`${item.accession}-${item.status}-${item.enzymeId}`" class="text-xs group hover:bg-apple-blue/5 transition-colors">
                  <td class="px-6 py-4 font-mono font-bold text-apple-text">{{ item.accession }}</td>
                  <td class="px-6 py-4 text-center">
                    <span 
                      class="px-2 py-0.5 rounded-full text-[9px] font-bold uppercase tracking-tighter transition-all"
                      :class="{
                        'bg-apple-green/10 text-apple-green': item.status === 'SUCCESS',
                        'bg-red-500/10 text-red-500': item.status === 'FAILED',
                        'bg-apple-blue/10 text-apple-blue': item.status === 'DUPLICATE'
                      }"
                    >
                      {{ item.status }}
                    </span>
                  </td>
                  <td class="px-6 py-4 text-apple-secondary-text text-[10px] leading-relaxed italic max-w-[240px]">
                    {{ item.message || 'Processing complete' }}
                  </td>
                </tr>
              </transition-group>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div v-else class="p-24 text-center">
      <div class="w-20 h-20 bg-apple-light-gray dark:bg-white/5 rounded-full flex items-center justify-center mx-auto mb-6 text-apple-secondary-text/20 shadow-inner">
        <Database :size="40" />
      </div>
      <h3 class="text-sm font-bold text-apple-text mb-2 uppercase tracking-widest">等待捕捉“自由意志”</h3>
      <p class="text-xs text-apple-secondary-text max-w-xs mx-auto leading-relaxed">
        一旦你按下导入按钮，这里将开启一场针对 NCBI 的“围猎直播”，我们将实时追踪每一只酶的“归案”进度。
      </p>
    </div>
  </div>
</template>

<style scoped>
.list-enter-active,
.list-leave-active {
  transition: all 0.5s ease;
}
.list-enter-from {
  opacity: 0;
  transform: translateX(-10px);
}
.list-leave-to {
  opacity: 0;
  transform: translateX(10px);
}
</style>
