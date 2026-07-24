<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  Activity,
  AlertCircle,
  ArrowLeft,
  CheckCircle2,
  Dna,
  Download,
  FileText,
  Loader2,
  Microscope,
  Settings,
  Sparkles,
  Trash2,
} from 'lucide-vue-next'
import StructureViewer from '@/components/StructureViewer.vue'
import { usePredictionStore } from '@/stores/prediction'
import type { PredictionProvider, PredictionTask } from '@/types'

const props = defineProps<{
  provider: Extract<PredictionProvider, 'nvidia' | 'rnafold'>
}>()

const router = useRouter()
const store = usePredictionStore()

const providerMeta = computed(() => {
  if (props.provider === 'rnafold') {
    return {
      title: 'RNAfold 工作台',
      description: '专门处理单条 RNA 二级结构预测，聚焦序列输入、能量结果和 dot-bracket 返回。',
      providerLabel: 'RNAfold',
      inputLabel: '输入 RNA 序列',
      inputHint: '支持 Plain 或单条 FASTA，标题行会自动忽略。',
      inputPlaceholder: '>sample_rna\nGGGAAAUCC',
      example: '>sample_rna\nGGGAAAUCC',
      typeLabel: 'RNA',
      modeLabel: '单条',
      modelLabel: 'rnafold',
      needsApiKey: false,
      apiHint: '通过系统后端代理调用，无需额外填写 API Key。',
      resultLabel: '二级结构结果',
      summarySuffix: 'nt',
    }
  }

  return {
    title: 'NVIDIA ESMFold 工作台',
    description: '专门处理单条蛋白结构预测，聚焦模型、凭证、输入序列和三维结构结果。',
    providerLabel: 'NVIDIA ESMFold',
    inputLabel: '输入蛋白序列',
    inputHint: '支持 Plain 或单条 FASTA，标题行会自动忽略。',
    inputPlaceholder: '>sample_1\nMKTFFVLLLCTFTVQAAPDAGVTKTYLQDVGGKSTLQKQLAELNQGQKELAAKLEQKQK',
    example: '>sample_1\nMKTFFVLLLCTFTVQAAPDAGVTKTYLQDVGGKSTLQKQLAELNQGQKELAAKLEQKQK',
    typeLabel: 'protein',
    modeLabel: '单条',
    modelLabel: 'esmfold',
    needsApiKey: true,
    apiHint: '需要有效的 NVIDIA API Key，可选自定义 Base URL。',
    resultLabel: '三维结构结果',
    summarySuffix: 'aa',
  }
})

const providerTasks = computed(() => store.tasks.filter(task => task.provider === props.provider))
const pendingCount = computed(() => providerTasks.value.filter(task => task.status === 'running').length)
const currentTask = computed(() => store.activeTask?.provider === props.provider ? store.activeTask : null)
const hasInput = computed(() => store.sequence.trim().length > 0)
const normalizedInputLength = computed(() => {
  return store.sequence
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(line => line && !line.startsWith('>'))
    .join('')
    .replace(/\s+/g, '')
    .length
})
const readinessItems = computed(() => [
  {
    label: '固定引擎',
    done: true,
    hint: providerMeta.value.providerLabel,
  },
  {
    label: '访问凭证',
    done: !providerMeta.value.needsApiKey || !!store.apiKey.trim(),
    hint: providerMeta.value.needsApiKey
      ? (store.apiKey.trim() ? '已填写 API Key' : '需要 API Key')
      : '无需 API Key',
  },
  {
    label: '输入内容',
    done: hasInput.value,
    hint: normalizedInputLength.value ? `${normalizedInputLength.value} ${providerMeta.value.summarySuffix}` : '请填写序列',
  },
])
const summaryItems = computed(() => [
  {
    label: '当前引擎',
    value: providerMeta.value.providerLabel,
  },
  {
    label: '分子类型',
    value: providerMeta.value.typeLabel,
  },
  {
    label: '提交模式',
    value: providerMeta.value.modeLabel,
  },
  {
    label: '输入长度',
    value: normalizedInputLength.value ? `${normalizedInputLength.value} ${providerMeta.value.summarySuffix}` : '未填写',
  },
])
const statusMeta = computed(() => {
  if (currentTask.value?.status === 'success') {
    return {
      title: '结果已就绪',
      description: '当前任务已经完成，可以直接查看结构或下载结果。',
      chip: 'Success',
      chipClass: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-300',
    }
  }
  if (currentTask.value?.status === 'running') {
    return {
      title: '任务执行中',
      description: '任务已经提交，结果返回后会自动切换到结果区。',
      chip: 'Running',
      chipClass: 'bg-apple-blue/10 text-apple-blue',
    }
  }
  if (currentTask.value?.status === 'error' || store.error) {
    return {
      title: '等待修正后重试',
      description: currentTask.value?.error || store.error || '请检查输入内容和执行配置后重新提交。',
      chip: 'Error',
      chipClass: 'bg-red-500/10 text-red-500',
    }
  }
  return {
    title: '准备提交预测',
    description: '这条工作台只处理当前引擎，填写好输入内容后即可直接运行。',
    chip: 'Idle',
    chipClass: 'bg-apple-background text-apple-secondary-text',
  }
})
const activeTaskSummary = computed(() => {
  if (!currentTask.value) return '尚未提交'
  return `${currentTask.value.name} · ${currentTask.value.sequenceLength || 0} ${providerMeta.value.summarySuffix}`
})

function applyProviderDefaults() {
  store.provider = props.provider
  store.submitMode = 'single'
  store.moleculeType = props.provider === 'rnafold' ? 'RNA' : 'protein'
}

function ensureProviderTaskSelected(tasks: PredictionTask[]) {
  if (store.activeTask?.provider === props.provider) return
  if (tasks.length > 0) {
    store.selectTask(tasks[0].id)
  }
}

function fillExample() {
  store.sequence = providerMeta.value.example
  if (!store.name.trim()) {
    store.name = props.provider === 'rnafold' ? 'RNA sample' : 'Protein sample'
  }
}

function clearInput() {
  store.sequence = ''
}

function clearProviderTasks() {
  const remainingTasks = store.tasks.filter(task => task.provider !== props.provider)
  store.tasks = remainingTasks
  if (store.activeTask?.provider === props.provider) {
    store.activeTaskId = null
  }
}

async function handleSubmit() {
  applyProviderDefaults()
  await store.submit()
  ensureProviderTaskSelected(providerTasks.value)
}

function selectTask(id: string) {
  store.selectTask(id)
}

function downloadStructure() {
  const text = store.lastStructureText
  if (!text || !currentTask.value?.result) return
  const ext = store.viewerFormat === 'mmcif' ? 'cif' : 'pdb'
  const blob = new Blob([text], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `${currentTask.value.name || 'prediction'}.${ext}`
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

watch(providerTasks, tasks => {
  ensureProviderTaskSelected(tasks)
}, { deep: true, immediate: true })

onMounted(() => {
  applyProviderDefaults()
  store.error = null
  ensureProviderTaskSelected(providerTasks.value)
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
        返回预测入口
      </button>

      <div class="apple-card p-6 md:p-7 bg-gradient-to-br from-apple-blue/8 via-transparent to-purple-500/8 border-apple-blue/10">
        <div class="flex flex-col gap-6 lg:flex-row lg:items-start lg:justify-between">
          <div class="space-y-3">
            <div class="flex items-center gap-3">
              <div class="w-10 h-10 rounded-apple bg-apple-blue/10 text-apple-blue flex items-center justify-center">
                <Sparkles :size="18" />
              </div>
              <div>
                <h1 class="text-3xl font-bold tracking-tight text-apple-text">{{ providerMeta.title }}</h1>
                <p class="text-sm text-apple-secondary-text">{{ providerMeta.description }}</p>
              </div>
            </div>

            <div class="flex flex-wrap gap-2">
              <span class="inline-flex items-center gap-2 rounded-full px-3 py-1 text-[11px] font-bold" :class="statusMeta.chipClass">
                <Activity v-if="currentTask?.status === 'running'" class="animate-pulse" :size="12" />
                <CheckCircle2 v-else-if="currentTask?.status === 'success'" :size="12" />
                <AlertCircle v-else-if="currentTask?.status === 'error' || store.error" :size="12" />
                <Microscope v-else :size="12" />
                {{ statusMeta.title }}
              </span>
              <span class="inline-flex items-center rounded-full bg-apple-background px-3 py-1 text-[11px] font-bold text-apple-secondary-text">
                当前队列 {{ providerTasks.length }} 个任务 / {{ pendingCount }} 个运行中
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
        <div class="apple-card p-6">
          <div class="flex items-center gap-3 mb-6">
            <div class="w-8 h-8 rounded-apple bg-apple-blue/10 text-apple-blue flex items-center justify-center">
              <Settings :size="16" />
            </div>
            <div>
              <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">Step 1</p>
              <h3 class="text-sm font-bold text-apple-text">确认工作台配置</h3>
            </div>
          </div>

          <div class="grid gap-4 md:grid-cols-3">
            <div class="rounded-apple border border-apple-border bg-apple-background/35 px-4 py-3">
              <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">固定引擎</p>
              <p class="mt-1 text-sm font-semibold text-apple-text">{{ providerMeta.providerLabel }}</p>
            </div>
            <div class="rounded-apple border border-apple-border bg-apple-background/35 px-4 py-3">
              <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">分子类型</p>
              <p class="mt-1 text-sm font-semibold text-apple-text">{{ providerMeta.typeLabel }}</p>
            </div>
            <div class="rounded-apple border border-apple-border bg-apple-background/35 px-4 py-3">
              <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">模型版本</p>
              <p class="mt-1 text-sm font-semibold text-apple-text">{{ providerMeta.modelLabel }}</p>
            </div>
          </div>

          <div v-if="providerMeta.needsApiKey" class="mt-6 pt-6 border-t border-apple-border space-y-4">
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
                  placeholder="默认：https://health.api.nvidia.com"
                >
              </div>
            </div>
            <p class="text-[11px] text-apple-secondary-text">{{ providerMeta.apiHint }}</p>
          </div>

          <div v-else class="mt-6 pt-6 border-t border-apple-border">
            <div class="rounded-apple border border-apple-border bg-apple-background/35 p-4 text-[11px] leading-relaxed text-apple-secondary-text">
              {{ providerMeta.apiHint }}
            </div>
          </div>
        </div>

        <div class="apple-card p-6">
          <div class="flex items-center justify-between mb-6 gap-4">
            <div class="flex items-center gap-3">
              <div class="w-8 h-8 rounded-apple bg-apple-green/10 text-apple-green flex items-center justify-center">
                <Dna :size="16" />
              </div>
              <div>
                <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">Step 2</p>
                <h3 class="text-sm font-bold text-apple-text">{{ providerMeta.inputLabel }}</h3>
              </div>
            </div>
            <button
              type="button"
              class="px-3 py-1.5 rounded-apple border border-apple-border text-[11px] font-bold text-apple-secondary-text hover:text-apple-text hover:bg-apple-background transition-colors"
              @click="fillExample"
            >
              填入示例
            </button>
          </div>

          <div class="space-y-4">
            <div class="grid gap-4 md:grid-cols-3">
              <div class="rounded-apple border border-apple-border bg-apple-background/35 px-4 py-3">
                <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">输入长度</p>
                <p class="mt-1 text-lg font-bold text-apple-text">{{ normalizedInputLength }}</p>
              </div>
              <div class="rounded-apple border border-apple-border bg-apple-background/35 px-4 py-3">
                <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">输入状态</p>
                <p class="mt-1 text-sm font-semibold" :class="hasInput ? 'text-emerald-600 dark:text-emerald-300' : 'text-apple-secondary-text'">
                  {{ hasInput ? '可以提交' : '等待填写' }}
                </p>
              </div>
              <div class="rounded-apple border border-apple-border bg-apple-background/35 px-4 py-3">
                <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">格式要求</p>
                <p class="mt-1 text-sm font-semibold text-apple-text">{{ providerMeta.modeLabel }} {{ providerMeta.typeLabel }}</p>
              </div>
            </div>

            <div class="space-y-2">
              <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest ml-1">输入序列</label>
              <textarea
                v-model="store.sequence"
                class="apple-input min-h-[220px] text-xs font-mono leading-relaxed p-4 bg-apple-background/50 focus:bg-white dark:focus:bg-white/5"
                :placeholder="providerMeta.inputPlaceholder"
              />
              <p class="text-[11px] text-apple-secondary-text">{{ providerMeta.inputHint }}</p>
            </div>

            <div class="grid gap-4 md:grid-cols-[minmax(0,1fr)_auto_auto] md:items-center">
              <div>
                <input
                  v-model="store.name"
                  type="text"
                  class="apple-input text-xs"
                  placeholder="任务名称 (可选)..."
                >
              </div>
              <button
                type="button"
                class="px-4 py-2 rounded-apple border border-apple-border text-xs font-bold text-apple-secondary-text hover:text-apple-text hover:bg-apple-background transition-colors"
                @click="clearInput"
              >
                清空输入
              </button>
              <button
                type="button"
                class="apple-button-primary px-8 py-2.5 flex items-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
                :disabled="store.isSubmitting || !hasInput"
                @click="handleSubmit"
              >
                <template v-if="store.isSubmitting">
                  <Loader2 class="animate-spin" :size="16" />
                  <span>提交中...</span>
                </template>
                <template v-else>
                  <Microscope :size="16" />
                  <span>开始预测</span>
                </template>
              </button>
            </div>

            <p v-if="store.error" class="text-[11px] font-bold text-red-500">{{ store.error }}</p>
          </div>
        </div>

        <div class="apple-card overflow-hidden min-h-[620px]">
          <template v-if="currentTask?.status === 'success' && currentTask.result">
            <div class="p-6 border-b border-apple-border flex items-center justify-between bg-apple-background/30">
              <div class="flex items-center gap-3">
                <div class="w-8 h-8 rounded-apple bg-apple-blue/10 text-apple-blue flex items-center justify-center">
                  <Microscope :size="16" />
                </div>
                <div>
                  <h3 class="text-sm font-bold text-apple-text">{{ providerMeta.resultLabel }}</h3>
                  <p class="text-[10px] text-apple-secondary-text uppercase tracking-widest font-bold">
                    {{ currentTask.result.modelName }} • {{ currentTask.result.format }}
                  </p>
                </div>
              </div>
              <button
                type="button"
                class="flex items-center gap-2 px-4 py-2 rounded-apple bg-apple-blue text-white text-[10px] font-bold uppercase tracking-widest hover:bg-apple-blue/90 transition-all"
                @click="downloadStructure"
              >
                <Download :size="14" />
                下载结果
              </button>
            </div>

            <div class="grid grid-cols-1 md:grid-cols-4 min-h-[540px]">
              <div class="md:col-span-3 bg-black/5 dark:bg-white/5 border-r border-apple-border">
                <StructureViewer
                  v-if="store.viewerUrl"
                  :url="store.viewerUrl"
                  :format="store.viewerFormat === 'mmcif' ? 'mmcif' : 'pdb'"
                  class="w-full h-full"
                />
                <div v-else-if="store.viewerFormat === 'dot-bracket' && store.lastStructureText" class="h-full p-6 overflow-auto bg-apple-background/30">
                  <pre class="text-xs font-mono text-apple-text whitespace-pre-wrap">{{ store.lastStructureText }}</pre>
                </div>
                <div v-else class="flex flex-col items-center justify-center h-full text-apple-secondary-text gap-4">
                  <Loader2 class="animate-spin text-apple-blue" :size="32" />
                  <p class="text-xs font-medium">正在初始化查看器...</p>
                </div>
              </div>

              <div class="p-6 space-y-6 bg-apple-background/10">
                <div v-if="currentTask.result.plddt != null" class="space-y-2">
                  <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest">Confidence (pLDDT)</label>
                  <div class="flex items-end gap-2">
                    <span class="text-3xl font-bold text-apple-text tracking-tighter">{{ currentTask.result.plddt.toFixed(1) }}</span>
                    <span class="text-[10px] font-bold text-apple-secondary-text mb-1.5">%</span>
                  </div>
                </div>

                <div v-if="currentTask.result.ptm != null" class="space-y-2">
                  <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest">Global Score (pTM)</label>
                  <div class="text-xl font-bold text-apple-text">{{ currentTask.result.ptm.toFixed(3) }}</div>
                </div>

                <div v-if="currentTask.result.mfeEnergy != null" class="space-y-2">
                  <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest">MFE Energy</label>
                  <div class="text-xl font-bold text-apple-text">{{ currentTask.result.mfeEnergy.toFixed(2) }} <span class="text-[10px]">kcal/mol</span></div>
                </div>

                <div v-if="currentTask.result.analysis" class="space-y-2">
                  <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest">结果分析</label>
                  <div class="text-[11px] text-apple-secondary-text leading-relaxed bg-white/50 dark:bg-white/5 p-3 rounded-apple border border-apple-border italic">
                    "{{ currentTask.result.analysis }}"
                  </div>
                </div>
              </div>
            </div>
          </template>

          <template v-else-if="currentTask?.status === 'running'">
            <div class="h-full min-h-[620px] flex flex-col items-center justify-center text-center gap-4 p-12">
              <Loader2 class="animate-spin text-apple-blue" :size="36" />
              <div class="space-y-2">
                <h3 class="text-lg font-bold text-apple-text">任务执行中</h3>
                <p class="text-sm text-apple-secondary-text">正在等待结果返回，完成后会自动切换到结果视图。</p>
              </div>
            </div>
          </template>

          <template v-else-if="currentTask?.status === 'error'">
            <div class="h-full min-h-[620px] flex flex-col items-center justify-center text-center gap-4 p-12">
              <div class="w-16 h-16 rounded-full bg-red-500/10 text-red-500 flex items-center justify-center">
                <AlertCircle :size="28" />
              </div>
              <div class="space-y-2">
                <h3 class="text-lg font-bold text-apple-text">预测失败</h3>
                <p class="text-sm text-apple-secondary-text max-w-md mx-auto">
                  {{ currentTask.error || '发生了未知错误。' }}
                </p>
              </div>
            </div>
          </template>

          <template v-else>
            <div class="h-full min-h-[620px] flex flex-col items-center justify-center text-center gap-4 p-12">
              <div class="w-16 h-16 rounded-full bg-apple-blue/10 text-apple-blue flex items-center justify-center">
                <Microscope :size="28" />
              </div>
              <div class="space-y-2">
                <h3 class="text-lg font-bold text-apple-text">等待结果返回</h3>
                <p class="text-sm text-apple-secondary-text max-w-md mx-auto">
                  填写上方配置并提交任务后，这里会显示 {{ providerMeta.resultLabel }}。
                </p>
              </div>
            </div>
          </template>
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
              class="rounded-apple border border-apple-border bg-apple-background/35 px-4 py-3"
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
            <p><span class="font-bold text-apple-text">当前引擎：</span>{{ providerMeta.providerLabel }}</p>
            <p class="mt-1"><span class="font-bold text-apple-text">当前任务：</span>{{ activeTaskSummary }}</p>
            <p class="mt-1"><span class="font-bold text-apple-text">任务数：</span>{{ providerTasks.length }}</p>
          </div>
        </div>

        <div class="apple-card p-6">
          <div class="flex items-center justify-between mb-6">
            <div>
              <h3 class="text-sm font-bold text-apple-text">任务队列</h3>
              <p class="text-[11px] text-apple-secondary-text">这里仅展示当前工作台的历史任务。</p>
            </div>
            <div class="flex items-center gap-2">
              <span class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest">{{ pendingCount }} 运行中</span>
              <button
                v-if="providerTasks.length > 0"
                type="button"
                class="p-1 hover:bg-apple-background rounded text-apple-secondary-text hover:text-red-500 transition-colors"
                title="清空当前工作台历史"
                @click="clearProviderTasks"
              >
                <Trash2 :size="12" />
              </button>
            </div>
          </div>

          <div class="space-y-4">
            <button
              v-for="task in providerTasks.slice(0, 15)"
              :key="task.id"
              type="button"
              class="p-3 rounded-apple bg-apple-background dark:bg-white/5 border border-apple-border flex items-center justify-between w-full text-left hover:border-apple-blue/20 transition-all"
              :class="currentTask?.id === task.id ? 'border-apple-blue/40' : ''"
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
                    {{ task.sequenceLength || 0 }} {{ providerMeta.summarySuffix }} • {{ task.provider }}
                  </p>
                </div>
              </div>
              <span class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-tighter">
                {{ task.status === 'running' ? 'Running' : task.status === 'success' ? 'Done' : 'Error' }}
              </span>
            </button>

            <div v-if="providerTasks.length === 0" class="text-[10px] text-apple-secondary-text font-bold text-center py-8">
              暂无任务，请先提交一次预测
            </div>
          </div>
        </div>

        <div class="apple-card p-6 bg-gradient-to-br from-purple-500/5 to-transparent border-purple-500/10">
          <div class="flex items-center gap-3 mb-4">
            <div class="w-8 h-8 rounded-apple bg-purple-500/10 text-purple-500 flex items-center justify-center">
              <FileText :size="16" />
            </div>
            <h3 class="text-sm font-bold text-apple-text">工作台说明</h3>
          </div>
          <p class="text-xs text-apple-secondary-text leading-relaxed">
            这个页面只负责 {{ providerMeta.providerLabel }} 的预测流程。MiniFold 和另一套云端引擎都已拆成独立页面，便于保持输入规则和结果展示一致。
          </p>
        </div>
      </div>
    </div>
  </div>
</template>
