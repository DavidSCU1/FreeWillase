<script setup lang="ts">
import { computed } from 'vue'
import type { ImportTask } from '@/types'
import { Database, CheckCircle2, XCircle, AlertCircle, Clock, Loader2 } from 'lucide-vue-next'

const props = defineProps<{
  task: ImportTask | null
}>()

const statusConfig = {
  SUCCESS: { icon: CheckCircle2, color: 'text-emerald-300', label: '已完成', bgColor: 'status-pill status-pill-success' },
  FAILED: { icon: XCircle, color: 'text-rose-300', label: '任务失败', bgColor: 'status-pill status-pill-failed' },
  RUNNING: { icon: Loader2, color: 'text-sky-300', label: '实时处理中', bgColor: 'status-pill status-pill-running' },
  PENDING: { icon: Clock, color: 'text-apple-secondary-text', label: '排队等待', bgColor: 'status-pill status-pill-pending' },
  PARTIAL_SUCCESS: { icon: AlertCircle, color: 'text-amber-300', label: '部分成功', bgColor: 'status-pill status-pill-partial' },
}

const progressPercentage = computed(() => {
  if (!props.task || props.task.totalCount === 0) return 0
  const processed = (props.task.successCount || 0) + (props.task.failedCount || 0) + (props.task.duplicateCount || 0)
  return Math.min(Math.round((processed / props.task.totalCount) * 100), 100)
})

const isProcessing = computed(() => props.task?.status === 'RUNNING')
</script>

<template>
  <div class="apple-card import-task-card relative overflow-hidden transition-all duration-500" :class="{ 'ring-1 ring-apple-blue/12 shadow-[0_0_0_1px_rgba(56,189,248,0.04),0_22px_56px_-40px_rgba(56,189,248,0.18)]': isProcessing }">
    <div class="pointer-events-none absolute inset-x-0 top-0 h-24 bg-[linear-gradient(180deg,rgba(56,189,248,0.05),rgba(45,212,191,0.025)_40%,transparent)]"></div>
    <!-- Header: Status and Task Info -->
    <div class="apple-soft-panel relative p-6 flex items-center justify-between border-b border-white/[0.04]">
      <div class="flex items-center gap-4">
        <div class="w-10 h-10 rounded-apple bg-apple-blue/12 text-apple-blue flex items-center justify-center shadow-inner shadow-apple-blue/10">
          <Database :size="20" :class="{ 'animate-pulse': isProcessing }" />
        </div>
        <div>
          <div class="flex items-center gap-2">
            <h2 class="text-sm font-bold text-apple-text tracking-tight">NCBI 导入任务</h2>
            <span v-if="isProcessing" class="flex h-2 w-2 rounded-full bg-apple-blue animate-ping"></span>
          </div>
          <p class="text-xs font-semibold text-apple-secondary-text mt-1">
            {{ task?.taskName || '等待任务启动' }}
          </p>
          <p class="text-[10px] text-apple-secondary-text uppercase tracking-widest font-bold mt-1">
            {{ isProcessing ? '实时任务流' : '任务结果面板' }}
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
    <div v-if="isProcessing" class="h-1.5 w-full overflow-hidden bg-[rgba(56,189,248,0.06)]">
      <div 
        class="h-full bg-[linear-gradient(90deg,rgba(56,189,248,0.72),rgba(45,212,191,0.58))] transition-all duration-700 ease-out shadow-[0_0_14px_rgba(56,189,248,0.22)]"
        :style="{ width: `${progressPercentage}%` }"
      ></div>
    </div>

    <div v-if="task" class="p-6">
      <!-- Metrics Grid -->
      <div class="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <div class="metric-card metric-card-neutral p-5 rounded-apple group transition-colors">
          <p class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest mb-2 flex items-center justify-between">
            条目总数
            <span class="text-apple-blue opacity-0 group-hover:opacity-100 transition-opacity">待处理 accession</span>
          </p>
          <p class="text-3xl font-bold text-apple-text tracking-tighter">{{ task.totalCount }}</p>
        </div>
        
        <div class="metric-card metric-card-success p-5 rounded-apple group transition-colors">
          <p class="text-[10px] font-bold text-emerald-300 uppercase tracking-widest mb-2 flex items-center justify-between">
            导入成功
            <span class="opacity-0 group-hover:opacity-100 transition-opacity">已写入本地酶库</span>
          </p>
          <div class="flex items-baseline gap-1">
            <p class="text-3xl font-bold text-emerald-300 tracking-tighter">{{ task.successCount }}</p>
            <span class="text-[10px] font-bold text-emerald-300/55" v-if="isProcessing">进行中</span>
          </div>
        </div>

        <div class="metric-card metric-card-failed p-5 rounded-apple group transition-colors">
          <p class="text-[10px] font-bold text-rose-300 uppercase tracking-widest mb-2 flex items-center justify-between">
            导入失败
            <span class="opacity-0 group-hover:opacity-100 transition-opacity">需回查原因</span>
          </p>
          <p class="text-3xl font-bold text-rose-300 tracking-tighter">{{ task.failedCount }}</p>
        </div>

        <div class="metric-card metric-card-duplicate p-5 rounded-apple group transition-colors">
          <p class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest mb-2 flex items-center justify-between">
            重复条目
            <span class="text-apple-blue opacity-0 group-hover:opacity-100 transition-opacity">已在本地存在</span>
          </p>
          <p class="text-3xl font-bold text-apple-text tracking-tighter">{{ task.duplicateCount }}</p>
        </div>
      </div>

      <!-- Live Stream Table -->
      <div class="apple-soft-panel overflow-hidden rounded-apple shadow-[0_18px_44px_-40px_rgba(2,6,23,0.8)]">
        <div class="px-4 py-3 border-b border-white/[0.04] flex items-center justify-between bg-[linear-gradient(180deg,rgba(56,189,248,0.04),rgba(8,12,23,0.1))]">
          <span class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">任务执行明细</span>
          <span class="text-[10px] font-bold text-apple-blue" v-if="isProcessing">实时滚动中</span>
        </div>
        <div class="max-h-[300px] overflow-y-auto no-scrollbar">
          <table class="w-full text-left border-collapse">
            <thead class="sticky top-0 z-10 border-b border-white/[0.04] bg-[rgba(9,13,24,0.78)] backdrop-blur-md">
              <tr class="text-[9px] uppercase tracking-widest font-bold text-apple-secondary-text">
                <th class="px-6 py-3">Accession</th>
                <th class="px-6 py-3 text-center">状态</th>
                <th class="px-6 py-3">说明</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-white/[0.035]">
              <transition-group name="list">
                <tr v-for="item in task.items" :key="`${item.accession}-${item.status}-${item.enzymeId}`" class="text-xs group transition-colors hover:bg-white/[0.025]">
                  <td class="px-6 py-4 font-mono font-bold text-apple-text">{{ item.accession }}</td>
                  <td class="px-6 py-4 text-center">
                    <span 
                      class="px-2 py-0.5 rounded-full text-[9px] font-bold uppercase tracking-tighter transition-all"
                      :class="{
                        'bg-emerald-400/10 text-emerald-300 ring-1 ring-emerald-400/10': item.status === 'SUCCESS',
                        'bg-rose-400/10 text-rose-300 ring-1 ring-rose-400/10': item.status === 'FAILED',
                        'bg-sky-400/10 text-sky-300 ring-1 ring-sky-400/10': item.status === 'DUPLICATE'
                      }"
                    >
                      {{ item.status }}
                    </span>
                  </td>
                  <td class="px-6 py-4 text-apple-secondary-text text-[10px] leading-relaxed italic max-w-[240px]">
                    {{ item.message || '处理完成，已写入任务结果。' }}
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
      <div class="apple-soft-panel w-20 h-20 rounded-full flex items-center justify-center mx-auto mb-6 text-apple-secondary-text/20 shadow-inner">
        <Database :size="40" />
      </div>
      <h3 class="text-sm font-bold text-apple-text mb-2 uppercase tracking-widest">等待导入任务启动</h3>
      <p class="text-xs text-apple-secondary-text max-w-xs mx-auto leading-relaxed">
        提交 accession 后，这里会持续展示抓取、去重、入库和失败回执，方便你跟踪整批任务状态。
      </p>
    </div>
  </div>
</template>

<style scoped>
.import-task-card {
  background:
    radial-gradient(circle at top left, rgba(56, 189, 248, 0.05), transparent 30%),
    radial-gradient(circle at 82% 8%, rgba(45, 212, 191, 0.035), transparent 26%),
    linear-gradient(180deg, rgba(7, 12, 24, 0.72), rgba(8, 13, 26, 0.46));
}

.status-pill {
  border: 1px solid rgba(148, 163, 184, 0.06);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.02);
}

.status-pill-success {
  background: linear-gradient(180deg, rgba(16, 185, 129, 0.14), rgba(6, 78, 59, 0.16));
}

.status-pill-failed {
  background: linear-gradient(180deg, rgba(251, 113, 133, 0.14), rgba(76, 5, 25, 0.18));
}

.status-pill-running {
  background: linear-gradient(180deg, rgba(56, 189, 248, 0.16), rgba(12, 74, 110, 0.18));
}

.status-pill-pending {
  background: linear-gradient(180deg, rgba(30, 41, 59, 0.76), rgba(15, 23, 42, 0.66));
}

.status-pill-partial {
  background: linear-gradient(180deg, rgba(251, 191, 36, 0.14), rgba(120, 53, 15, 0.18));
}

.metric-card {
  border: 1px solid rgba(71, 85, 105, 0.08);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.02),
    0 14px 32px -30px rgba(2, 6, 23, 0.82);
}

.metric-card-neutral {
  background:
    linear-gradient(180deg, rgba(10, 15, 27, 0.58), rgba(8, 12, 23, 0.38)),
    radial-gradient(circle at top left, rgba(56, 189, 248, 0.035), transparent 36%);
}

.metric-card-success {
  border-color: rgba(52, 211, 153, 0.08);
  background:
    linear-gradient(180deg, rgba(6, 95, 70, 0.16), rgba(6, 24, 23, 0.52)),
    radial-gradient(circle at top right, rgba(52, 211, 153, 0.06), transparent 34%);
}

.metric-card-failed {
  border-color: rgba(251, 113, 133, 0.08);
  background:
    linear-gradient(180deg, rgba(76, 5, 25, 0.14), rgba(18, 8, 18, 0.5)),
    radial-gradient(circle at top right, rgba(251, 113, 133, 0.05), transparent 34%);
}

.metric-card-duplicate {
  background:
    linear-gradient(180deg, rgba(10, 15, 27, 0.58), rgba(8, 12, 23, 0.38)),
    radial-gradient(circle at top right, rgba(56, 189, 248, 0.04), transparent 34%);
}

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
