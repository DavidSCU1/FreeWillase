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
const isTaskPanelCollapsed = ref(false)

let resultInterval: ReturnType<typeof setInterval> | null = null
let logInterval: ReturnType<typeof setInterval> | null = null
let clockInterval: ReturnType<typeof setInterval> | null = null
const TASK_PANEL_STORAGE_KEY = 'minifoldTaskPanelCollapsed'

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
  { label: '自动选择', value: 'auto', hint: '优先尝试最合适的本机后端' },
  { label: 'CUDA', value: 'cuda', hint: '优先调用 NVIDIA 独显' },
  { label: 'DirectML', value: 'directml', hint: '统一尝试 Windows 图形设备' },
  { label: 'IPEX', value: 'ipex', hint: '偏向 Intel XPU / Arc' },
  { label: 'oneAPI CPU', value: 'oneapi_cpu', hint: 'Intel CPU 加速路径' },
  { label: 'CPU', value: 'cpu', hint: '最稳妥，但速度最慢' },
] as const

const selectedStructureId = computed(() => store.engineTaskId || '尚未创建')
const selectedStructureStatus = computed(() => {
  if (store.status === 'success') return '本地结构已生成'
  if (store.status === 'running') return '结构计算中'
  if (store.status === 'error') return '结构生成失败'
  return '等待开始'
})
const backendLabel = computed(() => backendOptions.find(item => item.value === store.backend)?.label || store.backend)
const backendHint = computed(() => backendOptions.find(item => item.value === store.backend)?.hint || '')
const targetChainLabel = computed(() => store.targetChains ? `${store.targetChains} 条链` : '自动判断')
const condaEnvLabel = computed(() => store.condaEnvName.trim() || '自动发现 / MINIFOLD_PYTHON')
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
    label: '序列',
    value: normalizedSequenceLength.value ? `${normalizedSequenceLength.value} aa` : '未填写',
  },
  {
    label: '环境描述',
    value: envLength.value ? `已填写 ${envLength.value} 字` : '选填',
  },
  {
    label: '链数',
    value: targetChainLabel.value,
  },
  {
    label: '执行模式',
    value: store.useAcceleration ? `加速 · ${backendLabel.value}` : 'CPU',
  },
  {
    label: 'Python 环境',
    value: condaEnvLabel.value,
  },
])
const readinessItems = computed(() => [
  {
    label: '输入序列',
    done: normalizedSequenceLength.value > 0,
    hint: normalizedSequenceLength.value > 0 ? `${normalizedSequenceLength.value} aa` : '必填',
  },
  {
    label: '环境描述',
    done: envLength.value > 0,
    hint: envLength.value > 0 ? '已补充场景约束' : '选填',
  },
  {
    label: '执行配置',
    done: true,
    hint: `${targetChainLabel.value} · ${store.useAcceleration ? backendLabel.value : 'CPU'} · ${store.condaEnvName.trim() || '自动环境'}`,
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
  if (store.status === 'success') return '结构已完成，可直接查看结果'
  if (store.status === 'error') return store.engineTaskId ? '任务中途停止，请看下方日志' : '请求尚未进入任务队列'
  if (store.status === 'running') return '运行时正在连续输出，日志会自动刷新'
  return '点击开始后，这里会展示完整任务过程'
})
const runtimeSignalLabel = computed(() => {
  if (store.status === 'success') return '结构文件已稳定写出，可直接查看或入库'
  if (store.status === 'error') return store.engineTaskId ? '运行信号已中断，请结合日志定位问题' : '任务还未进入运行阶段'
  if (store.status === 'running') return '推理引擎持续输出中，当前波形为实时采样态'
  return '等待任务启动后开始采样'
})
const runtimeSignalTone = computed(() => {
  if (store.status === 'success') return 'Result Locked'
  if (store.status === 'error') return 'Signal Lost'
  if (store.status === 'running') return 'Signal Live'
  return 'Standby'
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
const recoveredTaskHint = computed(() => {
  if (!store.engineTaskId) return ''
  if (store.status === 'running') return '页面已恢复上次未完成任务，正在继续监控实时输出。'
  if (store.status === 'success') return '页面已恢复上次任务结果，你可以继续查看结构或下载 PDB。'
  if (store.status === 'error') return '页面已恢复上次任务状态，可直接查看失败信息与日志。'
  return ''
})
const taskList = computed(() => store.taskHistory.map(task => ({
  ...task,
  badgeClass: task.status === 'running'
    ? 'bg-apple-blue/10 text-apple-blue'
    : task.status === 'success'
      ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-300'
      : 'bg-red-500/10 text-red-500',
  badgeLabel: task.status === 'running' ? 'Running' : task.status === 'success' ? 'Success' : 'Error',
  isActive: task.taskId === store.engineTaskId,
  updatedLabel: new Date(task.updatedAt).toLocaleTimeString('zh-CN', { hour12: false }),
})))
const activeTaskCard = computed(() => taskList.value.find(task => task.isActive) || taskList.value[0] || null)
const statusMeta = computed(() => {
  if (store.status === 'success') {
    return {
      title: '结果已返回',
      description: '结构已经生成完成，可以直接查看三维结果或下载 PDB。',
      chip: 'Success',
      chipClass: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-300',
    }
  }
  if (store.status === 'running') {
    return {
      title: 'MiniFold 正在运行',
      description: '任务已提交，页面会持续轮询结果并在完成后自动刷新视图。',
      chip: 'Running',
      chipClass: 'bg-apple-blue/10 text-apple-blue',
    }
  }
  if (store.status === 'error') {
    return {
      title: store.engineTaskId ? '任务执行失败' : '提交未成功',
      description: store.error || (store.engineTaskId
        ? '请检查输入格式、环境约束或本机后端状态后重试。'
        : '这次请求还没拿到任务 ID，说明失败发生在提交阶段。请查看页面下方错误提示或浏览器控制台日志。'),
      chip: 'Error',
      chipClass: 'bg-red-500/10 text-red-500',
    }
  }
  return {
    title: '准备开始推理',
    description: '按顺序填写左侧配置，确认摘要无误后即可启动 MiniFold。',
    chip: 'Idle',
    chipClass: 'bg-apple-background text-apple-secondary-text',
  }
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

function fillExample() {
  store.sequence = `>sample_1
MKTFFVLLLCTFTVQAAPDAGVTKTYLQDVGGKSTLQKQLAELNQGQKELAAKLEQKQK`
  if (!store.envText.trim()) {
    store.envText = '线粒体相关酶，倾向形成稳定紧凑构象，尽量避免疏水核心过度暴露。'
  }
}

function toggleTaskPanel() {
  isTaskPanelCollapsed.value = !isTaskPanelCollapsed.value
  localStorage.setItem(TASK_PANEL_STORAGE_KEY, isTaskPanelCollapsed.value ? '1' : '0')
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

async function activateTask(taskId: string) {
  const switched = store.activateTask(taskId)
  if (!switched) return

  runtimeLog.value = ''
  lastLogUpdatedAt.value = null
  autoScrollLogs.value = true

  if (store.status === 'running') {
    ensureClock()
    startPolling()
  } else {
    stopPolling()
    stopClock()
    await refreshLogs()
  }
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
    const normalizedSequence = normalizeSequenceInput(store.sequence, 'protein')
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
  isTaskPanelCollapsed.value = localStorage.getItem(TASK_PANEL_STORAGE_KEY) === '1'
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
  <div class="space-y-8 pb-20">
    <div class="space-y-4">
      <button
        type="button"
        class="inline-flex items-center gap-2 text-xs font-bold text-apple-secondary-text hover:text-apple-text transition-colors"
        @click="router.push('/prediction')"
      >
        <ArrowLeft :size="14" />
        返回预测中心
      </button>

      <div class="apple-card p-6 md:p-7 bg-gradient-to-br from-apple-blue/8 via-transparent to-purple-500/8 border-apple-blue/10">
        <div class="flex flex-col gap-6 lg:flex-row lg:items-start lg:justify-between">
          <div class="space-y-3">
            <div class="flex items-center gap-3">
              <div class="w-10 h-10 rounded-apple bg-apple-blue/10 text-apple-blue flex items-center justify-center">
                <Sparkles :size="18" />
              </div>
              <div>
                <h1 class="text-3xl font-bold tracking-tight text-apple-text">MiniFold 工作台</h1>
                <p class="text-sm text-apple-secondary-text">把预测流程收成三步：准备输入、确认执行、查看结构结果。</p>
              </div>
            </div>

            <div class="flex flex-wrap gap-2">
              <span class="inline-flex items-center gap-2 rounded-full px-3 py-1 text-[11px] font-bold" :class="statusMeta.chipClass">
                <Activity v-if="store.status === 'running'" class="animate-pulse" :size="12" />
                <CheckCircle2 v-else-if="store.status === 'success'" :size="12" />
                <AlertCircle v-else-if="store.status === 'error'" :size="12" />
                <Microscope v-else :size="12" />
                {{ statusMeta.title }}
              </span>
              <span class="inline-flex items-center rounded-full bg-apple-background px-3 py-1 text-[11px] font-bold text-apple-secondary-text">
                任务 ID: {{ selectedStructureId }}
              </span>
            </div>

            <p class="max-w-2xl text-sm leading-relaxed text-apple-secondary-text">
              {{ statusMeta.description }}
            </p>
            <p v-if="recoveredTaskHint" class="text-[11px] font-bold text-apple-blue">
              {{ recoveredTaskHint }}
            </p>
          </div>

          <div class="grid grid-cols-2 gap-3 lg:min-w-[320px]">
            <div
              v-for="item in summaryItems"
              :key="item.label"
              class="rounded-apple border border-apple-border bg-white/60 dark:bg-white/5 px-4 py-3"
            >
              <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">{{ item.label }}</p>
              <p class="mt-1 text-sm font-semibold text-apple-text">{{ item.value }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="flex flex-col gap-8 xl:flex-row xl:items-start">
      <aside
        class="shrink-0 transition-all duration-300 xl:sticky xl:top-6"
        :class="isTaskPanelCollapsed ? 'xl:w-[92px]' : 'xl:w-[320px]'"
      >
        <div class="apple-card overflow-hidden">
          <div class="flex items-center justify-between gap-3 border-b border-apple-border px-4 py-4">
            <div class="flex items-center gap-3" :class="isTaskPanelCollapsed ? 'justify-center w-full' : ''">
              <div class="flex h-9 w-9 items-center justify-center rounded-apple bg-apple-blue/10 text-apple-blue">
                <ListTree :size="16" />
              </div>
              <div v-if="!isTaskPanelCollapsed" class="space-y-1">
                <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">任务侧栏</p>
                <h3 class="text-sm font-bold text-apple-text">最近 MiniFold 任务</h3>
              </div>
            </div>
            <button
              type="button"
              class="inline-flex rounded-full border border-apple-border p-2 text-apple-secondary-text transition-colors hover:bg-apple-background hover:text-apple-text"
              :title="isTaskPanelCollapsed ? '展开任务侧栏' : '收起任务侧栏'"
              @click="toggleTaskPanel"
            >
              <ChevronRight v-if="isTaskPanelCollapsed" :size="16" />
              <ChevronLeft v-else :size="16" />
            </button>
          </div>

          <div v-if="isTaskPanelCollapsed" class="space-y-3 p-3">
            <div class="rounded-apple border border-apple-border bg-apple-background/35 px-3 py-3 text-center">
              <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">任务数</p>
              <p class="mt-1 text-lg font-bold text-apple-text">{{ taskList.length }}</p>
            </div>

            <button
              v-for="task in taskList.slice(0, 6)"
              :key="task.taskId"
              type="button"
              class="flex w-full items-center justify-center rounded-apple border px-2 py-3 transition-all"
              :class="task.isActive
                ? 'border-apple-blue/40 bg-apple-blue/5 shadow-sm'
                : 'border-apple-border bg-white/60 dark:bg-white/5 hover:bg-apple-background/50'"
              :title="`#${task.taskId} · ${task.badgeLabel}`"
              @click="activateTask(task.taskId)"
            >
              <div
                class="h-2.5 w-2.5 rounded-full"
                :class="task.status === 'running' ? 'bg-apple-blue' : task.status === 'success' ? 'bg-emerald-500' : 'bg-red-500'"
              ></div>
            </button>

            <div
              v-if="taskList.length === 0"
              class="rounded-apple border border-dashed border-apple-border bg-apple-background/35 px-2 py-6 text-center text-[11px] text-apple-secondary-text"
            >
              暂无任务
            </div>
          </div>

          <div v-else class="space-y-4 p-4">
            <div v-if="activeTaskCard" class="rounded-apple border border-apple-blue/15 bg-apple-blue/5 px-4 py-4">
              <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">当前焦点</p>
              <div class="mt-3 flex items-start justify-between gap-3">
                <div class="min-w-0">
                  <p class="text-sm font-bold text-apple-text">#{{ activeTaskCard.taskId }}</p>
                  <p class="mt-1 text-[11px] text-apple-secondary-text">
                    {{ activeTaskCard.sequenceLength }} aa · {{ activeTaskCard.targetChains ? `${activeTaskCard.targetChains} 条链` : '自动链数' }}
                  </p>
                </div>
                <span class="rounded-full px-2 py-1 text-[10px] font-bold uppercase tracking-widest" :class="activeTaskCard.badgeClass">
                  {{ activeTaskCard.badgeLabel }}
                </span>
              </div>
              <p class="mt-3 truncate text-[11px] text-apple-secondary-text">{{ activeTaskCard.error || activeTaskCard.pythonLabel }}</p>
            </div>

            <div class="flex items-center justify-between gap-3">
              <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">历史切换</p>
              <span class="rounded-full bg-apple-background px-3 py-1 text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">
                {{ taskList.length }} items
              </span>
            </div>

            <div class="space-y-2 max-h-[420px] overflow-y-auto pr-1">
              <button
                v-for="task in taskList"
                :key="task.taskId"
                type="button"
                class="w-full rounded-apple border px-4 py-3 text-left transition-all"
                :class="task.isActive
                  ? 'border-apple-blue/40 bg-apple-blue/5 shadow-sm'
                  : 'border-apple-border bg-white/60 dark:bg-white/5 hover:bg-apple-background/50'"
                @click="activateTask(task.taskId)"
              >
                <div class="flex items-start justify-between gap-3">
                  <div class="min-w-0 space-y-1">
                    <div class="flex items-center gap-2">
                      <p class="text-xs font-bold text-apple-text">#{{ task.taskId }}</p>
                      <span class="rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-widest" :class="task.badgeClass">
                        {{ task.badgeLabel }}
                      </span>
                      <span v-if="task.isActive" class="rounded-full bg-apple-text px-2 py-0.5 text-[10px] font-bold uppercase tracking-widest text-white">
                        Current
                      </span>
                    </div>
                    <p class="text-[11px] text-apple-secondary-text">
                      {{ task.sequenceLength }} aa · {{ task.targetChains ? `${task.targetChains} 条链` : '自动链数' }} · {{ task.backend }}
                    </p>
                    <p class="truncate text-[11px] text-apple-secondary-text">
                      {{ task.error || task.pythonLabel }}
                    </p>
                  </div>
                  <div class="shrink-0 text-right">
                    <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">Updated</p>
                    <p class="mt-1 text-[11px] font-semibold text-apple-text">{{ task.updatedLabel }}</p>
                  </div>
                </div>
              </button>

              <div v-if="taskList.length === 0" class="rounded-apple border border-dashed border-apple-border bg-apple-background/35 px-4 py-6 text-center text-[11px] text-apple-secondary-text">
                还没有 MiniFold 历史任务。开始第一次推理后，这里会自动记录并可随时切回查看。
              </div>
            </div>
          </div>
        </div>
      </aside>

      <div class="min-w-0 flex-1 space-y-8">
        <div class="grid grid-cols-1 gap-8 2xl:grid-cols-[minmax(0,1.08fr)_minmax(320px,0.92fr)]">
          <div class="space-y-8">
            <div class="space-y-2">
              <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">执行配置</p>
              <h2 class="text-lg font-bold text-apple-text">把输入、约束、运行方式分开确认</h2>
              <p class="text-sm text-apple-secondary-text">三步仍然按顺序走，但每块信息单独成区，避免挤在一起看不清。</p>
            </div>

            <div class="apple-card p-6 space-y-6">
              <div class="flex items-center justify-between gap-4">
                <div class="flex items-center gap-3">
                  <div class="w-8 h-8 rounded-apple bg-apple-green/10 text-apple-green flex items-center justify-center">
                    <Dna :size="16" />
                  </div>
                  <div>
                    <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">Step 1</p>
                    <h3 class="text-sm font-bold text-apple-text">准备输入序列</h3>
                    <p class="text-[11px] text-apple-secondary-text">支持 Plain 或单条 FASTA，标题行会自动忽略。</p>
                  </div>
                </div>
                <div class="flex items-center gap-2">
                  <button
                    type="button"
                    class="px-3 py-1.5 rounded-apple border border-apple-border text-[11px] font-bold text-apple-secondary-text hover:text-apple-text hover:bg-apple-background transition-colors"
                    @click="fillExample"
                  >
                    填入示例
                  </button>
                  <button
                    type="button"
                    class="px-3 py-1.5 rounded-apple border border-apple-border text-[11px] font-bold text-apple-secondary-text hover:text-apple-text hover:bg-apple-background transition-colors"
                    @click="store.sequence = ''"
                  >
                    清空
                  </button>
                </div>
              </div>

              <div class="grid gap-4 md:grid-cols-3">
                <div class="rounded-apple border border-apple-border bg-apple-background/35 px-4 py-3">
                  <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">有效长度</p>
                  <p class="mt-1 text-lg font-bold text-apple-text">{{ normalizedSequenceLength || 0 }}</p>
                </div>
                <div class="rounded-apple border border-apple-border bg-apple-background/35 px-4 py-3">
                  <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">输入状态</p>
                  <p class="mt-1 text-sm font-semibold" :class="normalizedSequenceLength ? 'text-emerald-600 dark:text-emerald-300' : 'text-apple-secondary-text'">
                    {{ normalizedSequenceLength ? '可以提交' : '等待填写' }}
                  </p>
                </div>
                <div class="rounded-apple border border-apple-border bg-apple-background/35 px-4 py-3">
                  <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">格式要求</p>
                  <p class="mt-1 text-sm font-semibold text-apple-text">单条蛋白序列</p>
                </div>
              </div>

              <div class="space-y-2">
                <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest ml-1">输入序列</label>
                <textarea
                  v-model="store.sequence"
                  class="apple-input min-h-[220px] text-xs font-mono leading-relaxed p-4 bg-apple-background/50 focus:bg-white dark:focus:bg-white/5"
                  placeholder=">sample_1&#10;MKTFFVLLLCTFTVQAAPDAGVTKTYLQDVGGKSTLQKQLAELNQGQKELAAKLEQKQK"
                />
                <p class="text-[11px] text-apple-secondary-text">建议直接粘贴单条目标蛋白序列，避免把多条链一起放入这里。</p>
              </div>
            </div>

            <div class="apple-card p-6 space-y-6">
              <div class="flex items-center gap-3">
                <div class="w-8 h-8 rounded-apple bg-apple-green/10 text-apple-green flex items-center justify-center">
                  <FileText :size="16" />
                </div>
                <div>
                  <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">Step 2</p>
                  <h3 class="text-sm font-bold text-apple-text">补充环境与链数</h3>
                  <p class="text-[11px] text-apple-secondary-text">环境描述是选填项，链数用于告诉模型更偏向单链还是多链构象。</p>
                </div>
              </div>

              <div class="grid gap-6 lg:grid-cols-[minmax(0,1.4fr)_minmax(0,0.8fr)]">
                <div class="space-y-2">
                  <div class="flex items-center justify-between">
                    <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest ml-1">环境描述</label>
                    <span class="text-[10px] font-bold text-apple-secondary-text">{{ envLength }} 字</span>
                  </div>
                  <textarea
                    v-model="store.envText"
                    class="apple-input min-h-[140px] text-xs leading-relaxed p-4"
                    placeholder="例如：线粒体内膜相关酶，倾向形成稳定跨膜区段，避免过度暴露疏水残基。"
                  />
                  <p class="text-[11px] text-apple-secondary-text">可写生物学场景、亚细胞定位、跨膜偏好或希望避免的构象倾向。</p>
                </div>

                <div class="space-y-4">
                  <div class="space-y-2">
                    <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest ml-1">目标链数</label>
                    <select
                      :value="store.targetChains ?? ''"
                      class="apple-input text-xs"
                      @change="updateTargetChains"
                    >
                      <option v-for="item in targetChainOptions" :key="item.value || 'auto'" :value="item.value">
                        {{ item.label }}
                      </option>
                    </select>
                  </div>

                  <div class="rounded-apple border border-apple-border bg-apple-background/35 p-4 text-[11px] leading-relaxed text-apple-secondary-text">
                    <p class="font-bold text-apple-text">当前理解</p>
                    <p class="mt-2">链数：{{ targetChainLabel }}</p>
                    <p class="mt-1">环境：{{ envLength ? '已提供上下文约束' : '未提供，模型将主要依赖序列本身' }}</p>
                  </div>
                </div>
              </div>
            </div>

            <div class="apple-card p-6 space-y-6">
              <div class="flex items-center gap-3">
                <div class="w-8 h-8 rounded-apple bg-purple-500/10 text-purple-500 flex items-center justify-center">
                  <Cpu :size="16" />
                </div>
                <div>
                  <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">Step 3</p>
                  <h3 class="text-sm font-bold text-apple-text">确认执行配置</h3>
                  <p class="text-[11px] text-apple-secondary-text">这里既支持填写 Conda 环境名，也支持直接填写 Python 可执行文件路径，再决定是否加速和后端。</p>
                </div>
              </div>

              <div class="space-y-2">
                <div class="flex items-center justify-between">
                  <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest ml-1">Conda 环境名 / Python 路径</label>
                  <span class="text-[10px] font-bold text-apple-secondary-text">{{ store.condaEnvName.trim() ? '用户指定' : '自动发现' }}</span>
                </div>
                <input
                  v-model="store.condaEnvName"
                  type="text"
                  class="apple-input text-xs"
                  placeholder="例如：minifold 或 D:\\MiniFold\\python-portable\\python.exe"
                />
                <p class="text-[11px] text-apple-secondary-text">留空时继续使用 `MINIFOLD_PYTHON` 或系统 Python；填环境名会执行 `conda run -n 环境名 python`，填 `.exe` / 路径则直接调用该解释器。</p>
              </div>

              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <button
                  type="button"
                  class="rounded-apple border p-4 text-left transition-all"
                  :class="store.useAcceleration ? 'border-apple-blue/40 bg-apple-blue/5' : 'border-apple-border bg-apple-background/40'"
                  @click="store.useAcceleration = true"
                >
                  <p class="text-xs font-bold text-apple-text">启用加速</p>
                  <p class="mt-1 text-[11px] text-apple-secondary-text">尝试使用本机显卡或特定后端进行折叠。</p>
                </button>
                <button
                  type="button"
                  class="rounded-apple border p-4 text-left transition-all"
                  :class="!store.useAcceleration ? 'border-apple-blue/40 bg-apple-blue/5' : 'border-apple-border bg-apple-background/40'"
                  @click="store.useAcceleration = false"
                >
                  <p class="text-xs font-bold text-apple-text">仅 CPU</p>
                  <p class="mt-1 text-[11px] text-apple-secondary-text">禁用显卡后端，走最稳妥的 CPU 路线。</p>
                </button>
              </div>

              <div class="space-y-2">
                <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest ml-1">后端类型</label>
                <select v-model="store.backend" class="apple-input text-xs" :disabled="!store.useAcceleration">
                  <option v-for="item in backendOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
                </select>
                <p class="text-[11px] text-apple-secondary-text">
                  {{ backendHint }}
                </p>
              </div>

              <div class="rounded-apple border border-apple-border bg-apple-background/35 p-4">
                <div class="flex items-center gap-2 mb-3">
                  <Layers3 class="text-apple-blue" :size="14" />
                  <h4 class="text-xs font-bold text-apple-text">本次执行摘要</h4>
                </div>
                <div class="grid gap-3 md:grid-cols-4">
                  <div class="text-[11px] text-apple-secondary-text">
                    <p class="font-bold text-apple-text">输入</p>
                    <p class="mt-1">{{ normalizedSequenceLength || 0 }} aa</p>
                  </div>
                  <div class="text-[11px] text-apple-secondary-text">
                    <p class="font-bold text-apple-text">约束</p>
                    <p class="mt-1">{{ targetChainLabel }} / {{ envLength ? '含环境描述' : '无环境描述' }}</p>
                  </div>
                  <div class="text-[11px] text-apple-secondary-text">
                    <p class="font-bold text-apple-text">Python</p>
                    <p class="mt-1">{{ condaEnvLabel }}</p>
                  </div>
                  <div class="text-[11px] text-apple-secondary-text">
                    <p class="font-bold text-apple-text">运行方式</p>
                    <p class="mt-1">{{ store.useAcceleration ? backendLabel : 'CPU' }}</p>
                  </div>
                </div>
              </div>

              <div class="flex flex-col gap-4 pt-2 md:flex-row md:items-center md:justify-between">
                <p v-if="store.error" class="text-[11px] font-bold text-red-500">{{ store.error }}</p>
                <div v-else class="text-[11px] text-apple-secondary-text">
                  当前状态：<span class="font-bold text-apple-text">{{ selectedStructureStatus }}</span>
                </div>
                <div class="ml-auto flex items-center gap-3">
                  <button
                    type="button"
                    class="px-4 py-2 rounded-apple border border-apple-border text-xs font-bold text-apple-secondary-text hover:text-apple-text hover:bg-apple-background transition-colors"
                    @click="store.resetResult"
                  >
                    清空结果
                  </button>
                  <button
                    type="button"
                    class="apple-button-primary px-8 py-2.5 flex items-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
                    :disabled="store.isSubmitting || !store.sequence.trim()"
                    @click="handleSubmit"
                  >
                    <template v-if="store.isSubmitting">
                      <Loader2 class="animate-spin" :size="16" />
                      <span>提交中...</span>
                    </template>
                    <template v-else>
                      <Play :size="16" />
                      <span>开始 MiniFold 推理</span>
                    </template>
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div class="space-y-8 2xl:sticky 2xl:top-6">
            <div class="space-y-2">
              <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">执行总览</p>
              <h2 class="text-lg font-bold text-apple-text">运行状态和实时输出单独放一列</h2>
              <p class="text-sm text-apple-secondary-text">提交前看准备情况，提交后盯进度和日志，不和输入表单搅在一起。</p>
            </div>

            <div class="apple-card p-6 space-y-4">
              <div class="flex items-center justify-between gap-3">
                <div class="space-y-1">
                  <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">运行状态</p>
                  <h3 class="text-sm font-bold text-apple-text">{{ statusMeta.title }}</h3>
                </div>
                <span class="rounded-full px-3 py-1 text-[10px] font-bold uppercase tracking-widest" :class="statusMeta.chipClass">
                  {{ statusMeta.chip }}
                </span>
              </div>

              <div class="space-y-3">
                <div
                  v-for="item in readinessItems"
                  :key="item.label"
                  class="flex items-center justify-between rounded-apple border border-apple-border bg-apple-background/35 px-4 py-3"
                >
                  <div class="flex items-center gap-3">
                    <CheckCircle2 v-if="item.done" class="text-emerald-500" :size="16" />
                    <div v-else class="w-4 h-4 rounded-full border-2 border-apple-border"></div>
                    <div>
                      <p class="text-xs font-semibold text-apple-text">{{ item.label }}</p>
                      <p class="text-[11px] text-apple-secondary-text">{{ item.hint }}</p>
                    </div>
                  </div>
                </div>
              </div>

              <div class="rounded-apple border border-apple-border bg-white/60 dark:bg-white/5 px-4 py-3 text-[11px] leading-relaxed text-apple-secondary-text">
                <p><span class="font-bold text-apple-text">任务 ID：</span>{{ selectedStructureId }}</p>
                <p class="mt-1"><span class="font-bold text-apple-text">后端：</span>{{ store.useAcceleration ? backendLabel : 'CPU' }}</p>
                <p class="mt-1"><span class="font-bold text-apple-text">Python：</span>{{ condaEnvLabel }}</p>
                <p class="mt-1"><span class="font-bold text-apple-text">链数：</span>{{ targetChainLabel }}</p>
              </div>
            </div>

            <div class="apple-card p-6 space-y-5 overflow-hidden">
              <div class="flex items-start justify-between gap-4">
                <div class="space-y-1">
                  <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">任务进程</p>
                  <h3 class="text-sm font-bold text-apple-text">MiniFold Runtime Monitor</h3>
                  <p class="text-[11px] text-apple-secondary-text">{{ runtimeHeadline }}</p>
                </div>
                <div class="rounded-apple border border-apple-border bg-white/60 dark:bg-white/5 px-3 py-2 text-right">
                  <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">Elapsed</p>
                  <p class="mt-1 text-lg font-bold text-apple-text tabular-nums">{{ elapsedLabel }}</p>
                </div>
              </div>

              <div class="relative overflow-hidden rounded-[28px] border border-apple-blue/15 bg-[radial-gradient(circle_at_top,#60a5fa18,transparent_52%),linear-gradient(135deg,rgba(255,255,255,0.9),rgba(255,255,255,0.55))] dark:bg-[radial-gradient(circle_at_top,#60a5fa24,transparent_52%),linear-gradient(135deg,rgba(15,23,42,0.95),rgba(15,23,42,0.8))] p-5">
                <div class="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-apple-blue/60 to-transparent"></div>
                <div class="relative grid gap-5 lg:grid-cols-[minmax(0,1fr)_180px] lg:items-end">
                  <div class="space-y-4">
                    <div class="runtime-monitor-screen" :class="runtimeMonitorStateClass">
                      <div class="runtime-monitor-grid"></div>
                      <div v-if="store.status === 'running'" class="runtime-monitor-sweep"></div>
                      <svg viewBox="0 0 640 140" preserveAspectRatio="none" class="runtime-monitor-waveform">
                        <path
                          d="M0 84 H640"
                          class="runtime-monitor-baseline"
                        />
                        <path
                          d="M0 84 L56 84 L84 84 L96 76 L108 84 L140 84 L164 84 L176 52 L188 106 L206 22 L220 118 L236 84 L300 84 L330 84 L344 74 L356 84 L392 84 L420 84 L434 56 L446 102 L462 28 L476 114 L492 84 L556 84 L584 84 L596 76 L608 84 L640 84"
                          class="runtime-monitor-wave"
                        />
                      </svg>
                    </div>
                    <div class="flex flex-wrap items-end justify-between gap-4">
                      <div>
                        <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">Heartbeat</p>
                        <p class="mt-1 text-sm font-semibold text-apple-text">{{ runtimeSignalLabel }}</p>
                      </div>
                      <div class="flex items-center gap-2 rounded-full border border-white/60 dark:border-white/10 bg-white/70 dark:bg-white/5 px-3 py-1.5 text-[11px] font-bold text-apple-secondary-text">
                        <span
                          class="h-2.5 w-2.5 rounded-full"
                          :class="store.status === 'running'
                            ? 'bg-emerald-400 animate-pulse'
                            : store.status === 'success'
                              ? 'bg-cyan-400'
                              : store.status === 'error'
                                ? 'bg-rose-400'
                                : 'bg-slate-300 dark:bg-slate-600'"
                        ></span>
                        {{ runtimeSignalTone }}
                      </div>
                    </div>
                  </div>

                  <div class="grid gap-3 sm:grid-cols-3 lg:grid-cols-1">
                    <div class="runtime-monitor-readout">
                      <p class="runtime-monitor-readout-label">Signal</p>
                      <p class="runtime-monitor-readout-value">{{ runtimeSignalCode }}</p>
                    </div>
                    <div class="runtime-monitor-readout">
                      <p class="runtime-monitor-readout-label">Stage</p>
                      <p class="runtime-monitor-readout-value">{{ currentStageLabel }}</p>
                    </div>
                    <div class="runtime-monitor-readout">
                      <p class="runtime-monitor-readout-label">Log Sync</p>
                      <p class="runtime-monitor-readout-value text-base">{{ logTimestampLabel }}</p>
                    </div>
                  </div>
                </div>
              </div>

              <div class="space-y-3">
                <div class="flex items-center justify-between gap-3">
                  <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">阶段进度</p>
                  <p class="text-[11px] font-bold text-apple-text">{{ Math.round(stageProgress) }}%</p>
                </div>
                <div class="h-2 rounded-full bg-apple-background/70 overflow-hidden">
                  <div class="h-full rounded-full bg-gradient-to-r from-apple-blue via-cyan-400 to-violet-500 transition-all duration-700" :style="{ width: `${stageProgress}%` }"></div>
                </div>
                <div class="space-y-2">
                  <div
                    v-for="(stage, index) in stageItems"
                    :key="stage.label"
                    class="rounded-apple border px-4 py-3 transition-all"
                    :class="stage.state === 'done'
                      ? 'border-emerald-500/20 bg-emerald-500/5'
                      : stage.state === 'active'
                        ? 'border-apple-blue/30 bg-apple-blue/5'
                        : stage.state === 'error'
                          ? 'border-red-500/20 bg-red-500/5'
                          : 'border-apple-border bg-apple-background/35'"
                  >
                    <div class="flex items-start gap-3">
                      <div
                        class="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-[10px] font-bold"
                        :class="stage.state === 'done'
                          ? 'bg-emerald-500 text-white'
                          : stage.state === 'active'
                            ? 'bg-apple-blue text-white'
                            : stage.state === 'error'
                              ? 'bg-red-500 text-white'
                              : 'bg-apple-background text-apple-secondary-text'"
                      >
                        {{ index + 1 }}
                      </div>
                      <div class="min-w-0">
                        <div class="flex items-center gap-2">
                          <p class="text-xs font-bold text-apple-text">{{ stage.label }}</p>
                          <span
                            class="rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-widest"
                            :class="stage.state === 'done'
                              ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-300'
                              : stage.state === 'active'
                                ? 'bg-apple-blue/10 text-apple-blue'
                                : stage.state === 'error'
                                  ? 'bg-red-500/10 text-red-500'
                                  : 'bg-apple-background text-apple-secondary-text'"
                          >
                            {{ stage.state === 'done' ? 'Done' : stage.state === 'active' ? 'Active' : stage.state === 'error' ? 'Error' : 'Pending' }}
                          </span>
                        </div>
                        <p class="mt-1 text-[11px] leading-relaxed text-apple-secondary-text">{{ stage.hint }}</p>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <div class="grid gap-3 md:grid-cols-2">
                <div class="rounded-apple border border-apple-border bg-white/60 dark:bg-white/5 p-4">
                  <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">最近输出</p>
                  <div class="mt-3 space-y-2">
                    <p
                      v-for="(line, index) in latestLogLines"
                      :key="`${index}-${line}`"
                      class="rounded-2xl bg-apple-background/60 px-3 py-2 text-[11px] leading-relaxed text-apple-secondary-text"
                    >
                      {{ line }}
                    </p>
                    <p v-if="latestLogLines.length === 0" class="rounded-2xl bg-apple-background/60 px-3 py-2 text-[11px] text-apple-secondary-text">
                      任务开始后，这里会滚动显示最新几条运行日志。
                    </p>
                  </div>
                </div>
                <div class="rounded-apple border border-apple-border bg-white/60 dark:bg-white/5 p-4">
                  <div class="flex items-center justify-between gap-3">
                    <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">实时控制台</p>
                    <div class="flex items-center gap-2 text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">
                      <span class="inline-flex h-2 w-2 rounded-full bg-emerald-500" :class="store.status === 'running' ? 'animate-pulse' : ''"></span>
                      <span>{{ logTimestampLabel }}</span>
                    </div>
                  </div>
                  <div
                    ref="consoleViewport"
                    class="mt-3 h-[260px] overflow-y-auto rounded-[24px] bg-slate-950 px-4 py-4 font-mono text-[11px] leading-6 text-emerald-300 shadow-inner"
                    @scroll="handleLogScroll"
                  >
                    <template v-if="runtimeLogLines.length">
                      <p
                        v-for="(line, index) in runtimeLogLines"
                        :key="`${index}-${line}`"
                        class="whitespace-pre-wrap break-words"
                      >
                        <span class="text-slate-500 mr-3 select-none">{{ String(index + 1).padStart(3, '0') }}</span>{{ line }}
                      </p>
                    </template>
                    <p v-else class="text-slate-400">[console] 等待任务输出...</p>
                  </div>
                  <div class="mt-3 flex items-center justify-between gap-3 text-[11px] text-apple-secondary-text">
                    <span>自动滚动：{{ autoScrollLogs ? '开启' : '暂停' }}</span>
                    <button
                      type="button"
                      class="rounded-full border border-apple-border px-3 py-1 font-bold text-apple-text transition-colors hover:bg-apple-background"
                      @click="refreshLogs"
                    >
                      刷新日志
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="space-y-2">
          <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">结构结果</p>
          <h2 class="text-lg font-bold text-apple-text">结果查看和入库独立成区</h2>
          <p class="text-sm text-apple-secondary-text">上面负责配置和监控，下面专心看结构、下载 PDB、确认是否入库。</p>
        </div>

        <div class="apple-card overflow-hidden min-h-[720px]">
        <template v-if="store.status === 'success' && store.viewerUrl">
          <div class="p-6 border-b border-apple-border flex items-center justify-between bg-apple-background/30">
            <div class="flex items-center gap-3">
              <div class="w-8 h-8 rounded-apple bg-apple-blue/10 text-apple-blue flex items-center justify-center">
                <Microscope :size="16" />
              </div>
              <div>
                <h3 class="text-sm font-bold text-apple-text">MiniFold 结果</h3>
                <p class="text-[10px] text-apple-secondary-text uppercase tracking-widest font-bold">
                  {{ store.targetChains || 'Auto' }} chains • {{ store.useAcceleration ? store.backend : 'cpu' }}
                </p>
              </div>
            </div>
            <div class="flex items-center gap-2">
              <button
                type="button"
                class="p-2 rounded-full hover:bg-black/5 dark:hover:bg-white/5 text-apple-secondary-text transition-colors"
                title="全屏查看"
                @click="showFullscreenViewer = true"
              >
                <Maximize2 :size="14" />
              </button>
              <button
                type="button"
                class="flex items-center gap-2 px-4 py-2 rounded-apple bg-apple-blue text-white text-[10px] font-bold uppercase tracking-widest hover:bg-apple-blue/90 transition-all"
                @click="downloadStructure"
              >
                <Download :size="14" />
                下载 PDB
              </button>
            </div>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-4 min-h-[640px]">
            <div class="md:col-span-3 bg-black/5 dark:bg-white/5 border-r border-apple-border relative group/viewer p-6">
              <StructureViewer
                :url="store.viewerUrl"
                source-db="LOCAL"
                :format="store.viewerFormat"
                class="w-full h-full"
              />

              <div class="absolute top-10 left-10 flex flex-col gap-2">
                <div class="px-3 py-1.5 rounded-apple bg-white/90 dark:bg-black/50 backdrop-blur shadow-sm border border-apple-border text-[10px] font-bold text-apple-text">
                  {{ selectedStructureStatus }}
                </div>
              </div>

              <div class="absolute bottom-10 left-10 right-10 flex gap-2 overflow-x-auto pb-2 no-scrollbar opacity-0 group-hover/viewer:opacity-100 transition-opacity">
                <div class="px-3 py-1.5 rounded-full bg-white/80 dark:bg-black/80 backdrop-blur shadow-sm border border-apple-border text-[10px] font-bold text-apple-text whitespace-nowrap">
                  ID: {{ selectedStructureId }}
                </div>
                <div class="px-3 py-1.5 rounded-full bg-apple-blue text-white shadow-sm text-[10px] font-bold whitespace-nowrap">
                  Runtime PDB
                </div>
              </div>
            </div>

            <div class="p-6 space-y-6 bg-apple-background/10">
              <div class="space-y-2">
                <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest">执行概况</label>
                <div class="rounded-apple border border-apple-border bg-white/50 dark:bg-white/5 p-3 text-[11px] text-apple-secondary-text leading-relaxed">
                  <p>Python: {{ condaEnvLabel }}</p>
                  <p>后端: {{ store.useAcceleration ? backendLabel : 'CPU' }}</p>
                  <p>加速: {{ store.useAcceleration ? '启用' : '关闭' }}</p>
                  <p>链数: {{ store.targetChains || '自动' }}</p>
                  <p class="mt-2">环境描述: {{ envLength ? `${envLength} 字` : '未填写' }}</p>
                </div>
              </div>

              <div class="space-y-3">
                <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest">确认入库</label>
                <div class="rounded-apple border border-apple-border bg-white/50 dark:bg-white/5 p-4 space-y-3">
                  <input
                    v-model="libraryEntryName"
                    type="text"
                    class="apple-input text-xs"
                    placeholder="例如：线粒体酶候选体 A"
                  />
                  <p class="text-[11px] leading-relaxed text-apple-secondary-text">
                    只有在你确认并命名后，这次 MiniFold 结果才会进入“预测成果库”，不会和 accession 导入条目混在一起。
                  </p>
                  <p v-if="saveToLibraryError" class="text-[11px] font-bold text-red-500">{{ saveToLibraryError }}</p>
                  <div v-if="savedLibraryEntry" class="rounded-apple border border-emerald-500/20 bg-emerald-500/5 px-3 py-2 text-[11px] text-emerald-700 dark:text-emerald-300">
                    已入库为「{{ savedLibraryEntry.proteinName }}」，编号 {{ savedLibraryEntry.code }}。
                  </div>
                  <div class="flex flex-wrap gap-2">
                    <button
                      type="button"
                      class="apple-button-primary !py-2 !px-4 text-xs flex items-center gap-2 disabled:opacity-50"
                      :disabled="isSavingToLibrary || !store.lastStructureText"
                      @click="handleSaveToLibrary"
                    >
                      <Loader2 v-if="isSavingToLibrary" :size="14" class="animate-spin" />
                      <FolderPlus v-else :size="14" />
                      确认入库
                    </button>
                    <button
                      v-if="savedLibraryEntry"
                      type="button"
                      class="apple-button-secondary !py-2 !px-4 text-xs"
                      @click="router.push({ path: '/library/predicted', query: { enzymeId: String(savedLibraryEntry.id) } })"
                    >
                      前往预测成果库
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>

        <template v-else-if="store.status === 'running'">
          <div class="h-full min-h-[720px] flex flex-col items-center justify-center text-center gap-4 p-12">
            <Loader2 class="animate-spin text-apple-blue" :size="36" />
            <div class="space-y-2">
              <h3 class="text-lg font-bold text-apple-text">MiniFold 推理中</h3>
              <p class="text-sm text-apple-secondary-text">正在等待结构结果返回，完成后会自动切换到三维视图。</p>
              <p class="text-[11px] text-apple-secondary-text">当前任务：{{ selectedStructureId }}</p>
            </div>
          </div>
        </template>

        <template v-else-if="store.status === 'error'">
          <div class="h-full min-h-[720px] flex flex-col items-center justify-center text-center gap-4 p-12">
            <div class="w-16 h-16 rounded-full bg-red-500/10 text-red-500 flex items-center justify-center">
              <Microscope :size="28" />
            </div>
            <div class="space-y-2">
              <h3 class="text-lg font-bold text-apple-text">MiniFold 任务失败</h3>
              <p class="text-sm text-apple-secondary-text max-w-md mx-auto">
                {{ store.error || '发生了未知错误。' }}
              </p>
            </div>
          </div>
        </template>

        <template v-else>
          <div class="h-full min-h-[720px] flex flex-col items-center justify-center text-center gap-4 p-12">
            <div class="w-16 h-16 rounded-full bg-apple-blue/10 text-apple-blue flex items-center justify-center">
              <Microscope :size="28" />
            </div>
            <div class="space-y-2">
              <h3 class="text-lg font-bold text-apple-text">等待返回结构</h3>
              <p class="text-sm text-apple-secondary-text max-w-md mx-auto">
                完成上方 3 步配置后开始推理，这里会显示最终结构结果与执行概况。
              </p>
            </div>
          </div>
        </template>
      </div>
      </div>
    </div>

    <transition name="fade">
      <div v-if="showFullscreenViewer && store.viewerUrl && store.status === 'success'" class="fixed inset-0 z-[100] bg-black/80 backdrop-blur-md flex flex-col">
        <div class="h-16 px-8 flex items-center justify-between border-b border-white/10">
          <div class="flex items-center gap-4">
            <h3 class="text-white font-bold">MiniFold 结果</h3>
            <span class="px-2 py-0.5 rounded-full bg-apple-blue text-white text-[10px] font-bold uppercase tracking-widest">
              {{ selectedStructureId }}
            </span>
          </div>
          <button
            type="button"
            class="w-10 h-10 rounded-full bg-white/10 text-white flex items-center justify-center hover:bg-white/20 transition-all"
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
.runtime-monitor-screen {
  --wave-color: #60a5fa;
  position: relative;
  height: 172px;
  overflow: hidden;
  border-radius: 24px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background:
    radial-gradient(circle at top, rgba(34, 211, 238, 0.08), transparent 45%),
    linear-gradient(180deg, rgba(6, 18, 36, 0.96), rgba(9, 22, 41, 0.88));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.05),
    0 24px 48px rgba(15, 23, 42, 0.18);
}

.runtime-monitor-screen.is-running {
  --wave-color: #5eead4;
}

.runtime-monitor-screen.is-success {
  --wave-color: #34d399;
}

.runtime-monitor-screen.is-error {
  --wave-color: #fb7185;
}

.runtime-monitor-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(125, 211, 252, 0.09) 1px, transparent 1px),
    linear-gradient(90deg, rgba(125, 211, 252, 0.09) 1px, transparent 1px);
  background-size: 40px 28px;
  opacity: 0.55;
}

.runtime-monitor-waveform {
  position: absolute;
  inset: 0;
  z-index: 1;
  height: 100%;
  width: 100%;
}

.runtime-monitor-baseline {
  fill: none;
  stroke: rgba(148, 163, 184, 0.35);
  stroke-width: 1.5;
}

.runtime-monitor-wave {
  fill: none;
  stroke: var(--wave-color);
  stroke-width: 3;
  stroke-linecap: round;
  stroke-linejoin: round;
  filter: drop-shadow(0 0 10px rgba(94, 234, 212, 0.35));
}

.runtime-monitor-screen.is-running .runtime-monitor-wave {
  animation: runtimeWaveGlow 1.6s ease-in-out infinite;
}

.runtime-monitor-sweep {
  position: absolute;
  top: -12%;
  bottom: -12%;
  left: -18%;
  z-index: 0;
  width: 24%;
  background: linear-gradient(90deg, transparent 0%, rgba(94, 234, 212, 0.03) 20%, rgba(94, 234, 212, 0.22) 52%, transparent 100%);
  filter: blur(10px);
  animation: runtimeSweep 2.8s linear infinite;
}

.runtime-monitor-readout {
  border-radius: 20px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(255, 255, 255, 0.68);
  padding: 14px 16px;
  backdrop-filter: blur(16px);
}

.dark .runtime-monitor-readout {
  background: rgba(15, 23, 42, 0.4);
}

.runtime-monitor-readout-label {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: rgb(100 116 139);
}

.runtime-monitor-readout-value {
  margin-top: 6px;
  font-size: 1.125rem;
  font-weight: 700;
  color: rgb(15 23 42);
}

.dark .runtime-monitor-readout-value {
  color: rgb(241 245 249);
}

@keyframes runtimeSweep {
  from {
    transform: translateX(0);
  }
  to {
    transform: translateX(520%);
  }
}

@keyframes runtimeWaveGlow {
  0%,
  100% {
    opacity: 0.82;
  }
  50% {
    opacity: 1;
  }
}
</style>
