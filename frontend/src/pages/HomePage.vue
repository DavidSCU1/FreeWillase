<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ArrowRight, Database, Dna, Microscope, SearchCheck, Activity } from 'lucide-vue-next'
import { RouterLink } from 'vue-router'
import ImportTaskCard from '@/components/ImportTaskCard.vue'
import MetricCard from '@/components/MetricCard.vue'
import BrandMark from '@/components/BrandMark.vue'
import BrandPuzzlePanel from '@/components/BrandPuzzlePanel.vue'
import { useNcbiImport } from '@/composables/useNcbiImport'
import { getDashboardStats } from '@/utils/api'

const { task, refreshAll } = useNcbiImport()
const stats = ref({
  enzymeCount: 0,
  successRatio: '0%',
  literatureCoverage: '0%',
  systemStatus: 'Normal'
})

onMounted(async () => {
  try {
    await refreshAll()
    stats.value = await getDashboardStats()
  } catch {
    // Keep dashboard visible
  }
})

const features = [
  {
    title: 'NCBI 导入建库',
    desc: '自动化从 NCBI 获取蛋白、基因与物种元数据，建立本地标准化酶库。',
    icon: Database,
    color: 'bg-apple-blue/[0.09] text-apple-blue',
    to: '/importer',
    action: '前往导入页'
  },
  {
    title: '文献自动匹配',
    desc: '基于多维打分模型，自动关联 PubMed 文献，提供结构与功能证据。',
    icon: SearchCheck,
    color: 'bg-apple-green/[0.09] text-apple-green',
    to: '/matcher',
    action: '前往文献匹配'
  },
  {
    title: '3D 结构工作站',
    desc: '深度集成 Mol* 渲染引擎，支持活性位点高亮与文献联动展示。',
    icon: Dna,
    color: 'bg-violet-400/10 text-violet-300',
    to: '/library/imported',
    action: '查看结构工作站'
  },
  {
    title: '智能预测接口',
    desc: '提供 MiniFold 本地物理推理入口，聚焦序列、环境、链数、加速配置与结构结果返回。',
    icon: Microscope,
    color: 'bg-amber-400/10 text-amber-300',
    to: '/prediction/minifold',
    action: '打开 MiniFold'
  }
]
</script>

<template>
  <div class="space-y-12 motion-stagger">
    <!-- Hero Section -->
    <section class="relative overflow-hidden rounded-[36px] px-8 py-10 md:px-12 md:py-14 bg-[linear-gradient(180deg,rgba(9,14,25,0.58),rgba(8,12,23,0.22))] shadow-[0_34px_92px_-72px_rgba(2,6,23,0.9)]">
      <div class="absolute -right-24 top-10 h-64 w-64 rounded-full bg-apple-blue/[0.06] blur-3xl"></div>
      <div class="absolute -left-20 bottom-0 h-56 w-56 rounded-full bg-apple-green/[0.06] blur-3xl"></div>
      <div class="absolute inset-x-12 top-0 h-px bg-[linear-gradient(90deg,transparent,rgba(255,255,255,0.05),transparent)]"></div>

      <div class="relative z-10 flex flex-col gap-10 xl:flex-row xl:items-stretch">
        <div class="flex-1 space-y-7">
          <div class="flex items-center gap-4">
            <BrandMark class="h-14 w-14 shrink-0" />
            <div>
              <p class="text-xs font-bold uppercase tracking-[0.32em] text-apple-secondary-text">FreeWillase 酶平台</p>
              <p class="mt-1 text-sm text-apple-secondary-text">面向酶库整理、文献证据管理与结构预测的一体化工作站。</p>
            </div>
          </div>
          <h1 class="max-w-4xl text-4xl font-bold tracking-tight text-apple-text leading-[1.02] md:text-5xl xl:text-6xl">
            管理酶条目、文献证据和预测结果
           
          </h1>
          <p class="max-w-2xl text-base leading-8 text-apple-secondary-text md:text-lg">
            这里集中提供 accession 导入、酶库整理、文献匹配和结构查看等功能，方便研究人员按条目持续补全信息并跟踪分析结果。
          </p>
          <div class="flex flex-wrap gap-3 text-xs font-semibold text-apple-secondary-text">
            <span class="rounded-full bg-white/[0.025] px-3 py-1.5 shadow-[inset_0_1px_0_rgba(255,255,255,0.022)]">统一任务视图</span>
            <span class="rounded-full bg-white/[0.025] px-3 py-1.5 shadow-[inset_0_1px_0_rgba(255,255,255,0.022)]">导入与预测分仓管理</span>
            <span class="rounded-full bg-white/[0.025] px-3 py-1.5 shadow-[inset_0_1px_0_rgba(255,255,255,0.022)]">结构与文献联动查看</span>
          </div>
          <div class="flex flex-wrap gap-4 pt-4">
            <RouterLink to="/importer" class="apple-button-primary flex items-center gap-2">
              开始导入 Accession
              <ArrowRight :size="16" />
            </RouterLink>
            <RouterLink to="/library" class="apple-button-secondary">
              浏览酶库中心
            </RouterLink>
          </div>
          <BrandPuzzlePanel class="max-w-[680px] pt-2" />
        </div>
        
        <div class="grid gap-4 xl:w-[420px]">
          <div class="rounded-[28px] bg-white/[0.024] p-6 shadow-[inset_0_1px_0_rgba(255,255,255,0.022),0_22px_54px_-48px_rgba(2,6,23,0.84)] backdrop-blur-sm">
            <p class="text-[10px] font-bold uppercase tracking-[0.3em] text-apple-secondary-text">平台概览</p>
            <h2 class="mt-3 text-2xl font-semibold tracking-tight text-apple-text">围绕真实科研流程组织数据与工具。</h2>
            <p class="mt-3 text-sm leading-7 text-apple-secondary-text">
              每个入口都直接对应实际工作内容，减少跳转和解释性界面，让信息更快落到可操作的页面上。
            </p>
          </div>

          <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-2">
            <RouterLink
              v-for="feature in features"
              :key="feature.title"
              :to="feature.to"
              class="group rounded-[24px] bg-black/[0.12] p-5 shadow-[inset_0_1px_0_rgba(255,255,255,0.02)] backdrop-blur-sm transition-all duration-300 hover:bg-black/[0.15] hover:-translate-y-[2px] hover:shadow-[inset_0_1px_0_rgba(255,255,255,0.028),0_18px_36px_-28px_rgba(15,23,42,0.82)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-apple-blue/[0.32]"
            >
              <div :class="feature.color" class="mb-4 flex h-10 w-10 items-center justify-center rounded-apple">
                <component :is="feature.icon" :size="18" />
              </div>
              <h3 class="mb-1 text-sm font-bold text-apple-text">{{ feature.title }}</h3>
              <p class="text-[11px] leading-6 text-apple-secondary-text">{{ feature.desc }}</p>
              <div class="mt-4 flex items-center gap-2 text-[11px] font-semibold text-apple-blue/[0.72] transition-colors duration-300 group-hover:text-apple-blue/90">
                <span>{{ feature.action }}</span>
                <ArrowRight :size="14" />
              </div>
            </RouterLink>
          </div>

          <div class="grid grid-cols-2 gap-4">
            <div class="rounded-[24px] bg-black/[0.12] p-4 shadow-[inset_0_1px_0_rgba(255,255,255,0.02)]">
              <p class="text-[10px] font-bold uppercase tracking-[0.24em] text-apple-secondary-text">核心模块</p>
              <p class="mt-2 text-lg font-semibold text-apple-text">4 项</p>
            </div>
            <div class="rounded-[24px] bg-black/[0.12] p-4 shadow-[inset_0_1px_0_rgba(255,255,255,0.02)]">
              <p class="text-[10px] font-bold uppercase tracking-[0.24em] text-apple-secondary-text">当前状态</p>
              <p class="mt-2 text-lg font-semibold text-apple-text">可继续工作</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Metrics Grid -->
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 motion-stagger">
      <MetricCard 
        label="数据库规模" 
        :value="stats.enzymeCount" 
        hint="当前本地存储的酶条目总数，来源于 NCBI 自动建库。"
        :trend="{ value: 12, isUp: true }"
      />
      <MetricCard 
        label="任务成功率" 
        :value="stats.successRatio" 
        hint="最近一次批量导入任务的执行效率反馈。"
      />
      <MetricCard 
        label="文献关联度" 
        :value="stats.literatureCoverage" 
        hint="已匹配文献条目占总酶库的比例，展示数据深度。"
        :trend="{ value: 5, isUp: true }"
      />
      <MetricCard 
        label="系统负载" 
        :value="stats.systemStatus" 
        hint="后端任务调度系统当前运行状态。"
      />
    </div>

    <!-- Main Content Grid -->
    <div class="grid grid-cols-1 xl:grid-cols-2 gap-8 items-start motion-stagger">
      <ImportTaskCard :task="task" />

      <!-- Activity Card -->
      <div class="apple-card h-full p-6">
        <div class="flex items-center gap-3 mb-6">
          <div class="w-8 h-8 rounded-apple bg-apple-blue/10 text-apple-blue flex items-center justify-center">
            <Activity :size="16" />
          </div>
          <div>
            <h2 class="text-sm font-bold text-apple-text">最近动态</h2>
            <p class="text-xs text-apple-secondary-text">导入任务、证据匹配与结构结果会集中回显在这里。</p>
          </div>
        </div>
        <div class="space-y-6">
          <div v-for="i in 3" :key="i" class="relative pl-6 pb-6 border-l border-apple-border/60 last:border-0 last:pb-0">
            <div class="absolute left-[-5px] top-1 w-2 h-2 rounded-full bg-apple-blue shadow-[0_0_0_4px_rgba(92,199,245,0.06)]"></div>
            <p class="text-[10px] text-apple-secondary-text font-bold uppercase mb-1 tracking-[0.24em]">2026-07-20 15:30</p>
            <h4 class="text-xs font-bold text-apple-text mb-1">批量导入任务完成</h4>
            <p class="text-xs text-apple-secondary-text leading-6">成功导入 12 个 Accession，包含 WP_012345678.1 等。</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
