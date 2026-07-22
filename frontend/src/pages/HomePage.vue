<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowRight, Database, Dna, Microscope, SearchCheck, Sparkles, Activity } from 'lucide-vue-next'
import { RouterLink } from 'vue-router'
import EnzymeLibraryTable from '@/components/EnzymeLibraryTable.vue'
import ImportTaskCard from '@/components/ImportTaskCard.vue'
import MetricCard from '@/components/MetricCard.vue'
import { useNcbiImport } from '@/composables/useNcbiImport'
import { getDashboardStats } from '@/utils/api'

const { task, enzymes, refreshAll, removeEnzyme } = useNcbiImport()
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
    color: 'bg-blue-500/10 text-blue-500'
  },
  {
    title: '文献自动匹配',
    desc: '基于多维打分模型，自动关联 PubMed 文献，提供结构与功能证据。',
    icon: SearchCheck,
    color: 'bg-apple-green/10 text-apple-green'
  },
  {
    title: '3D 结构工作站',
    desc: '深度集成 Mol* 渲染引擎，支持活性位点高亮与文献联动展示。',
    icon: Dna,
    color: 'bg-purple-500/10 text-purple-500'
  },
  {
    title: '智能预测接口',
    desc: '预留 MiniFold/ESM 等结构预测引擎接口，支持后续算法扩展。',
    icon: Microscope,
    color: 'bg-orange-500/10 text-orange-500'
  }
]
</script>

<template>
  <div class="space-y-10">
    <!-- Hero Section -->
    <section class="relative overflow-hidden rounded-apple-xl bg-white dark:bg-white/5 p-12 border border-apple-border shadow-apple">
      <div class="relative z-10 flex flex-col md:flex-row gap-12 items-center">
        <div class="flex-1 space-y-6">
          <div class="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-apple-blue/10 text-apple-blue text-[10px] font-bold uppercase tracking-widest">
            <Sparkles :size="12" />
            科研管理新视界
          </div>
          <h1 class="text-4xl md:text-5xl font-bold tracking-tight text-apple-text leading-[1.1]">
            酶信息管理与<br />
            <span class="text-apple-blue">智能预测平台</span>
          </h1>
          <p class="text-lg text-apple-secondary-text max-w-xl leading-relaxed">
            FreeWillase 提供优雅、高效的酶学研究工具。通过 NCBI 自动化建库与文献智能关联，将复杂的数据转化为直观的科研洞察。
          </p>
          <div class="flex flex-wrap gap-4 pt-4">
            <RouterLink to="/importer" class="apple-button-primary flex items-center gap-2">
              开始导入 Accession
              <ArrowRight :size="16" />
            </RouterLink>
            <RouterLink to="/library" class="apple-button-secondary">
              浏览酶库中心
            </RouterLink>
          </div>
        </div>
        
        <div class="hidden lg:grid grid-cols-2 gap-4 flex-1 max-w-md">
          <div v-for="feature in features" :key="feature.title" class="p-6 rounded-apple border border-apple-border bg-apple-background dark:bg-black/20 hover:scale-105 transition-transform">
            <div :class="feature.color" class="w-10 h-10 rounded-apple flex items-center justify-center mb-4">
              <component :is="feature.icon" :size="20" />
            </div>
            <h3 class="text-sm font-bold text-apple-text mb-1">{{ feature.title }}</h3>
            <p class="text-[10px] text-apple-secondary-text leading-relaxed">{{ feature.desc }}</p>
          </div>
        </div>
      </div>
      
      <!-- Background Decorative Elements -->
      <div class="absolute -right-20 -top-20 w-80 h-80 bg-apple-blue/5 rounded-full blur-3xl"></div>
      <div class="absolute -left-20 -bottom-20 w-80 h-80 bg-apple-green/5 rounded-full blur-3xl"></div>
    </section>

    <!-- Metrics Grid -->
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
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
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-8">
      <div class="lg:col-span-2 space-y-8">
        <EnzymeLibraryTable :enzymes="enzymes" @delete="removeEnzyme" />
      </div>
      <div class="space-y-8">
        <ImportTaskCard :task="task" />
        
        <!-- Activity Card -->
        <div class="apple-card p-6">
          <div class="flex items-center gap-3 mb-6">
            <div class="w-8 h-8 rounded-apple bg-orange-500/10 text-orange-500 flex items-center justify-center">
              <Activity :size="16" />
            </div>
            <h2 class="text-sm font-bold text-apple-text">最近动态</h2>
          </div>
          <div class="space-y-6">
            <div v-for="i in 3" :key="i" class="relative pl-6 pb-6 border-l border-apple-border last:border-0 last:pb-0">
              <div class="absolute left-[-5px] top-1 w-2 h-2 rounded-full bg-apple-blue"></div>
              <p class="text-[10px] text-apple-secondary-text font-bold uppercase mb-1">2026-07-20 15:30</p>
              <h4 class="text-xs font-bold text-apple-text mb-1">批量导入任务完成</h4>
              <p class="text-xs text-apple-secondary-text">成功导入 12 个 Accession，包含 WP_012345678.1 等。</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
