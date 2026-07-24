<script setup lang="ts">
import { computed, ref, watch, onUnmounted, nextTick } from 'vue'
import { 
  Settings, 
  Play, 
  Activity, 
  Dna, 
  Cpu, 
  Layers, 
  ChevronRight,
  Sparkles,
  Loader2,
  Microscope,
  Download,
  Terminal,
  Trash2
} from 'lucide-vue-next'
import { predictionModules } from '@/data/mock'
import StructureViewer from '@/components/StructureViewer.vue'
import { usePredictionStore } from '@/stores/prediction'

const store = usePredictionStore()
const consoleOutput = ref<HTMLElement | null>(null)
let logInterval: any = null

const engines = [
  { id: 'minifold', label: 'MiniFold-v1' },
  { id: 'biohub', label: 'Biohub' },
  { id: 'nvidia', label: 'NVIDIA ESMFold' },
  { id: 'chai1', label: 'Chai-1' },
  { id: 'rnafold', label: 'RNAfold' },
] as const

const pendingCount = computed(() => store.tasks.filter(t => t.status === 'running').length)

const inputLabel = computed(() => store.moleculeType === 'ligand' ? '输入 SMILES' : '输入序列')

const inputPlaceholder = computed(() => {
  if (store.moleculeType === 'ligand') return '请输入配体 SMILES...'
  if (store.submitMode === 'batch') return '请输入多条 FASTA 记录，每条以 >名称 开头...'
  if (store.submitMode === 'complex') return '请输入多条 FASTA 记录（每条作为一条链），每条以 >名称 开头...'
  return '请输入单条序列，支持 Plain 或单条 FASTA（会自动忽略 > 标题行）...'
})

const defaultBaseUrlHint = computed(() => {
  if (store.provider === 'biohub') return '默认：https://www.biohub.ai'
  if (store.provider === 'nvidia') return '默认：https://health.api.nvidia.com'
  if (store.provider === 'chai1') return '默认：https://api.biolm.ai'
  if (store.provider === 'rnafold') return '默认：系统后端代理 Vienna RNA RNAfold'
  return ''
})

const canSubmit = computed(() => !store.isSubmitting)
const nvidiaSingleOnly = computed(() => store.provider === 'nvidia')
const rnafoldSingleOnly = computed(() => store.provider === 'rnafold')
const requiresApiKey = computed(() => store.provider !== 'minifold' && store.provider !== 'rnafold')
const rnafoldLockedType = computed(() => store.provider === 'rnafold')

const inputFormatTitle = computed(() => {
  if (store.moleculeType === 'ligand') return '输入格式'
  if (store.submitMode === 'batch') return '多条提交格式'
  if (store.submitMode === 'complex') return '多链复合体格式'
  return '单条提交格式'
})

const inputFormatExample = computed(() => {
  if (store.moleculeType === 'ligand') {
    return 'CC(=O)OC1=CC=CC=C1C(=O)O'
  }

  if (store.submitMode === 'batch') {
    return `>seq1
MKTFFVLLLCTFTVQAAPDAGVTKTYLQDVGGKSTLQKQLAELNQGQKELAAKLEQKQK
>seq2
GSHMSTNPKPQRITFVKDAGQKALDNLVQKQGQKLEAELQKQKVGDKTLEEALNQK`
  }

  if (store.submitMode === 'complex') {
    return `>chainA
MKTFFVLLLCTFTVQAAPDAGVTKTYLQDVGGKSTLQKQLAELNQGQKELAAKLEQKQK
>chainB
GSHMSTNPKPQRITFVKDAGQKALDNLVQKQGQKLEAELQKQKVGDKTLEEALNQK`
  }

  if (store.moleculeType === 'RNA') {
    return `>sample_rna
GGGAAAUCC`
  }

  if (store.moleculeType === 'DNA') {
    return `>sample_dna
ACGTACGTACGT`
  }

  return `>sample_1
MKTFFVLLLCTFTVQAAPDAGVTKTYLQDVGGKSTLQKQLAELNQGQKELAAKLEQKQK`
})

function downloadStructure() {
  const text = store.lastStructureText
  if (!text) return
  const ext = store.viewerFormat === 'mmcif' ? 'cif' : 'pdb'
  const blob = new Blob([text], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${store.name || 'prediction'}.${ext}`
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

function scrollToBottom() {
  nextTick(() => {
    if (consoleOutput.value) {
      consoleOutput.value.scrollTop = consoleOutput.value.scrollHeight
    }
  })
}

async function handleSubmit() {
  await store.submit()
  const active = store.activeTask
  if (active && active.engineTaskId && active.status === 'running') {
    startPollingLogs(active.engineTaskId, active.id)
    startPollingResult(active)
  }
}

function startPollingResult(task: any) {
  const timer = setInterval(async () => {
    const finished = await store.fetchTaskResult(task)
    if (finished) {
      clearInterval(timer)
      if (task.id === store.activeTask?.id) {
        stopPollingLogs()
      }
    }
  }, 3000)
}

function startPollingLogs(engineTaskId: string, frontendId: string) {
  stopPollingLogs()
  logInterval = setInterval(() => {
    store.fetchTaskLogs(engineTaskId, frontendId)
  }, 2000)
}

function stopPollingLogs() {
  if (logInterval) {
    clearInterval(logInterval)
    logInterval = null
  }
}

function selectTask(id: string) {
  store.selectTask(id)
  const task = store.activeTask
  if (task && task.status === 'running' && task.engineTaskId) {
    startPollingLogs(task.engineTaskId, task.id)
  } else {
    stopPollingLogs()
  }
}

watch(() => store.taskLogs[store.activeTask?.id || ''], () => {
  scrollToBottom()
})

watch(() => store.activeTask?.id, (newId) => {
  if (newId && store.activeTask?.status === 'running') {
    // If the task is already running when selected, and we don't have its engine taskId yet, 
    // it's tricky because the engine taskId is only returned after success in blocking mode.
  }
})

onUnmounted(() => {
  stopPollingLogs()
})
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
      <!-- Right Column: Queue & Info (First on Mobile) -->
      <div class="lg:col-start-3 space-y-8">
        <!-- Queue Card -->
        <div class="apple-card p-6">
          <div class="flex items-center justify-between mb-6">
            <h3 class="text-sm font-bold text-apple-text">任务队列</h3>
            <div class="flex items-center gap-2">
              <span class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest">{{ pendingCount }} 运行中</span>
              <button 
                v-if="store.tasks.length > 0"
                @click="store.tasks = []" 
                class="p-1 hover:bg-apple-background rounded text-apple-secondary-text hover:text-red-500 transition-colors"
                title="清空历史"
              >
                <Trash2 :size="12" />
              </button>
            </div>
          </div>
          <div class="space-y-4">
            <button
              v-for="task in store.tasks.slice(0, 15)"
              :key="task.id"
              type="button"
              class="p-3 rounded-apple bg-apple-background dark:bg-white/5 border border-apple-border flex items-center justify-between w-full text-left hover:border-apple-blue/20 transition-all"
              :class="store.activeTaskId === task.id ? 'border-apple-blue/40' : ''"
              @click="selectTask(task.id)"
            >
              <div class="flex items-center gap-3">
                <div
                  class="w-2 h-2 rounded-full"
                  :class="task.status === 'running' ? 'bg-apple-blue animate-pulse' : task.status === 'success' ? 'bg-apple-green' : 'bg-red-500'"
                ></div>
                <div>
                  <p class="text-xs font-bold text-apple-text">{{ task.name }}</p>
                  <p class="text-[10px] text-apple-secondary-text">
                    {{ task.sequenceLength != null ? `${task.sequenceLength} aa` : task.moleculeType }} • {{ task.provider }}
                  </p>
                </div>
              </div>
              <span class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-tighter">
                {{ task.status === 'running' ? 'Running' : task.status === 'success' ? 'Done' : 'Error' }}
              </span>
            </button>

            <div v-if="store.tasks.length === 0" class="text-[10px] text-apple-secondary-text font-bold text-center py-8">
              暂无任务，请先提交一次预测
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
            本板块集成了多种结构预测引擎。MiniFold 采用本地异步引擎，支持实时日志监控与 AI 投票辅助；Biohub/NVIDIA/Chai-1 则通过 API 进行同步调用。
          </p>
        </div>
      </div>

      <!-- Left Column: Setup & Results -->
      <div class="lg:col-span-2 lg:col-start-1 lg:row-start-1 space-y-8">
        
        <!-- Setup Card -->
        <div class="apple-card p-6">
          <div class="flex items-center gap-3 mb-6">
            <div class="w-8 h-8 rounded-apple bg-apple-blue/10 text-apple-blue flex items-center justify-center">
              <Settings :size="16" />
            </div>
            <h3 class="text-sm font-bold text-apple-text">引擎与参数配置</h3>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
            <!-- Engine Selection -->
            <div class="space-y-2">
              <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest ml-1">预测引擎 (Provider)</label>
              <div class="grid grid-cols-2 gap-2">
                <button 
                  v-for="engine in engines" 
                  :key="engine.id"
                  @click="store.provider = engine.id"
                  class="px-3 py-2 rounded-apple border text-xs font-medium transition-all text-center"
                  :class="store.provider === engine.id 
                    ? 'bg-apple-blue border-apple-blue text-white shadow-sm' 
                    : 'bg-apple-background border-apple-border text-apple-text hover:border-apple-blue/30'"
                >
                  {{ engine.label }}
                </button>
              </div>
            </div>

            <!-- Model Selection -->
            <div class="space-y-2">
              <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest ml-1">模型版本 (Model)</label>
              <select 
                v-model="store.model"
                class="apple-input text-xs"
                :disabled="store.supportedModels.length === 0"
              >
                <option v-if="store.supportedModels.length === 0" value="">当前引擎无可选模型</option>
                <option v-for="m in store.supportedModels" :key="m" :value="m">{{ m }}</option>
              </select>
            </div>

            <!-- Molecule Type -->
            <div class="space-y-2">
              <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest ml-1">分子类型</label>
              <div class="flex flex-wrap gap-2">
                <button 
                  v-for="t in store.supportedTypes" 
                  :key="t"
                  @click="store.moleculeType = t"
                  class="px-3 py-1.5 rounded-full border text-[10px] font-bold uppercase tracking-wider transition-all"
                  :class="store.moleculeType === t 
                    ? 'bg-apple-text border-apple-text text-white' 
                    : 'bg-transparent border-apple-border text-apple-secondary-text hover:border-apple-text/30'"
                  :disabled="rnafoldLockedType && t !== 'RNA'"
                >
                  {{ t }}
                </button>
              </div>
            </div>

            <!-- Submit Mode -->
            <div class="space-y-2">
              <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest ml-1">提交模式</label>
              <div class="flex gap-2">
                <button 
                  @click="store.submitMode = 'single'"
                  class="flex-1 px-3 py-1.5 rounded-apple border text-[10px] font-bold uppercase tracking-wider transition-all"
                  :class="store.submitMode === 'single' ? 'bg-apple-text border-apple-text text-white' : 'bg-transparent border-apple-border text-apple-secondary-text hover:border-apple-text/30'"
                >
                  单条 (Single)
                </button>
                <button 
                  @click="store.submitMode = 'batch'"
                  class="flex-1 px-3 py-1.5 rounded-apple border text-[10px] font-bold uppercase tracking-wider transition-all"
                  :class="store.submitMode === 'batch' ? 'bg-apple-text border-apple-text text-white' : 'bg-transparent border-apple-border text-apple-secondary-text hover:border-apple-text/30'"
                  :disabled="nvidiaSingleOnly || rnafoldSingleOnly || store.moleculeType === 'ligand'"
                >
                  批量 (Batch)
                </button>
                <button 
                  @click="store.submitMode = 'complex'"
                  class="flex-1 px-3 py-1.5 rounded-apple border text-[10px] font-bold uppercase tracking-wider transition-all"
                  :class="store.submitMode === 'complex' ? 'bg-apple-text border-apple-text text-white' : 'bg-transparent border-apple-border text-apple-secondary-text hover:border-apple-text/30'"
                  :disabled="store.provider !== 'chai1' || store.moleculeType === 'ligand'"
                >
                  复合体 (Complex)
                </button>
              </div>
            </div>
          </div>

          <!-- MiniFold Specific Parameters -->
          <div v-if="store.provider === 'minifold'" class="mt-6 pt-6 border-t border-apple-border grid grid-cols-1 md:grid-cols-2 gap-6">
            <div class="space-y-2">
              <div class="flex justify-between items-center">
                <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest ml-1">SSN 深度 (SSN Depth)</label>
                <span class="text-[10px] font-mono font-bold text-apple-blue">{{ store.minifoldSsn }}</span>
              </div>
              <input 
                type="range" 
                v-model.number="store.minifoldSsn" 
                min="1" 
                max="20" 
                step="1"
                class="w-full h-1.5 bg-apple-background rounded-lg appearance-none cursor-pointer accent-apple-blue"
              >
              <p class="text-[9px] text-apple-secondary-text italic">控制物理折叠中的结构采样多样性</p>
            </div>
            <div class="space-y-2">
              <div class="flex justify-between items-center">
                <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest ml-1">AI 投票阈值 (Threshold)</label>
                <span class="text-[10px] font-mono font-bold text-apple-blue">{{ store.minifoldThreshold.toFixed(2) }}</span>
              </div>
              <input 
                type="range" 
                v-model.number="store.minifoldThreshold" 
                min="0.1" 
                max="0.9" 
                step="0.05"
                class="w-full h-1.5 bg-apple-background rounded-lg appearance-none cursor-pointer accent-apple-blue"
              >
              <p class="text-[9px] text-apple-secondary-text italic">Ark 大模型筛选结构的置信度下限</p>
            </div>
          </div>

          <!-- API Key & Base URL (Conditionally shown) -->
          <div v-if="requiresApiKey" class="mt-6 pt-6 border-t border-apple-border space-y-4">
            <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div class="space-y-2">
                <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest ml-1">API Key / Token</label>
                <input 
                  v-model="store.apiKey"
                  type="password"
                  class="apple-input text-xs"
                  placeholder="请输入您的 API 密钥..."
                >
              </div>
              <div class="space-y-2">
                <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest ml-1">Base URL (可选)</label>
                <input 
                  v-model="store.baseUrl"
                  type="text"
                  class="apple-input text-xs"
                  :placeholder="defaultBaseUrlHint"
                >
              </div>
            </div>
            <div class="flex items-center gap-2">
              <input type="checkbox" v-model="store.rememberApiKey" id="rememberKey" class="rounded border-apple-border text-apple-blue focus:ring-apple-blue/20">
              <label for="rememberKey" class="text-[10px] font-bold text-apple-secondary-text">记住密钥 (保存在本地浏览器)</label>
            </div>
          </div>
        </div>

        <!-- Input Card -->
        <div class="apple-card p-6">
          <div class="flex items-center justify-between mb-6">
            <div class="flex items-center gap-3">
              <div class="w-8 h-8 rounded-apple bg-apple-green/10 text-apple-green flex items-center justify-center">
                <Dna :size="16" />
              </div>
              <h3 class="text-sm font-bold text-apple-text">{{ inputLabel }}</h3>
            </div>
            <div class="text-[10px] font-bold text-apple-secondary-text bg-apple-background px-2 py-1 rounded">
              {{ inputFormatTitle }}
            </div>
          </div>

          <div class="space-y-4">
            <div class="relative group">
              <textarea 
                v-model="store.sequence"
                class="apple-input min-h-[160px] text-xs font-mono leading-relaxed p-4 bg-apple-background/50 focus:bg-white dark:focus:bg-white/5"
                :placeholder="inputPlaceholder"
              ></textarea>
              <div class="absolute bottom-3 right-3 opacity-0 group-hover:opacity-100 transition-opacity">
                <button 
                  @click="store.sequence = inputFormatExample"
                  class="text-[9px] font-bold text-apple-blue bg-white dark:bg-apple-background border border-apple-border px-2 py-1 rounded shadow-sm hover:border-apple-blue transition-colors"
                >
                  填入示例
                </button>
              </div>
            </div>

            <div class="flex items-center gap-4 pt-2">
              <div class="flex-1">
                <input 
                  v-model="store.name"
                  type="text"
                  class="apple-input text-xs"
                  placeholder="任务名称 (可选)..."
                >
              </div>
              <button 
                @click="handleSubmit"
                :disabled="!canSubmit || !store.sequence"
                class="apple-button-primary px-8 py-2.5 flex items-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <template v-if="store.isSubmitting">
                  <Loader2 class="animate-spin" :size="16" />
                  <span>处理中...</span>
                </template>
                <template v-else>
                  <Play :size="16" />
                  <span>开始预测</span>
                </template>
              </button>
            </div>
            
            <p v-if="store.error" class="text-[10px] font-bold text-red-500 mt-2 flex items-center gap-1">
              <Activity :size="12" />
              错误: {{ store.error }}
            </p>
          </div>
        </div>

        <!-- Result Card -->
        <div v-if="store.activeTask" class="apple-card overflow-hidden">
          <!-- Success State -->
          <template v-if="store.activeTask.status === 'success' && store.activeTask.result">
            <div class="p-6 border-b border-apple-border flex items-center justify-between bg-apple-background/30">
              <div class="flex items-center gap-3">
                <div class="w-8 h-8 rounded-apple bg-apple-blue/10 text-apple-blue flex items-center justify-center">
                  <Microscope :size="16" />
                </div>
                <div>
                  <h3 class="text-sm font-bold text-apple-text">{{ store.activeTask.name }} - 预测结果</h3>
                  <p class="text-[10px] text-apple-secondary-text uppercase tracking-widest font-bold">
                    {{ store.activeTask.provider }} • {{ store.activeTask.result.modelName }} • {{ store.activeTask.result.format }}
                  </p>
                </div>
              </div>
              <button 
                @click="downloadStructure"
                class="flex items-center gap-2 px-4 py-2 rounded-apple bg-apple-blue text-white text-[10px] font-bold uppercase tracking-widest hover:bg-apple-blue/90 transition-all shadow-sm"
              >
                <Download :size="14" />
                下载结构
              </button>
            </div>
            
            <div class="grid grid-cols-1 md:grid-cols-4 min-h-[500px]">
              <!-- Structure Viewer (3/4 width) -->
              <div class="md:col-span-3 bg-black/5 dark:bg-white/5 relative border-r border-apple-border">
                <StructureViewer 
                  v-if="store.viewerUrl || store.viewerFormat === 'dot-bracket'"
                  :structure-url="store.viewerUrl" 
                  :format="store.viewerFormat"
                  :data="store.lastStructureText"
                  class="w-full h-full"
                />
                <div v-else class="flex flex-col items-center justify-center h-full text-apple-secondary-text gap-4">
                  <Loader2 class="animate-spin text-apple-blue" :size="32" />
                  <p class="text-xs font-medium">正在初始化查看器...</p>
                </div>
              </div>

              <!-- Analysis/Metrics (1/4 width) -->
              <div class="p-6 space-y-6 bg-apple-background/10">
                <div v-if="store.activeTask.result.plddt != null" class="space-y-2">
                  <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest">Confidence (pLDDT)</label>
                  <div class="flex items-end gap-2">
                    <span class="text-3xl font-bold text-apple-text tracking-tighter">{{ store.activeTask.result.plddt.toFixed(1) }}</span>
                    <span class="text-[10px] font-bold text-apple-secondary-text mb-1.5">%</span>
                  </div>
                  <div class="w-full h-1 bg-apple-border rounded-full overflow-hidden">
                    <div 
                      class="h-full bg-apple-blue transition-all duration-1000"
                      :style="{ width: `${store.activeTask.result.plddt}%` }"
                    ></div>
                  </div>
                </div>

                <div v-if="store.activeTask.result.ptm != null" class="space-y-2">
                  <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest">Global Score (pTM)</label>
                  <div class="text-xl font-bold text-apple-text">{{ store.activeTask.result.ptm.toFixed(3) }}</div>
                </div>

                <div v-if="store.activeTask.result.analysis" class="space-y-2">
                  <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest">AI 投票分析</label>
                  <div class="text-[11px] text-apple-secondary-text leading-relaxed bg-white/50 dark:bg-white/5 p-3 rounded-apple border border-apple-border italic">
                    "{{ store.activeTask.result.analysis }}"
                  </div>
                </div>

                <div v-if="store.activeTask.result.mfeEnergy != null" class="space-y-2">
                  <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest">MFE Energy</label>
                  <div class="text-xl font-bold text-apple-text">{{ store.activeTask.result.mfeEnergy.toFixed(2) }} <span class="text-[10px]">kcal/mol</span></div>
                </div>
              </div>
            </div>
          </template>

          <!-- Running/Console State -->
          <template v-else-if="store.activeTask.status === 'running'">
            <div class="p-6 border-b border-apple-border flex items-center justify-between bg-black text-white">
              <div class="flex items-center gap-3">
                <div class="w-8 h-8 rounded-apple bg-white/10 text-white flex items-center justify-center">
                  <Terminal :size="16" />
                </div>
                <div>
                  <h3 class="text-sm font-bold">实时计算控制台</h3>
                  <p class="text-[10px] text-white/50 uppercase tracking-widest font-bold">
                    任务 ID: {{ store.activeTask.engineTaskId || '分配中...' }}
                  </p>
                </div>
              </div>
              <div class="flex items-center gap-2">
                <span class="w-2 h-2 bg-apple-blue rounded-full animate-ping"></span>
                <span class="text-[10px] font-bold uppercase tracking-widest">物理折叠计算中...</span>
              </div>
            </div>

            <div 
              ref="consoleOutput"
              class="bg-black p-6 h-[400px] overflow-y-auto font-mono text-[11px] leading-relaxed scrollbar-thin scrollbar-thumb-white/20"
            >
              <div v-if="store.taskLogs[store.activeTask.id]" class="space-y-1">
                <div 
                  v-for="(line, idx) in store.taskLogs[store.activeTask.id].split('\n')" 
                  :key="idx"
                  class="flex gap-4"
                  :class="line.includes('ERROR') ? 'text-red-400' : line.includes('SUCCESS') ? 'text-green-400' : 'text-gray-300'"
                >
                  <span class="text-gray-600 shrink-0 w-4">{{ idx + 1 }}</span>
                  <span class="break-all">{{ line }}</span>
                </div>
              </div>
              <div v-else class="flex items-center justify-center h-full text-white/30 italic">
                正在连接到预测引擎，请稍候...
              </div>
            </div>
          </template>

          <!-- Error State -->
          <template v-else-if="store.activeTask.status === 'error'">
            <div class="p-12 flex flex-col items-center justify-center text-center gap-4">
              <div class="w-16 h-16 rounded-full bg-red-500/10 text-red-500 flex items-center justify-center">
                <Activity :size="32" />
              </div>
              <div class="space-y-2">
                <h3 class="text-lg font-bold text-apple-text">预测失败</h3>
                <p class="text-sm text-apple-secondary-text max-w-md mx-auto">
                  {{ store.activeTask.error || '发生了未知错误，请检查网络连接或引擎状态。' }}
                </p>
              </div>
              <button 
                @click="handleSubmit"
                class="mt-4 px-6 py-2 rounded-apple border border-apple-border text-xs font-bold hover:bg-apple-background transition-all"
              >
                重试任务
              </button>
            </div>
          </template>
        </div>

      </div>
    </div>
  </div>
</template>
