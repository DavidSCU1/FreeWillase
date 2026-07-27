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
    badge: '云端蛋白预测',
    points: ['固定单条蛋白输入', '支持 API Key / Base URL', '返回 PDB 结构与置信度'],
    accent: 'from-apple-blue/[0.1] to-cyan-400/[0.08]',
  },
  {
    key: 'trrosettarna',
    title: 'trRosettaRNA',
    description: 'RNA 三维结构预测，通过网页接口模拟实现长序列的深度学习折叠。',
    route: '/prediction/trrosettarna',
    badge: 'RNA 三维预测',
    points: ['支持 400nt 以内 RNA', '模拟浏览器会话提交', '返回 PDB 三维结构文件'],
    accent: 'from-amber-400/[0.1] to-rose-400/[0.08]',
  },
  {
    key: 'minifold',
    title: 'MiniFold',
    description: '本地推理工作台，处理链数、环境描述、加速后端和结构结果展示。',
    route: '/prediction/minifold',
    badge: '本地结构推理',
    points: ['支持环境描述与链数', '支持本地加速后端', '返回结构视图与执行概况'],
    accent: 'from-violet-400/[0.1] to-sky-400/[0.08]',
  },
] as const
</script>

<template>
  <div class="space-y-8 pb-20 motion-stagger">
    <div class="apple-card p-6 md:p-7 bg-[linear-gradient(180deg,rgba(9,14,25,0.46),rgba(9,14,25,0.18))]">
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
            现在预测模块保留 3 个独立页面：`NVIDIA ESMFold`、`trRosettaRNA` 和 `MiniFold`。每个页面都只处理自己的输入规则、执行流程和结果展示。
          </p>
        </div>

        <div class="grid grid-cols-3 gap-3 lg:min-w-[360px]">
          <div class="apple-soft-panel rounded-apple px-4 py-3">
            <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">工作台数</p>
            <p class="mt-1 text-sm font-semibold text-apple-text">3</p>
          </div>
          <div class="apple-soft-panel rounded-apple px-4 py-3">
            <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">云端</p>
            <p class="mt-1 text-sm font-semibold text-apple-text">2</p>
          </div>
          <div class="apple-soft-panel rounded-apple px-4 py-3">
            <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">本地</p>
            <p class="mt-1 text-sm font-semibold text-apple-text">1</p>
          </div>
        </div>
      </div>
    </div>

    <div class="grid grid-cols-1 xl:grid-cols-3 gap-8 motion-stagger">
      <button
        v-for="bench in workbenches"
        :key="bench.key"
        type="button"
        class="apple-card p-6 text-left bg-[linear-gradient(180deg,rgba(9,14,25,0.44),rgba(9,14,25,0.22))] transition-all hover:-translate-y-[1px] hover:shadow-[0_22px_52px_-42px_rgba(92,199,245,0.14)]"
        @click="router.push(bench.route)"
      >
        <div class="space-y-6">
          <div class="flex items-start justify-between gap-4">
            <div class="space-y-3">
              <div class="w-10 h-10 rounded-apple flex items-center justify-center" :class="`bg-gradient-to-br ${bench.accent}`">
                <Cpu v-if="bench.key === 'minifold'" class="text-violet-300" :size="18" />
                <Dna v-else-if="bench.key === 'trrosettarna'" class="text-amber-300" :size="18" />
                <Microscope v-else class="text-apple-blue" :size="18" />
              </div>
              <div>
                <span class="apple-soft-strip inline-flex rounded-full px-3 py-1 text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">
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
              class="rounded-apple border border-apple-border/50 bg-apple-background/[0.24] px-4 py-3 text-[12px] font-medium text-apple-text shadow-[inset_0_1px_0_rgba(255,255,255,0.016)]"
            >
              {{ point }}
            </div>
          </div>

          <div class="inline-flex items-center gap-2 text-xs font-bold text-apple-blue/90">
            进入工作台
            <ArrowRight :size="14" />
          </div>
        </div>
      </button>
    </div>
  </div>
</template>
