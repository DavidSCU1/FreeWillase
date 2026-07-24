<script setup lang="ts">
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  ChevronRight,
  Cpu,
  Dna,
  Microscope,
  Sparkles,
} from 'lucide-vue-next'

const router = useRouter()

const workbenches = [
  {
    key: 'nvidia',
    title: 'NVIDIA ESMFold',
    description: '单条蛋白结构预测，适合快速直连云端模型并查看三维结构结果。',
    route: '/prediction/nvidia',
    badge: 'Cloud Protein',
    points: ['固定单条蛋白输入', '支持 API Key / Base URL', '返回 PDB 结构与置信度'],
    accent: 'from-apple-blue/10 to-cyan-500/10',
  },
  {
    key: 'rnafold',
    title: 'RNAfold',
    description: '单条 RNA 二级结构预测，聚焦 dot-bracket、能量信息和 RNA 专属输入。',
    route: '/prediction/rnafold',
    badge: 'RNA Secondary',
    points: ['固定单条 RNA 输入', '无需额外 API Key', '返回 dot-bracket 与 MFE'],
    accent: 'from-emerald-500/10 to-teal-500/10',
  },
  {
    key: 'minifold',
    title: 'MiniFold',
    description: '本地推理工作台，处理链数、环境描述、加速后端和结构结果展示。',
    route: '/prediction/minifold',
    badge: 'Local Folding',
    points: ['支持环境描述与链数', '支持本地加速后端', '返回结构视图与执行概况'],
    accent: 'from-purple-500/10 to-fuchsia-500/10',
  },
] as const
</script>

<template>
  <div class="space-y-8 pb-20">
    <div class="apple-card p-6 md:p-7 bg-gradient-to-br from-apple-blue/8 via-transparent to-purple-500/8 border-apple-blue/10">
      <div class="flex flex-col gap-6 lg:flex-row lg:items-start lg:justify-between">
        <div class="space-y-3">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-apple bg-apple-blue/10 text-apple-blue flex items-center justify-center">
              <Sparkles :size="18" />
            </div>
            <div>
              <h1 class="text-3xl font-bold tracking-tight text-apple-text">预测模型中心</h1>
              <p class="text-sm text-apple-secondary-text">把不同预测能力拆成平级工作台，入口统一，规则清晰，页面职责单一。</p>
            </div>
          </div>

          <p class="max-w-2xl text-sm leading-relaxed text-apple-secondary-text">
            现在预测模块只保留 3 个独立页面：`NVIDIA ESMFold`、`RNAfold` 和 `MiniFold`。每个页面都只处理自己的输入规则、执行流程和结果展示，避免不同模型混在一起。
          </p>
        </div>

        <div class="grid grid-cols-3 gap-3 lg:min-w-[360px]">
          <div class="rounded-apple border border-apple-border bg-white/60 dark:bg-white/5 px-4 py-3">
            <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">工作台数</p>
            <p class="mt-1 text-sm font-semibold text-apple-text">3</p>
          </div>
          <div class="rounded-apple border border-apple-border bg-white/60 dark:bg-white/5 px-4 py-3">
            <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">云端</p>
            <p class="mt-1 text-sm font-semibold text-apple-text">2</p>
          </div>
          <div class="rounded-apple border border-apple-border bg-white/60 dark:bg-white/5 px-4 py-3">
            <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">本地</p>
            <p class="mt-1 text-sm font-semibold text-apple-text">1</p>
          </div>
        </div>
      </div>
    </div>

    <div class="grid grid-cols-1 xl:grid-cols-3 gap-8">
      <button
        v-for="bench in workbenches"
        :key="bench.key"
        type="button"
        class="apple-card p-6 text-left transition-all hover:-translate-y-1 hover:shadow-xl hover:shadow-apple-blue/10"
        @click="router.push(bench.route)"
      >
        <div class="space-y-6">
          <div class="flex items-start justify-between gap-4">
            <div class="space-y-3">
              <div class="w-10 h-10 rounded-apple flex items-center justify-center" :class="`bg-gradient-to-br ${bench.accent}`">
                <Cpu v-if="bench.key === 'minifold'" class="text-purple-500" :size="18" />
                <Dna v-else-if="bench.key === 'rnafold'" class="text-emerald-500" :size="18" />
                <Microscope v-else class="text-apple-blue" :size="18" />
              </div>
              <div>
                <span class="inline-flex rounded-full bg-apple-background px-3 py-1 text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">
                  {{ bench.badge }}
                </span>
                <h2 class="mt-3 text-xl font-bold text-apple-text">{{ bench.title }}</h2>
                <p class="mt-2 text-sm leading-relaxed text-apple-secondary-text">{{ bench.description }}</p>
              </div>
            </div>

            <ChevronRight class="text-apple-secondary-text" :size="18" />
          </div>

          <div class="space-y-3">
            <div
              v-for="point in bench.points"
              :key="point"
              class="rounded-apple border border-apple-border bg-apple-background/35 px-4 py-3 text-[12px] font-medium text-apple-text"
            >
              {{ point }}
            </div>
          </div>

          <div class="inline-flex items-center gap-2 text-xs font-bold text-apple-blue">
            进入工作台
            <ArrowRight :size="14" />
          </div>
        </div>
      </button>
    </div>
  </div>
</template>
