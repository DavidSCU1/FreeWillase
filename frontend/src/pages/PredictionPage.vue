<script setup lang="ts">
import { ref } from 'vue'
import { 
  Microscope, 
  Settings, 
  Play, 
  Activity, 
  Dna, 
  Cpu, 
  Layers, 
  ChevronRight,
  Info,
  Clock,
  Sparkles
} from 'lucide-vue-next'
import { predictionModules } from '@/data/mock'

const selectedEngine = ref('MiniFold-v1')
const isPredicting = ref(false)

const startPrediction = () => {
  isPredicting.value = true
  setTimeout(() => {
    isPredicting.value = false
  }, 3000)
}
</script>

<template>
  <div class="space-y-8 pb-20">
    <!-- Header -->
    <div class="flex flex-col md:flex-row md:items-center justify-between gap-4">
      <div class="space-y-1">
        <h1 class="text-3xl font-bold tracking-tight text-apple-text">预测接口中心</h1>
        <p class="text-apple-secondary-text text-sm">MiniFold / ESM 结构预测引擎与参数配置</p>
      </div>
      <div class="flex items-center gap-3">
        <div class="flex items-center gap-2 px-3 py-1 rounded-full bg-apple-blue/10 text-apple-blue text-[10px] font-bold uppercase tracking-widest">
          <Activity :size="12" />
          引擎状态: 运行中
        </div>
      </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-8 items-start">
      <!-- Prediction Setup -->
      <div class="lg:col-span-2 space-y-8">
        <div class="apple-card p-8">
          <div class="flex items-center gap-3 mb-8">
            <div class="w-10 h-10 rounded-apple bg-apple-blue/10 text-apple-blue flex items-center justify-center">
              <Settings :size="20" />
            </div>
            <div>
              <h2 class="text-lg font-bold text-apple-text">预测参数配置</h2>
              <p class="text-xs text-apple-secondary-text">配置计算引擎与推理参数</p>
            </div>
          </div>

          <div class="space-y-8">
            <div class="grid grid-cols-1 md:grid-cols-2 gap-8">
              <div class="space-y-3">
                <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest ml-1">选择预测引擎</label>
                <div class="grid grid-cols-1 gap-2">
                  <button 
                    v-for="engine in ['MiniFold-v1', 'ESM-Fold', 'AlphaFold2-Fast']" 
                    :key="engine"
                    @click="selectedEngine = engine"
                    :class="selectedEngine === engine ? 'border-apple-blue bg-apple-blue/5 text-apple-blue' : 'border-apple-border text-apple-secondary-text'"
                    class="w-full px-4 py-3 rounded-apple border text-xs font-bold flex items-center justify-between group transition-all"
                  >
                    {{ engine }}
                    <div :class="selectedEngine === engine ? 'bg-apple-blue' : 'bg-apple-secondary-text/20'" class="w-1.5 h-1.5 rounded-full"></div>
                  </button>
                </div>
              </div>
              <div class="space-y-3">
                <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest ml-1">高级参数 (高级用户)</label>
                <div class="space-y-4 p-4 rounded-apple bg-apple-background dark:bg-white/5 border border-apple-border">
                  <div class="flex items-center justify-between">
                    <span class="text-[10px] text-apple-secondary-text font-bold">Num Recycles</span>
                    <span class="text-xs font-bold text-apple-text">3</span>
                  </div>
                  <div class="flex items-center justify-between">
                    <span class="text-[10px] text-apple-secondary-text font-bold">Use Amber Relax</span>
                    <span class="text-[10px] font-bold text-apple-green uppercase tracking-tighter">Enabled</span>
                  </div>
                  <div class="flex items-center justify-between">
                    <span class="text-[10px] text-apple-secondary-text font-bold">Multiple Chains</span>
                    <span class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-tighter">Auto</span>
                  </div>
                </div>
              </div>
            </div>

            <div class="space-y-3">
              <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest ml-1">输入氨基酸序列</label>
              <textarea 
                rows="6"
                placeholder="请输入单链或多链氨基酸序列 (FASTA 格式)..."
                class="apple-input font-mono text-xs leading-relaxed resize-none"
              ></textarea>
            </div>

            <div class="pt-6 border-t border-apple-border flex items-center justify-between">
              <div class="flex items-center gap-4 text-[10px] text-apple-secondary-text font-bold">
                <span class="flex items-center gap-1"><Cpu :size="12" /> CUDA: Reserved</span>
                <span class="flex items-center gap-1"><Layers :size="12" /> Model: loaded</span>
              </div>
              <button 
                @click="startPrediction"
                :disabled="isPredicting"
                class="apple-button-primary flex items-center gap-2 min-w-[160px] justify-center disabled:opacity-50"
              >
                <template v-if="isPredicting">
                  <Loader2 :size="16" class="animate-spin" />
                  提交任务中...
                </template>
                <template v-else>
                  <Play :size="16" />
                  开始结构预测
                </template>
              </button>
            </div>
          </div>
        </div>

        <!-- Prediction Modules Mapping -->
        <div class="apple-card p-8">
          <div class="flex items-center gap-3 mb-8">
            <div class="w-10 h-10 rounded-apple bg-purple-500/10 text-purple-500 flex items-center justify-center">
              <Dna :size="20" />
            </div>
            <div>
              <h2 class="text-lg font-bold text-apple-text">MiniFold 模块映射</h2>
              <p class="text-xs text-apple-secondary-text">内部算法模块集成状态</p>
            </div>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div 
              v-for="module in predictionModules" 
              :key="module.name"
              class="p-4 rounded-apple border border-apple-border bg-apple-background dark:bg-white/5 hover:border-apple-blue/20 transition-all group"
            >
              <div class="flex items-center justify-between mb-2">
                <span class="text-[10px] font-mono font-bold text-apple-blue uppercase tracking-tighter">{{ module.name }}</span>
                <div class="px-2 py-0.5 rounded-full bg-apple-blue/10 text-apple-blue text-[9px] font-bold uppercase tracking-widest">
                  Ready
                </div>
              </div>
              <p class="text-xs font-bold text-apple-text mb-1">{{ module.purpose }}</p>
              <p class="text-[10px] text-apple-secondary-text leading-relaxed">{{ module.status }}</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Right Sidebar -->
      <div class="space-y-8">
        <!-- Queue Card -->
        <div class="apple-card p-6">
          <div class="flex items-center justify-between mb-6">
            <h3 class="text-sm font-bold text-apple-text">任务队列</h3>
            <span class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest">3 待处理</span>
          </div>
          <div class="space-y-4">
            <div v-for="i in 2" :key="i" class="p-3 rounded-apple bg-apple-background dark:bg-white/5 border border-apple-border flex items-center justify-between">
              <div class="flex items-center gap-3">
                <div class="w-2 h-2 rounded-full bg-apple-blue animate-pulse"></div>
                <div>
                  <p class="text-xs font-bold text-apple-text">Prediction #{{ 1024 + i }}</p>
                  <p class="text-[10px] text-apple-secondary-text">256 aa • MiniFold-v1</p>
                </div>
              </div>
              <span class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-tighter">64%</span>
            </div>
          </div>
        </div>

        <!-- Help Card -->
        <div class="apple-card p-6 bg-gradient-to-br from-purple-500/5 to-transparent border-purple-500/10">
          <div class="flex items-center gap-3 mb-4">
            <div class="w-8 h-8 rounded-apple bg-purple-500/10 text-purple-500 flex items-center justify-center">
              <Sparkles :size="16" />
            </div>
            <h3 class="text-sm font-bold text-apple-text">预测说明</h3>
          </div>
          <p class="text-xs text-apple-secondary-text leading-relaxed">
            结构预测任务将提交至后台高性能计算节点。首期版本仅支持单链预测，多链复合体预测功能将在二期版本中开放。
          </p>
          <div class="mt-4 flex flex-col gap-2">
            <button class="w-full text-left p-3 rounded-apple bg-white/50 dark:bg-black/20 text-[10px] font-bold text-apple-text flex items-center justify-between hover:bg-white/80 transition-all">
              查看预测协议说明
              <ChevronRight :size="12" />
            </button>
            <button class="w-full text-left p-3 rounded-apple bg-white/50 dark:bg-black/20 text-[10px] font-bold text-apple-text flex items-center justify-between hover:bg-white/80 transition-all">
              计算资源分配
              <ChevronRight :size="12" />
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
