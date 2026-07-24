<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  Activity,
  AlertCircle,
  ArrowLeft,
  CheckCircle2,
  Cpu,
  Download,
  Dna,
  FileText,
  Layers3,
  Loader2,
  Maximize2,
  Microscope,
  Play,
  Sparkles,
} from 'lucide-vue-next'
import StructureViewer from '@/components/StructureViewer.vue'
import { useMiniFoldStore } from '@/stores/minifold'

const router = useRouter()
const store = useMiniFoldStore()
const showFullscreenViewer = ref(false)

let resultInterval: ReturnType<typeof setInterval> | null = null

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

const selectedStructureId = computed(() => store.engineTaskId || 'LOCAL-PDB')
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
      title: '任务执行失败',
      description: store.error || '请检查输入格式、环境约束或本机后端状态后重试。',
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

function fillExample() {
  store.sequence = `>sample_1
MKTFFVLLLCTFTVQAAPDAGVTKTYLQDVGGKSTLQKQLAELNQGQKELAAKLEQKQK`
  if (!store.envText.trim()) {
    store.envText = '线粒体相关酶，倾向形成稳定紧凑构象，尽量避免疏水核心过度暴露。'
  }
}

function stopPolling() {
  if (resultInterval) {
    clearInterval(resultInterval)
    resultInterval = null
  }
}

function startPolling() {
  if (!store.engineTaskId) return
  stopPolling()
  resultInterval = setInterval(async () => {
    const finished = await store.fetchResult()
    if (finished) stopPolling()
  }, 3000)
}

async function handleSubmit() {
  const started = await store.submit()
  if (started && store.engineTaskId && store.status === 'running') {
    startPolling()
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

watch(() => store.engineTaskId, (newId) => {
  if (newId && store.status === 'running') {
    startPolling()
  } else {
    stopPolling()
  }
})

onMounted(() => {
  if (store.engineTaskId && store.status === 'running') {
    startPolling()
  }
})

onUnmounted(() => {
  stopPolling()
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

    <div class="grid grid-cols-1 xl:grid-cols-3 gap-8 items-start">
      <div class="space-y-8 xl:col-span-2">
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
              <p class="text-[11px] text-apple-secondary-text">先指定 Python / Conda 环境，再决定是否加速和后端，最后从摘要确认这次任务会怎么跑。</p>
            </div>
          </div>

          <div class="space-y-2">
            <div class="flex items-center justify-between">
              <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest ml-1">Conda 环境名</label>
              <span class="text-[10px] font-bold text-apple-secondary-text">{{ store.condaEnvName.trim() ? '用户指定' : '自动发现' }}</span>
            </div>
            <input
              v-model="store.condaEnvName"
              type="text"
              class="apple-input text-xs"
              placeholder="例如：minifold 或 my-torch-env"
            />
            <p class="text-[11px] text-apple-secondary-text">留空时将继续使用 `MINIFOLD_PYTHON` 或系统 Python；填写后会优先执行 `conda run -n 环境名 python`。</p>
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

      <div class="space-y-8 xl:sticky xl:top-6">
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
                左侧完成 3 步配置后开始推理，这里会显示最终结构结果与执行概况。
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
