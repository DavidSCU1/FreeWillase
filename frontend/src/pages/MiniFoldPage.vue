<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  Activity,
  AlertCircle,
  ArrowLeft,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Cpu,
  Download,
  Dna,
  FileText,
  FolderPlus,
  Layers3,
  ListTree,
  Loader2,
  Maximize2,
  Microscope,
  Play,
  Sparkles,
} from 'lucide-vue-next'
import StructureViewer from '@/components/StructureViewer.vue'
import { useMiniFoldStore } from '@/stores/minifold'
import type { MiniFoldBackend } from '@/stores/minifold'
import { normalizeSequenceInput } from '@/utils/predictionProviders'
import { getMiniFoldLogs, saveMiniFoldEnzyme } from '@/utils/api'
import type { EnzymeEntry } from '@/types'

const router = useRouter()
const store = useMiniFoldStore()
const showFullscreenViewer = ref(false)
const libraryEntryName = ref('')
const isSavingToLibrary = ref(false)
const saveToLibraryError = ref('')
const savedLibraryEntry = ref<EnzymeEntry | null>(null)
const runtimeLog = ref('')
const lastLogUpdatedAt = ref<number | null>(null)
const consoleViewport = ref<HTMLElement | null>(null)
const autoScrollLogs = ref(true)
const nowTick = ref(Date.now())

let resultInterval: ReturnType<typeof setInterval> | null = null
let logInterval: ReturnType<typeof setInterval> | null = null
let clockInterval: ReturnType<typeof setInterval> | null = null

const taskStageBlueprint = [
  { label: '提交任务', hint: '校验输入并向后端申请任务号' },
  { label: '启动运行时', hint: '拉起 Python worker 与本地环境' },
  { label: '解析输入', hint: '读取 FASTA、环境描述与链数约束' },
  { label: '结构推理', hint: '生成候选、骨架与多链结构' },
  { label: '输出结果', hint: '整理日志、写出 PDB 与结果文件' },
] as const

const targetChainOptions = [
  { label: '自动判断', value: '' },
  { label: '1 条链', value: '1' },
  { label: '2 条链', value: '2' },
  { label: '3 条链', value: '3' },
  { label: '4 条链', value: '4' },
]

const backendOptions = [
  { label: '自动选择', value: 'auto', hint: '推荐。由运行时优先尝试最合适的本机推理后端。' },
  { label: 'DirectML', value: 'directml', hint: '优先调用 Windows 图形设备，适合核显或通用 GPU。' },
  { label: 'IPEX', value: 'ipex', hint: '偏向 Intel XPU / Arc 路径。' },
  { label: 'oneAPI CPU', value: 'oneapi_cpu', hint: '走 Intel CPU 优化路径，不依赖图形设备。' },
  { label: 'CUDA', value: 'cuda', hint: '优先调用 NVIDIA 独显。' },
  { label: 'CPU', value: 'cpu', hint: '最稳妥，但速度最慢。' },
] as const

const selectedStructureId = computed(() => store.engineTaskId || '尚未创建')
const selectedStructureStatus = computed(() => {
  if (store.status === 'success') return '本地结构已生成'
  if (store.status === 'running') return '结构计算中'
  if (store.status === 'error') return '结构生成失败'
  return '等待开始'
})
const selectedInferenceMode = computed(() => {
  if (!store.useAcceleration || store.backend === 'cpu') return 'cpu'
  return store.backend
})
const inferenceModeLabel = computed(() => backendOptions.find(item => item.value === selectedInferenceMode.value)?.label || selectedInferenceMode.value)
const inferenceModeHint = computed(() => backendOptions.find(item => item.value === selectedInferenceMode.value)?.hint || '')
const targetChainLabel = computed(() => store.targetChains ? `${store.targetChains} 条链` : '自动判断')
const condaEnvLabel = computed(() => store.condaEnvName.trim() || '自动发现 / MINIFOLD_PYTHON')
const isRnaMode = computed(() => store.moleculeType === 'RNA')
const sequenceUnitLabel = computed(() => isRnaMode.value ? 'nt' : 'aa')
const sequenceKindLabel = computed(() => isRnaMode.value ? 'RNA 序列' : '蛋白序列')
const normalizedSequenceLength = computed(() => {
  return store.sequence
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(line => line && !line.startsWith('>'))
    .join('')
    .replace(/\s+/g, '')
    .length
})
const envLength = computed(() => store.envText.trim().length)
const summaryItems = computed(() => [
  {
    label: '序列长度',
    value: normalizedSequenceLength.value ? `${normalizedSequenceLength.value} ${sequenceUnitLabel.value}` : '未填写',
  },
  {
    label: '环境约束',
    value: envLength.value ? `已提供 (${envLength.value} 字)` : '未提供',
  },
  {
    label: '预测链数',
    value: targetChainLabel.value,
  },
  {
    label: '计算后端',
    value: inferenceModeLabel.value,
  },
  {
    label: '执行环境',
    value: condaEnvLabel.value,
  },
])
const readinessItems = computed(() => [
  {
    label: '输入序列',
    done: normalizedSequenceLength.value > 0,
    hint: normalizedSequenceLength.value > 0 ? `${normalizedSequenceLength.value} ${sequenceUnitLabel.value}` : '必填',
  },
  {
    label: '环境描述',
    done: envLength.value > 0,
    hint: envLength.value > 0 ? '已补充场景约束' : '选填',
  },
  {
    label: '执行配置',
    done: true,
    hint: `${targetChainLabel.value} · ${inferenceModeLabel.value} · ${store.condaEnvName.trim() || '自动环境'}`,
  },
])
const runtimeLogLines = computed(() => runtimeLog.value.split(/\r?\n/).filter(Boolean))
const latestLogLines = computed(() => runtimeLogLines.value.slice(-6))
const activeStageIndex = computed(() => {
  if (store.status === 'idle') return -1
  if (!store.engineTaskId) return store.status === 'error' ? 0 : 0

  const log = runtimeLog.value
  if (store.status === 'success') return taskStageBlueprint.length - 1
  if (store.status === 'error' && !log.trim()) return 1
  if (log.includes('Pipeline finished') || log.includes('Runtime Finished')) return 4
  if (log.includes('候选生成完成') || log.includes('Structure') || log.includes('3d_structures')) return 3
  if (log.includes('处理序列') || log.includes('读取 FASTA') || log.includes('Target chains')) return 2
  if (log.includes('Runtime Started') || log.includes('Loaded environment file')) return 1
  return 0
})
const stageProgress = computed(() => {
  if (store.status === 'idle') return 0
  if (store.status === 'success') return 100
  if (store.status === 'error' && activeStageIndex.value < 0) return 8
  const total = taskStageBlueprint.length
  const base = ((Math.max(activeStageIndex.value, 0) + 1) / total) * 100
  if (store.status === 'running') {
    return Math.min(base + 8, 92)
  }
  return Math.max(base, 12)
})
const stageItems = computed(() => taskStageBlueprint.map((item, index) => ({
  ...item,
  state: store.status === 'success'
    ? 'done'
    : store.status === 'error' && index === Math.max(activeStageIndex.value, 0)
      ? 'error'
      : index < activeStageIndex.value
        ? 'done'
        : index === activeStageIndex.value
          ? 'active'
          : 'pending',
})))
const currentStageLabel = computed(() => {
  if (store.status === 'idle') return `0 / ${taskStageBlueprint.length}`
  if (store.status === 'success') return `${taskStageBlueprint.length} / ${taskStageBlueprint.length}`
  return `${Math.min(Math.max(activeStageIndex.value + 1, 1), taskStageBlueprint.length)} / ${taskStageBlueprint.length}`
})
const runtimeHeadline = computed(() => {
  if (store.status === 'success') return '推理完成'
  if (store.status === 'error') return store.engineTaskId ? '任务中断' : '未入队'
  if (store.status === 'running') return '正在同步日志'
  return '等待启动'
})
const runtimeSignalLabel = computed(() => {
  if (store.status === 'success') return '结构已稳定输出'
  if (store.status === 'error') return store.engineTaskId ? '信号中断' : '待命'
  if (store.status === 'running') return '引擎持续输出中'
  return '待机'
})
const runtimeSignalTone = computed(() => {
  if (store.status === 'success') return '结果已固化'
  if (store.status === 'error') return '信号中断'
  if (store.status === 'running') return '信号在线'
  return '待命'
})
const runtimeSignalCode = computed(() => {
  if (store.status === 'success') return 'DONE'
  if (store.status === 'error') return 'LOST'
  if (store.status === 'running') return 'LIVE'
  return 'IDLE'
})
const runtimeMonitorStateClass = computed(() => {
  if (store.status === 'success') return 'is-success'
  if (store.status === 'error') return 'is-error'
  if (store.status === 'running') return 'is-running'
  return 'is-idle'
})
const elapsedLabel = computed(() => {
  if (!store.taskStartedAt) return '00:00'
  const seconds = Math.max(0, Math.floor((nowTick.value - store.taskStartedAt) / 1000))
  const minutes = Math.floor(seconds / 60)
  const remain = seconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(remain).padStart(2, '0')}`
})
const logTimestampLabel = computed(() => {
  if (!lastLogUpdatedAt.value) return '尚未刷新'
  const date = new Date(lastLogUpdatedAt.value)
  return date.toLocaleTimeString('zh-CN', { hour12: false })
})
const statusMeta = computed(() => {
  if (store.status === 'success') {
    return {
      title: '预测成功',
      description: '结构已生成。',
      chip: '已完成',
      chipClass: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-300',
    }
  }
  if (store.status === 'running') {
    return {
      title: '运行中',
      description: '正在推理。',
      chip: '运行中',
      chipClass: 'bg-apple-blue/10 text-apple-blue',
    }
  }
  if (store.status === 'error') {
    return {
      title: '执行失败',
      description: store.error || '任务中断。',
      chip: '异常',
      chipClass: 'bg-red-500/10 text-red-500',
    }
  }
  return {
    title: '准备推理',
    description: '确认配置后启动。',
    chip: '待提交',
    chipClass: 'bg-apple-background text-apple-secondary-text',
  }
})
const qualityAssessment = computed(() => store.qualityAssessment)
const resultMetrics = computed(() => store.resultMetrics)
const qualityDimensions = computed(() => qualityAssessment.value?.dimensions || [])
const qualityWarnings = computed(() => qualityAssessment.value?.warnings || [])
const qualityScoreLabel = computed(() => {
  const score = qualityAssessment.value?.overallScore
  return typeof score === 'number' ? String(score) : '--'
})
const qualityLevelClass = computed(() => {
  const score = qualityAssessment.value?.overallScore ?? 0
  if (score >= 85) return 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-300'
  if (score >= 72) return 'bg-apple-blue/10 text-apple-blue'
  if (score >= 58) return 'bg-amber-400/10 text-amber-300'
  return 'bg-red-500/10 text-red-500'
})
const rnaMetricCards = computed(() => {
  if (!resultMetrics.value?.scores) return []
  const labelMap: Record<string, string> = {
    backbone: '主链',
    pairing: '配对',
    clash: '碰撞',
    smoothness: '平滑',
    stacking: '堆叠',
    compactness: '紧凑',
    pairSupport: '支持度',
  }
  return Object.entries(resultMetrics.value.scores)
    .map(([key, value]) => ({
      key,
      label: labelMap[key] || key,
      score: typeof value === 'number' ? value : 0,
    }))
    .sort((a, b) => b.score - a.score)
})
const resultHeadline = computed(() => {
  if (qualityAssessment.value) return '质量评分'
  if (resultMetrics.value) return 'RNA 合理性评分'
  return '执行概况'
})
const suggestedLibraryName = computed(() => {
  const header = store.sequence
    .split(/\r?\n/)
    .map(line => line.trim())
    .find(line => line.startsWith('>'))
    ?.replace(/^>/, '')
    .trim()

  return header || `MiniFold 预测 ${selectedStructureId.value}`
})

function getQualityTrackClass(score: number) {
  if (score >= 85) return 'bg-emerald-500'
  if (score >= 72) return 'bg-apple-blue'
  if (score >= 58) return 'bg-amber-400'
  return 'bg-red-500'
}

function formatMetricNumber(value: number | null | undefined, digits = 2) {
  return typeof value === 'number' ? value.toFixed(digits) : '--'
}

function fillExample() {
  store.sequence = store.moleculeType === 'protein' ? `>freewillase_enzyme_candidate
MKTFFVLLLCTFTVQAAPDAGVTKTYLQDVGGKSTLQKQLAELNQGQKELAAKLEQKQK` : `>freewillase_rna_candidate
GGGCUAUUAGCUCAGUUGGUUAGAGCGCACCCCUGAUAAGGGUGAGGUCGCUGAUUCGAAUUCAGCAUAGCCCA`;
  if (!store.envText.trim()) {
    store.envText = store.moleculeType === 'protein' 
      ? '线粒体相关酶，倾向形成稳定紧凑构象，尽量避免疏水核心过度暴露。'
      : '包含 Mg2+，倾向于形成经典的 A-form 茎区和稳定的假结或三通结构。';
  }
}

function stopPolling() {
  if (resultInterval) {
    clearInterval(resultInterval)
    resultInterval = null
  }
  if (logInterval) {
    clearInterval(logInterval)
    logInterval = null
  }
}

function syncClock() {
  nowTick.value = Date.now()
}

function ensureClock() {
  if (clockInterval) return
  clockInterval = setInterval(syncClock, 1000)
}

function stopClock() {
  if (!clockInterval) return
  clearInterval(clockInterval)
  clockInterval = null
}

async function refreshLogs() {
  if (!store.engineTaskId) return
  try {
    const logs = await getMiniFoldLogs(store.engineTaskId)
    runtimeLog.value = typeof logs === 'string' ? logs : JSON.stringify(logs, null, 2)
    lastLogUpdatedAt.value = Date.now()
    if (autoScrollLogs.value) {
      await nextTick()
      if (consoleViewport.value) {
        consoleViewport.value.scrollTop = consoleViewport.value.scrollHeight
      }
    }
  } catch (error) {
    runtimeLog.value = `日志读取失败：${error instanceof Error ? error.message : '未知错误'}`
    lastLogUpdatedAt.value = Date.now()
  }
}

function startLogPolling() {
  if (!store.engineTaskId) return
  refreshLogs()
  if (logInterval) {
    clearInterval(logInterval)
  }
  logInterval = setInterval(refreshLogs, 2000)
}

function handleLogScroll(event: Event) {
  const target = event.target as HTMLElement
  const distanceToBottom = target.scrollHeight - target.scrollTop - target.clientHeight
  autoScrollLogs.value = distanceToBottom < 32
}

function startPolling() {
  if (!store.engineTaskId) return
  stopPolling()
  startLogPolling()
  resultInterval = setInterval(async () => {
    const finished = await store.fetchResult()
    if (finished) stopPolling()
  }, 3000)
}

async function handleSubmit() {
  saveToLibraryError.value = ''
  savedLibraryEntry.value = null
  runtimeLog.value = ''
  lastLogUpdatedAt.value = null
  autoScrollLogs.value = true
  ensureClock()
  const started = await store.submit()
  if (started && store.engineTaskId && store.status === 'running') {
    startPolling()
  } else if (!started && store.status !== 'running') {
    stopClock()
  }
}

function downloadStructure() {
  const text = store.lastStructureText
  if (!text) return
  const blob = new Blob([text], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = 'minifold-result.pdb'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

function updateTargetChains(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  store.targetChains = value ? Number(value) : null
}

function selectInferenceMode(mode: string) {
  if (mode === 'cpu') {
    store.useAcceleration = false
    store.backend = 'cpu'
    return
  }
  store.useAcceleration = true
  store.backend = mode as MiniFoldBackend
}

async function handleSaveToLibrary() {
  if (!store.lastStructureText) return
  if (!libraryEntryName.value.trim()) {
    saveToLibraryError.value = '请先给这次预测结果起一个名字'
    return
  }

  try {
    isSavingToLibrary.value = true
    saveToLibraryError.value = ''
    const normalizedSequence = normalizeSequenceInput(store.sequence, store.moleculeType)
    savedLibraryEntry.value = await saveMiniFoldEnzyme({
      name: libraryEntryName.value.trim(),
      sequence: normalizedSequence,
      pdb: store.lastStructureText,
      taskId: store.engineTaskId || undefined,
      envText: store.envText.trim() || undefined,
      targetChains: store.targetChains ?? undefined,
      backend: store.useAcceleration ? store.backend : 'cpu',
      useAcceleration: store.useAcceleration,
    })
  } catch (error) {
    saveToLibraryError.value = error instanceof Error ? error.message : '入库失败，请稍后重试'
  } finally {
    isSavingToLibrary.value = false
  }
}

watch(() => store.engineTaskId, (newId) => {
  if (newId && store.status === 'running') {
    ensureClock()
    startPolling()
  } else {
    stopPolling()
  }
})

watch(() => store.status, status => {
  if (status === 'success' && !libraryEntryName.value.trim()) {
    libraryEntryName.value = suggestedLibraryName.value
  }
  if (status !== 'success') {
    savedLibraryEntry.value = null
  }
  if (status === 'idle') {
    runtimeLog.value = ''
    lastLogUpdatedAt.value = null
    autoScrollLogs.value = true
    stopClock()
  }
  if (status === 'success' || status === 'error') {
    refreshLogs()
    stopClock()
  }
})

onMounted(() => {
  if (store.engineTaskId && store.status === 'running') {
    ensureClock()
    startPolling()
  }
  if (store.engineTaskId && (store.status === 'success' || store.status === 'error')) {
    refreshLogs()
  }
})

onUnmounted(() => {
  stopPolling()
  stopClock()
})
</script>

<template>
  <div class="space-y-6 pb-20">
    <!-- Top Action Bar -->
    <div class="apple-soft-panel flex flex-col gap-4 rounded-2xl p-4 sm:flex-row sm:items-center sm:justify-between">
      <div class="flex items-center gap-4">
        <button
          type="button"
          class="inline-flex items-center gap-2 text-xs font-bold text-apple-secondary-text hover:text-apple-text transition-colors"
          @click="router.push('/prediction')"
        >
          <ArrowLeft :size="14" />
          返回
        </button>
        <div class="h-4 w-px bg-apple-border"></div>
        <div class="flex items-center rounded-full bg-apple-background/32 p-1 shadow-[inset_0_1px_0_rgba(148,163,184,0.03)]">
          <button
            class="px-4 py-1 text-[10px] font-bold rounded-full transition-all duration-200"
            :class="store.moleculeType === 'protein' ? 'bg-[linear-gradient(135deg,rgba(9,13,24,0.96),rgba(92,199,245,0.7))] text-white shadow-[0_10px_24px_-18px_rgba(92,199,245,0.22)]' : 'text-apple-secondary-text hover:text-apple-text'"
            @click="store.moleculeType = 'protein'"
          >
            蛋白
          </button>
          <button
            class="px-4 py-1 text-[10px] font-bold rounded-full transition-all duration-200"
            :class="store.moleculeType === 'RNA' ? 'bg-[linear-gradient(135deg,rgba(8,16,26,0.96),rgba(103,218,205,0.72))] text-white shadow-[0_10px_24px_-18px_rgba(103,218,205,0.2)]' : 'text-apple-secondary-text hover:text-apple-text'"
            @click="store.moleculeType = 'RNA'"
          >
            RNA
          </button>
        </div>
      </div>

      <div class="flex items-center gap-3">
        <span class="inline-flex items-center gap-2 rounded-full px-3 py-1 text-[10px] font-bold" :class="statusMeta.chipClass">
          <Activity v-if="store.status === 'running'" class="animate-pulse" :size="12" />
          <CheckCircle2 v-else-if="store.status === 'success'" :size="12" />
          <AlertCircle v-else-if="store.status === 'error'" :size="12" />
          <Microscope v-else :size="12" />
          {{ statusMeta.title }}
        </span>
        <span class="text-[10px] font-bold text-apple-secondary-text tabular-nums">
          ID: {{ selectedStructureId }}
        </span>
      </div>
    </div>

    <!-- Main Workspace -->
    <div class="grid grid-cols-1 gap-6 lg:grid-cols-2">
      <!-- Left: Execution Configuration -->
      <div class="space-y-6">
        <div class="apple-card rounded-3xl p-8 space-y-8">
          <!-- Header Section -->
          <div class="space-y-2">
            <h2 class="text-lg font-bold text-apple-text flex items-center gap-2">
              <Dna class="text-apple-blue" :size="20" />
              任务配置
            </h2>
            <p class="text-xs text-apple-secondary-text">定义序列信息、环境约束及计算引擎参数以启动预测任务。</p>
          </div>

          <!-- Sequence Input -->
          <div class="space-y-3">
            <div class="flex items-center justify-between">
              <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest">序列输入 ({{ sequenceKindLabel }})</label>
              <div class="flex items-center gap-3">
                <button type="button" class="text-[10px] font-bold text-apple-blue hover:underline" @click="fillExample">载入示例</button>
                <button type="button" class="text-[10px] font-bold text-apple-secondary-text hover:underline" @click="store.sequence = ''">清空</button>
              </div>
            </div>
            <textarea
              v-model="store.sequence"
              class="apple-input min-h-[220px] text-xs font-mono p-4 bg-apple-background/30"
              placeholder="粘贴 FASTA 或纯序列数据..."
            />
          </div>

          <!-- Environment & Constraints -->
          <div class="grid gap-6 sm:grid-cols-2">
            <div class="space-y-3">
              <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest">环境/功能描述</label>
              <textarea
                v-model="store.envText"
                class="apple-input min-h-[120px] text-xs p-4"
                placeholder="描述酶的作用环境或目标构象特征..."
              />
            </div>
            <div class="space-y-6">
              <div v-if="store.moleculeType === 'protein'" class="space-y-3">
                <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest">目标链数</label>
                <select :value="store.targetChains ?? ''" class="apple-input text-xs" @change="updateTargetChains">
                  <option v-for="option in targetChainOptions" :key="option.value || 'auto'" :value="option.value">{{ option.label }}</option>
                </select>
              </div>
              <div class="space-y-3">
                <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest">计算后端</label>
                <select :value="selectedInferenceMode" class="apple-input text-xs" @change="e => selectInferenceMode((e.target as HTMLSelectElement).value)">
                  <option v-for="item in backendOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
                </select>
              </div>
            </div>
          </div>

          <!-- Engine Path -->
          <div class="space-y-3">
            <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest">推理引擎路径 (Python/Conda)</label>
            <input v-model="store.condaEnvName" type="text" class="apple-input text-xs" placeholder="自动发现，或填写 Python/Conda 具体路径..." />
          </div>
          
          <div class="flex items-center justify-between pt-6 border-t border-apple-border/50">
            <div class="flex items-center gap-4">
              <div v-for="item in readinessItems" :key="item.label" class="flex items-center gap-1.5">
                <CheckCircle2 v-if="item.done" class="text-emerald-500" :size="12" />
                <div v-else class="w-2 h-2 rounded-full bg-apple-background/50 shadow-[inset_0_0_0_1px_rgba(71,85,105,0.14)]"></div>
                <span class="text-[10px] font-bold text-apple-secondary-text">{{ item.label }}</span>
              </div>
            </div>
            <button
              type="button"
              class="apple-button-primary px-8 py-2.5 flex items-center gap-2 disabled:opacity-50 shadow-[0_18px_38px_-28px_rgba(56,189,248,0.28)]"
              :disabled="store.isSubmitting || !store.sequence.trim()"
              @click="handleSubmit"
            >
              <Loader2 v-if="store.isSubmitting" class="animate-spin" :size="16" />
              <Play v-else :size="16" />
              <span class="text-sm font-bold">启动推理</span>
            </button>
          </div>
        </div>
      </div>

      <!-- Right: Runtime Monitor & Result -->
      <div class="space-y-6">
        <!-- Execution Monitor (Running/Error/Idle) -->
        <div v-if="store.status !== 'success'" class="apple-card rounded-3xl p-8 space-y-8">
          <div class="flex items-center justify-between">
            <div class="space-y-1">
              <h2 class="text-lg font-bold text-apple-text flex items-center gap-2">
                <Activity class="text-apple-blue" :size="20" />
                实时监控
              </h2>
              <p class="text-xs text-apple-secondary-text">跟踪结构推理的实时进度与系统状态。</p>
            </div>
            <div class="flex items-center gap-4">
              <div class="text-right">
                <p class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest">已耗时</p>
                <p class="text-sm font-bold text-apple-text tabular-nums">{{ elapsedLabel }}</p>
              </div>
              <div class="h-8 w-px bg-apple-border"></div>
              <div class="text-right">
                <p class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest">当前阶段</p>
                <p class="text-sm font-bold text-apple-text">{{ currentStageLabel }}</p>
              </div>
            </div>
          </div>

          <!-- Simplified Monitor UI -->
          <div class="space-y-4">
            <div class="relative h-2 rounded-full bg-apple-background overflow-hidden">
              <div 
                class="h-full bg-apple-blue transition-all duration-700 relative" 
                :style="{ width: `${stageProgress}%` }"
              >
                <div v-if="store.status === 'running'" class="absolute inset-0 bg-gradient-to-r from-transparent via-apple-blue/20 to-transparent animate-shimmer"></div>
              </div>
            </div>
            
            <div class="flex flex-wrap gap-2">
              <div
                v-for="(stage, index) in stageItems"
                :key="stage.label"
                class="flex items-center gap-2 rounded-full px-3 py-1 text-[10px] font-bold transition-all"
                :class="stage.state === 'done' ? 'bg-emerald-500/10 text-emerald-300' : stage.state === 'active' ? 'bg-apple-blue/10 text-apple-blue' : 'bg-apple-background/45 text-apple-secondary-text opacity-75'"
              >
                <span class="w-4 h-4 rounded-full flex items-center justify-center bg-current/10">{{ index + 1 }}</span>
                <span>{{ stage.label }}</span>
              </div>
            </div>
          </div>

          <div class="space-y-3">
            <div class="flex items-center justify-between">
              <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest flex items-center gap-2">
                <span class="w-1.5 h-1.5 rounded-full" :class="store.status === 'running' ? 'bg-emerald-500 animate-pulse' : 'bg-apple-border'"></span>
                运行日志 (Terminal)
              </label>
              <div class="flex items-center gap-3 text-[10px] font-bold text-apple-secondary-text">
                <button type="button" class="hover:text-apple-blue transition-colors" @click="refreshLogs">刷新</button>
                <span class="opacity-30">|</span>
                <span class="opacity-50 uppercase tracking-tighter">{{ runtimeSignalTone }}</span>
              </div>
            </div>
            <div
              ref="consoleViewport"
              class="h-[420px] overflow-y-auto rounded-2xl border border-apple-border/40 bg-[linear-gradient(180deg,rgba(5,9,18,0.96),rgba(8,13,24,0.94))] p-5 font-mono text-[11px] leading-relaxed text-emerald-300 shadow-[inset_0_1px_0_rgba(255,255,255,0.02)]"
              @scroll="handleLogScroll"
            >
              <template v-if="runtimeLogLines.length">
                <p v-for="(line, index) in runtimeLogLines" :key="index" class="whitespace-pre-wrap break-words opacity-85 hover:opacity-100 transition-opacity">
                  <span class="mr-3 select-none text-[9px] text-slate-600/80">{{ String(index + 1).padStart(3, '0') }}</span>{{ line }}
                </p>
              </template>
              <div v-else class="h-full flex flex-col items-center justify-center gap-3 opacity-20">
                <Loader2 v-if="store.status === 'running'" class="animate-spin" :size="24" />
                <p class="text-xs italic">等待引擎输出实时日志...</p>
              </div>
            </div>
          </div>

          <!-- Error Feedback -->
          <div v-if="store.status === 'error'" class="rounded-2xl bg-red-500/[0.05] p-4 space-y-2 shadow-[inset_0_0_0_1px_rgba(239,68,68,0.12)]">
            <div class="flex items-center gap-2 text-red-400">
              <AlertCircle :size="16" />
              <span class="text-xs font-bold uppercase tracking-widest">任务执行失败</span>
            </div>
            <p class="text-xs text-red-300/80 leading-relaxed">{{ store.error || '发生了未知错误，请检查日志输出。' }}</p>
          </div>
        </div>

        <!-- Result Showcase (Success Only) -->
        <div v-else class="apple-card rounded-3xl overflow-hidden flex flex-col min-h-[680px]">
          <div class="p-5 flex items-center justify-between bg-apple-background/30">
            <div class="flex items-center gap-3">
              <div class="w-8 h-8 rounded-apple bg-apple-blue/10 text-apple-blue flex items-center justify-center">
                <Sparkles :size="16" />
              </div>
              <div>
                <h3 class="text-sm font-bold text-apple-text">预测结果</h3>
                <p class="text-[10px] text-apple-secondary-text uppercase tracking-widest font-bold">
                  {{ store.targetChains ? `${store.targetChains} 条链` : '自动判定链数' }} • {{ inferenceModeLabel }}
                </p>
              </div>
            </div>
            <div class="flex items-center gap-2">
              <button
                type="button"
                class="p-2 rounded-full hover:bg-apple-background/55 text-apple-secondary-text transition-colors"
                title="全屏查看"
                @click="showFullscreenViewer = true"
              >
                <Maximize2 :size="14" />
              </button>
              <button
                type="button"
                class="flex items-center gap-2 px-4 py-2 rounded-apple bg-[linear-gradient(135deg,rgba(9,13,24,0.96),rgba(92,199,245,0.72))] text-white text-[10px] font-bold uppercase tracking-widest transition-all shadow-[0_12px_30px_-18px_rgba(92,199,245,0.22)]"
                @click="downloadStructure"
              >
                <Download :size="14" />
                PDB
              </button>
            </div>
          </div>

          <div class="flex-1 relative min-h-[400px] bg-[radial-gradient(circle_at_top,rgba(92,199,245,0.06),transparent_28%),linear-gradient(180deg,rgba(7,11,23,0.94),rgba(9,14,25,0.84))]">
            <StructureViewer
              :url="store.viewerUrl!"
              source-db="LOCAL"
              :format="store.viewerFormat"
              class="w-full h-full"
            />
            <div class="absolute bottom-4 left-4 right-4 flex justify-between items-end pointer-events-none">
              <div class="px-3 py-1.5 rounded-full bg-[rgba(9,14,25,0.58)] backdrop-blur text-[10px] font-bold text-white/90 shadow-[inset_0_0_0_1px_rgba(255,255,255,0.04),0_12px_30px_-18px_rgba(92,199,245,0.18)]">
                ID: {{ selectedStructureId }}
              </div>
              <div v-if="qualityAssessment" class="px-3 py-1.5 rounded-full shadow-lg text-[10px] font-bold uppercase tracking-widest text-white" :class="getQualityTrackClass(qualityAssessment.overallScore)">
                Score: {{ qualityScoreLabel }}
              </div>
            </div>
          </div>

          <!-- Result Metrics / Archive -->
          <div class="p-5 bg-apple-background/10 space-y-4">
            <div class="grid grid-cols-2 gap-3">
              <div class="space-y-3">
                <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest">预测存档</label>
                <div class="flex gap-2">
                  <input
                    v-model="libraryEntryName"
                    type="text"
                    class="apple-input text-[11px] h-9"
                    placeholder="为结果命名..."
                  />
                  <button
                    type="button"
                    class="apple-button-primary !py-0 !px-4 h-9 flex items-center gap-2 shrink-0"
                    :disabled="isSavingToLibrary"
                    @click="handleSaveToLibrary"
                  >
                    <FolderPlus :size="14" />
                  </button>
                </div>
              </div>
              <div class="space-y-3">
                <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest">操作</label>
                <div class="flex gap-2">
                  <button
                    v-if="savedLibraryEntry"
                    type="button"
                    class="apple-button-secondary !py-0 !px-4 h-9 text-[10px] font-bold flex-1"
                    @click="router.push({ path: '/library/predicted', query: { enzymeId: String(savedLibraryEntry.id) } })"
                  >
                    查看成果库
                  </button>
                  <button
                    v-else
                    type="button"
                    class="apple-button-secondary !py-0 !px-4 h-9 text-[10px] font-bold flex-1 opacity-50 cursor-not-allowed"
                  >
                    未入库
                  </button>
                </div>
              </div>
            </div>
            <p v-if="saveToLibraryError" class="text-[10px] font-bold text-red-500">{{ saveToLibraryError }}</p>
          </div>
        </div>
      </div>
    </div>

    <transition name="fade">
      <div v-if="showFullscreenViewer && store.viewerUrl && store.status === 'success'" class="fixed inset-0 z-[100] bg-black/80 backdrop-blur-md flex flex-col">
        <div class="h-16 px-8 flex items-center justify-between border-b border-apple-border/60 bg-[rgba(5,9,18,0.38)]">
          <div class="flex items-center gap-4">
            <h3 class="text-white font-bold">MiniFold 结构结果</h3>
            <span class="px-2 py-0.5 rounded-full bg-apple-blue/90 text-white text-[10px] font-bold uppercase tracking-widest">
              {{ selectedStructureId }}
            </span>
          </div>
          <button
            type="button"
            class="w-10 h-10 rounded-full bg-[rgba(9,14,25,0.54)] text-white flex items-center justify-center hover:bg-[rgba(12,18,30,0.82)] transition-all shadow-[inset_0_0_0_1px_rgba(255,255,255,0.05)]"
            @click="showFullscreenViewer = false"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="lucide lucide-x"><path d="M18 6 6 18"/><path d="m6 6 12 12"/></svg>
          </button>
        </div>
        <div class="flex-1 p-8">
          <StructureViewer
            :url="store.viewerUrl"
            source-db="LOCAL"
            :format="store.viewerFormat"
          />
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped>
@keyframes shimmer {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(100%); }
}
.animate-shimmer {
  animation: shimmer 2s infinite;
}
</style>
