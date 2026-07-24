<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowLeft,
  Cpu,
  Download,
  Dna,
  Loader2,
  Maximize2,
  Microscope,
  Play,
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
    <div class="space-y-2">
      <button
        type="button"
        class="inline-flex items-center gap-2 text-xs font-bold text-apple-secondary-text hover:text-apple-text transition-colors"
        @click="router.push('/prediction')"
      >
        <ArrowLeft :size="14" />
        返回预测中心
      </button>
      <div>
        <h1 class="text-3xl font-bold tracking-tight text-apple-text">MiniFold 工作台</h1>
        <p class="text-sm text-apple-secondary-text">只保留序列、环境描述、链条数量、加速配置与结构结果返回。</p>
      </div>
    </div>

    <div class="grid grid-cols-1 xl:grid-cols-2 gap-8 items-start">
      <div class="space-y-8">
        <div class="apple-card p-6 space-y-6">
          <div class="flex items-center gap-3">
            <div class="w-8 h-8 rounded-apple bg-apple-green/10 text-apple-green flex items-center justify-center">
              <Dna :size="16" />
            </div>
            <div>
              <h3 class="text-sm font-bold text-apple-text">输入与约束</h3>
              <p class="text-[11px] text-apple-secondary-text">支持 Plain 或单条 FASTA，标题行会自动忽略。</p>
            </div>
          </div>

          <div class="space-y-2">
            <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest ml-1">输入序列</label>
            <textarea
              v-model="store.sequence"
              class="apple-input min-h-[180px] text-xs font-mono leading-relaxed p-4 bg-apple-background/50 focus:bg-white dark:focus:bg-white/5"
              placeholder=">sample_1&#10;MKTFFVLLLCTFTVQAAPDAGVTKTYLQDVGGKSTLQKQLAELNQGQKELAAKLEQKQK"
            />
          </div>

          <div class="space-y-2">
            <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest ml-1">环境描述</label>
            <textarea
              v-model="store.envText"
              class="apple-input min-h-[110px] text-xs leading-relaxed p-4"
              placeholder="例如：线粒体内膜相关酶，倾向形成稳定跨膜区段，避免过度暴露疏水残基。"
            />
          </div>

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
        </div>

        <div class="apple-card p-6 space-y-6">
          <div class="flex items-center gap-3">
            <div class="w-8 h-8 rounded-apple bg-purple-500/10 text-purple-500 flex items-center justify-center">
              <Cpu :size="16" />
            </div>
            <div>
              <h3 class="text-sm font-bold text-apple-text">加速配置</h3>
              <p class="text-[11px] text-apple-secondary-text">只保留是否启用加速与后端类型。</p>
            </div>
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
              {{ backendOptions.find(item => item.value === store.backend)?.hint }}
            </p>
          </div>

          <div class="flex items-center justify-between gap-4 pt-2">
            <p v-if="store.error" class="text-[11px] font-bold text-red-500">{{ store.error }}</p>
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
                  {{ store.targetChains || 'Auto' }} chains • {{ store.backend }}
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
                  <p>后端: {{ store.backend }}</p>
                  <p>加速: {{ store.useAcceleration ? '启用' : '关闭' }}</p>
                  <p>链数: {{ store.targetChains || '自动' }}</p>
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
              <p class="text-sm text-apple-secondary-text">正在等待结构结果返回。</p>
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
                填写左侧四项配置并开始推理，这里只显示最终结构结果。
              </p>
            </div>
          </div>
        </template>
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
