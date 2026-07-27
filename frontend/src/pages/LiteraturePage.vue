<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  Activity,
  CheckCircle2,
  Database,
  Download,
  ExternalLink,
  FileText,
  Loader2,
  Search,
  Sparkles,
} from 'lucide-vue-next'
import { useRoute } from 'vue-router'
import NcbiCredentialsForm from '@/components/NcbiCredentialsForm.vue'
import { useLiterature } from '@/composables/useLiterature'
import { useNcbiImport } from '@/composables/useNcbiImport'

const route = useRoute()
const {
  literatures,
  scanLoading,
  scanStatus,
  downloadingRelationIds,
  ncbiEmail,
  ncbiApiKey,
  fetchAllLiteratures,
  fetchScanStatus,
  scan,
  downloadLiterature,
} = useLiterature()
const { enzymes, refreshEnzymeLibrary } = useNcbiImport()

const scanScope = ref<'all' | 'selected'>('all')
const selectedEnzymeIds = ref<number[]>([])
const clockNow = ref(Date.now())
let pollInterval: number | null = null
let clockInterval: number | null = null
let pollingGraceUntil = 0

const confidenceConfig = {
  STRONG: { bg: 'bg-apple-green/10 text-apple-green', label: '强关联' },
  WEAK: { bg: 'bg-amber-500/10 text-amber-500', label: '弱关联' },
  CANDIDATE: { bg: 'bg-apple-blue/10 text-apple-blue', label: '候选' },
}

const selectedCount = computed(() => selectedEnzymeIds.value.length)
const isScanRunning = computed(() => scanStatus.value?.status === 'RUNNING')
const progressPercentage = computed(() => {
  const total = scanStatus.value?.totalEnzymes || 0
  if (!total) return isScanRunning.value ? 12 : 0
  return Math.min(100, Math.max(Math.round(((scanStatus.value?.processedEnzymes || 0) / total) * 100), isScanRunning.value ? 12 : 0))
})
const elapsedSeconds = computed(() => {
  if (!scanStatus.value?.startedAt) return 0
  const started = new Date(scanStatus.value.startedAt).getTime()
  const ended = scanStatus.value.finishedAt
    ? new Date(scanStatus.value.finishedAt).getTime()
    : clockNow.value
  return Math.max(0, Math.round((ended - started) / 1000))
})
const statusBadge = computed(() => {
  switch (scanStatus.value?.status) {
    case 'RUNNING':
      return { text: '运行中', className: 'bg-apple-blue/10 text-apple-blue' }
    case 'COMPLETED':
      return { text: '已完成', className: 'bg-apple-green/10 text-apple-green' }
    case 'FAILED':
      return { text: '异常结束', className: 'bg-red-500/10 text-red-500' }
    default:
      return { text: '待启动', className: 'apple-soft-strip text-apple-secondary-text' }
  }
})
const liveHint = computed(() => {
  if (!scanStatus.value) return ''
  if (isScanRunning.value) {
    return `已处理 ${scanStatus.value.processedEnzymes} / ${scanStatus.value.totalEnzymes} 个酶，累计发现 ${scanStatus.value.discoveredCandidates} 条候选文献`
  }
  return scanStatus.value.message
})

const orderedLiteratures = computed(() =>
  [...literatures.value].sort((a, b) => Number(Boolean(b.savedToLibrary)) - Number(Boolean(a.savedToLibrary))),
)

const formatDuration = (seconds: number) => {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${mins}分 ${secs.toString().padStart(2, '0')}秒`
}

const formatTimestamp = (value?: string) => {
  if (!value) return '等待心跳'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '等待心跳'
  return date.toLocaleTimeString('zh-CN', { hour12: false })
}

const toggleEnzyme = (enzymeId: number) => {
  selectedEnzymeIds.value = selectedEnzymeIds.value.includes(enzymeId)
    ? selectedEnzymeIds.value.filter((id) => id !== enzymeId)
    : [...selectedEnzymeIds.value, enzymeId]
}

const selectAllEnzymes = () => {
  selectedEnzymeIds.value = enzymes.value.map((enzyme) => enzyme.id)
}

const clearSelectedEnzymes = () => {
  selectedEnzymeIds.value = []
}

const startClock = () => {
  if (clockInterval) return
  clockInterval = window.setInterval(() => {
    clockNow.value = Date.now()
  }, 1000)
}

const stopClock = () => {
  if (clockInterval) {
    clearInterval(clockInterval)
    clockInterval = null
  }
}

const pollSnapshot = async () => {
  await Promise.all([fetchScanStatus(), fetchAllLiteratures()])
  clockNow.value = Date.now()
  if (isScanRunning.value) {
    startClock()
    return
  }
  if (Date.now() >= pollingGraceUntil && !scanLoading.value) {
    stopPolling()
  }
}

const startPolling = (graceMs = 0) => {
  pollingGraceUntil = Math.max(pollingGraceUntil, Date.now() + graceMs)
  if (pollInterval) return
  pollSnapshot()
  pollInterval = window.setInterval(() => {
    pollSnapshot()
  }, 2500)
}

const stopPolling = () => {
  if (pollInterval) {
    clearInterval(pollInterval)
    pollInterval = null
  }
  stopClock()
}

const handleScan = async () => {
  if (scanScope.value === 'selected' && !selectedEnzymeIds.value.length) {
    window.alert('请先选择要扫描的酶条目')
    return
  }
  startPolling(10000)
  await scan(scanScope.value === 'selected' ? selectedEnzymeIds.value : undefined)
  await pollSnapshot()
  if (isScanRunning.value) {
    startPolling(120000)
  }
}

const handleDownload = async (relationId?: number) => {
  if (!relationId) return
  await downloadLiterature(relationId)
}

onMounted(async () => {
  await Promise.all([fetchAllLiteratures(), fetchScanStatus(), refreshEnzymeLibrary()])
  const enzymeId = Number(route.query.enzymeId)
  if (enzymeId) {
    scanScope.value = 'selected'
    selectedEnzymeIds.value = [enzymeId]
  }
  if (scanStatus.value?.status === 'RUNNING') {
    startPolling(120000)
  }
})

onBeforeUnmount(() => {
  stopPolling()
})
</script>

<template>
  <div class="max-w-6xl mx-auto space-y-8 pb-20">
    <div class="apple-card p-10 bg-[linear-gradient(180deg,rgba(7,11,23,0.42),rgba(7,11,23,0.16))]">
      <div class="flex flex-col md:flex-row items-center justify-between gap-8">
        <div class="space-y-4 text-center md:text-left">
          <div class="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-apple-blue/10 text-apple-blue text-[10px] font-bold uppercase tracking-widest">
            <Sparkles :size="12" />
            AI 证据发现引擎
          </div>
          <h1 class="text-4xl font-bold tracking-tight text-apple-text">文献一键关联</h1>
          <p class="text-apple-secondary-text text-sm max-w-md">
            先扫描候选文献，再按条下载入库。只有已下载的文献，才会显示在酶库中心的关联文献板块。
          </p>
        </div>
        
        <div class="flex flex-col gap-4 w-full md:w-auto">
          <button 
            @click="handleScan"
            :disabled="scanLoading || isScanRunning"
            class="apple-button-primary !py-4 !px-8 flex items-center justify-center gap-3 text-sm shadow-apple-blue disabled:opacity-50"
          >
            <Loader2 v-if="scanLoading || isScanRunning" :size="20" class="animate-spin" />
            <Search v-else :size="20" />
            {{ scanLoading ? '正在提交扫描任务...' : isScanRunning ? 'PubMed 扫描进行中...' : scanScope === 'all' ? '开始全库扫描' : '开始部分扫描' }}
          </button>
          
          <div class="flex items-center justify-center md:justify-start gap-2 text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest">
            <Database :size="12" />
            {{ ncbiApiKey ? 'API KEY 已激活 (10次/秒)' : '匿名模式 (3次/秒)' }}
          </div>
          <p v-if="scanLoading || isScanRunning" class="text-xs text-apple-secondary-text text-center md:text-left">
            {{ liveHint }}
          </p>
        </div>
      </div>
    </div>

    <div class="grid grid-cols-1 gap-8">
      <div class="apple-card p-6">
        <NcbiCredentialsForm 
          v-model:email="ncbiEmail"
          v-model:api-key="ncbiApiKey"
        />
      </div>

      <div class="apple-card p-6 space-y-5">
        <div class="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4">
          <div>
            <h2 class="text-lg font-bold text-apple-text">扫描范围</h2>
            <p class="text-xs text-apple-secondary-text mt-1">你可以扫描全库，也可以只扫描库中的部分酶。</p>
          </div>
          <div class="flex flex-wrap gap-3">
            <button
              @click="scanScope = 'all'"
              class="apple-button-secondary !py-2 !px-4 text-xs"
              :class="scanScope === 'all' ? '!bg-apple-blue !text-white !border-apple-blue' : ''"
            >
              全库扫描
            </button>
            <button
              @click="scanScope = 'selected'"
              class="apple-button-secondary !py-2 !px-4 text-xs"
              :class="scanScope === 'selected' ? '!bg-apple-blue !text-white !border-apple-blue' : ''"
            >
              部分酶扫描
            </button>
          </div>
        </div>

        <div v-if="scanScope === 'selected'" class="space-y-4">
          <div class="flex flex-wrap items-center gap-3">
            <span class="text-xs text-apple-secondary-text">已选择 {{ selectedCount }} / {{ enzymes.length }} 个酶条目</span>
            <button @click="selectAllEnzymes" class="text-xs font-semibold text-apple-blue hover:underline">全选</button>
            <button @click="clearSelectedEnzymes" class="text-xs font-semibold text-apple-secondary-text hover:underline">清空</button>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3 max-h-80 overflow-y-auto pr-1">
            <button
              v-for="enzyme in enzymes"
              :key="enzyme.id"
              @click="toggleEnzyme(enzyme.id)"
              class="text-left p-4 rounded-apple transition-all"
              :class="selectedEnzymeIds.includes(enzyme.id) ? 'bg-apple-blue/[0.08] shadow-[inset_0_0_0_1px_rgba(56,189,248,0.16),0_16px_36px_-30px_rgba(56,189,248,0.2)]' : 'apple-soft-panel'"
            >
              <div class="flex items-center justify-between gap-2">
                <span class="text-xs font-bold text-apple-blue">{{ enzyme.accession }}</span>
                <CheckCircle2 v-if="selectedEnzymeIds.includes(enzyme.id)" :size="14" class="text-apple-blue" />
              </div>
              <p class="mt-2 text-sm font-semibold text-apple-text line-clamp-2">{{ enzyme.proteinName }}</p>
              <p class="mt-1 text-[11px] text-apple-secondary-text truncate">{{ enzyme.organismName }}</p>
            </button>
          </div>
        </div>
      </div>

      <div
        v-if="scanStatus && (scanStatus.status !== 'IDLE' || scanLoading)"
        class="apple-card p-6 bg-[linear-gradient(180deg,rgba(7,11,23,0.42),rgba(7,11,23,0.18))]"
      >
        <div class="flex flex-col lg:flex-row lg:items-start lg:justify-between gap-4">
          <div class="space-y-2">
            <div class="flex items-center gap-3">
              <div class="w-10 h-10 rounded-apple bg-apple-blue/10 text-apple-blue flex items-center justify-center">
                <Activity :size="18" :class="isScanRunning ? 'animate-pulse' : ''" />
              </div>
              <div>
                <h2 class="text-lg font-bold text-apple-text">运行观测台</h2>
                <p class="text-xs text-apple-secondary-text">让文献关联过程不再“黑箱”执行。</p>
              </div>
            </div>
            <p class="text-sm text-apple-text font-semibold">
              {{ scanStatus.message }}
            </p>
          </div>

          <div class="flex items-center gap-2">
            <span class="px-3 py-1 rounded-full text-[10px] font-bold uppercase tracking-widest" :class="statusBadge.className">
              {{ statusBadge.text }}
            </span>
            <span class="apple-soft-strip px-3 py-1 rounded-full text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">
              {{ scanStatus.scope === 'PARTIAL' ? '部分扫描' : '全库扫描' }}
            </span>
          </div>
        </div>

        <div class="mt-6 grid grid-cols-2 xl:grid-cols-4 gap-4">
          <div class="apple-soft-panel rounded-apple p-4">
            <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">扫描进度</p>
            <p class="mt-2 text-2xl font-bold text-apple-text">{{ scanStatus.processedEnzymes }} / {{ scanStatus.totalEnzymes }}</p>
          </div>
          <div class="apple-soft-panel rounded-apple p-4">
            <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">候选文献</p>
            <p class="mt-2 text-2xl font-bold text-apple-blue">{{ scanStatus.discoveredCandidates }}</p>
          </div>
          <div class="apple-soft-panel rounded-apple p-4">
            <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">已运行</p>
            <p class="mt-2 text-2xl font-bold text-apple-text">{{ formatDuration(elapsedSeconds) }}</p>
          </div>
          <div class="apple-soft-panel rounded-apple p-4">
            <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">异常条目</p>
            <p class="mt-2 text-2xl font-bold" :class="scanStatus.failedEnzymes ? 'text-red-500' : 'text-apple-text'">{{ scanStatus.failedEnzymes }}</p>
          </div>
        </div>

        <div class="mt-5">
          <div class="flex items-center justify-between text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text mb-2">
            <span>实时进度条</span>
            <span>{{ progressPercentage }}%</span>
          </div>
          <div class="h-2 rounded-full bg-apple-blue/8 overflow-hidden">
            <div
              class="h-full rounded-full bg-gradient-to-r from-apple-blue to-purple-500 transition-all duration-700"
              :class="isScanRunning ? 'shadow-[0_0_14px_rgba(0,113,227,0.35)]' : ''"
              :style="{ width: `${progressPercentage}%` }"
            ></div>
          </div>
        </div>

        <div class="mt-5 grid grid-cols-1 lg:grid-cols-3 gap-4 text-xs">
          <div class="apple-soft-panel rounded-apple p-4">
            <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">当前目标</p>
            <p class="mt-2 font-semibold text-apple-text">{{ scanStatus.currentAccession || '等待分配任务' }}</p>
            <p class="mt-1 text-apple-secondary-text line-clamp-2">{{ scanStatus.currentEnzymeName || '扫描启动后会在这里显示当前酶条目。' }}</p>
          </div>
          <div class="apple-soft-panel rounded-apple p-4">
            <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">扫描模式</p>
            <p class="mt-2 font-semibold text-apple-text">{{ scanStatus.apiKeyEnabled ? 'API Key 加速模式' : '匿名模式' }}</p>
            <p class="mt-1 text-apple-secondary-text">{{ scanStatus.apiKeyEnabled ? '更高请求频率，反馈更快。' : '限速较低，但仍会持续产出候选。' }}</p>
          </div>
          <div class="apple-soft-panel rounded-apple p-4">
            <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">最近心跳</p>
            <p class="mt-2 font-semibold text-apple-text">{{ formatTimestamp(scanStatus.lastHeartbeatAt) }}</p>
            <p class="mt-1 text-apple-secondary-text">{{ scanStatus.finishedAt ? '本轮扫描已落盘完成。' : '页面会持续轮询，直到扫描结束。' }}</p>
          </div>
        </div>
      </div>

      <div class="space-y-6">
        <div class="flex items-center justify-between px-2">
          <h2 class="text-lg font-bold text-apple-text flex items-center gap-2">
            <FileText :size="18" class="text-apple-blue" />
            匹配结果
            <span v-if="orderedLiteratures.length" class="text-xs font-normal text-apple-secondary-text ml-2">
              已发现 {{ orderedLiteratures.length }} 条候选
            </span>
          </h2>
        </div>

        <div v-if="(scanLoading || isScanRunning) && !literatures.length" class="apple-card p-20 flex flex-col items-center justify-center">
          <Loader2 :size="40" class="animate-spin text-apple-blue mb-4" />
          <p class="text-sm font-bold text-apple-text">正在深挖 PubMed 数据库...</p>
        </div>

        <template v-else-if="orderedLiteratures.length">
          <div class="grid grid-cols-1 gap-4">
            <div
              v-for="item in orderedLiteratures"
              :key="item.relationId || `${item.id}-${item.matchedEnzymeAccession}`"
              class="apple-card p-6 group transition-all flex gap-6 hover:shadow-[0_24px_58px_-44px_rgba(56,189,248,0.18)]"
            >
              <div class="hidden md:flex flex-col items-center justify-center w-20 h-20 rounded-apple bg-apple-background/38 shadow-[inset_0_1px_0_rgba(255,255,255,0.02)] shrink-0">
                <span class="text-[10px] font-bold text-apple-secondary-text uppercase">得分</span>
                <span class="text-2xl font-bold text-apple-blue">{{ item.confidenceScore || 0 }}</span>
              </div>

              <div class="flex-1 space-y-3">
                <div class="flex items-center justify-between">
                  <div class="flex items-center gap-3">
                    <div 
                      class="px-2 py-0.5 rounded-full text-[10px] font-bold uppercase"
                      :class="confidenceConfig[item.confidenceLevel as keyof typeof confidenceConfig]?.bg || 'bg-black/5 text-apple-secondary-text'"
                    >
                      {{ confidenceConfig[item.confidenceLevel as keyof typeof confidenceConfig]?.label || '待验证' }}
                    </div>
                    <div
                      class="px-2 py-0.5 rounded-full text-[10px] font-bold uppercase"
                      :class="item.savedToLibrary ? 'bg-apple-green/10 text-apple-green' : 'bg-black/5 text-apple-secondary-text'"
                    >
                      {{ item.savedToLibrary ? '已入库' : '未入库' }}
                    </div>
                        <div
                          v-if="item.savedToLibrary"
                          class="px-2 py-0.5 rounded-full text-[10px] font-bold uppercase"
                          :class="item.attachmentStatus === 'DOWNLOADED' ? 'bg-apple-green/10 text-apple-green' : item.attachmentStatus === 'NOT_OPEN_ACCESS' ? 'bg-amber-500/10 text-amber-500' : item.attachmentStatus === 'FAILED' ? 'bg-red-500/10 text-red-500' : 'bg-black/5 text-apple-secondary-text'"
                        >
                          {{ item.attachmentStatus === 'DOWNLOADED' ? '全文附件已入库' : item.attachmentStatus === 'NOT_OPEN_ACCESS' ? '无开放全文' : item.attachmentStatus === 'FAILED' ? '附件抓取失败' : '待抓取全文' }}
                        </div>
                    <span class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest">PMID: {{ item.pmid }}</span>
                  </div>
                  <div class="flex items-center gap-2">
                    <a
                      :href="item.sourceUrl || `https://pubmed.ncbi.nlm.nih.gov/${item.pmid}/`"
                      target="_blank"
                      rel="noreferrer"
                      class="text-apple-secondary-text hover:text-apple-blue transition-colors"
                      title="打开文献页面"
                    >
                      <ExternalLink :size="14" />
                    </a>
                    <button
                      :disabled="item.savedToLibrary || !item.relationId || downloadingRelationIds.includes(item.relationId)"
                      class="apple-button-secondary !py-2 !px-3 text-xs disabled:opacity-50 flex items-center gap-2"
                      @click="handleDownload(item.relationId)"
                    >
                      <Loader2
                        v-if="item.relationId && downloadingRelationIds.includes(item.relationId)"
                        :size="12"
                        class="animate-spin"
                      />
                      <Download v-else :size="12" />
                      {{ item.savedToLibrary ? '已下载' : '下载入库' }}
                    </button>
                  </div>
                </div>

                <h3 class="text-lg font-bold text-apple-text leading-snug">
                  {{ item.title }}
                </h3>

                <div class="p-4 rounded-apple bg-apple-blue/[0.05] shadow-[inset_0_1px_0_rgba(255,255,255,0.02)] space-y-2">
                  <div class="flex items-center gap-2 text-[10px] font-bold text-apple-blue uppercase tracking-widest">
                    <Sparkles :size="12" />
                    关联证据
                  </div>
                  <p class="text-xs text-apple-text font-semibold">
                    匹配目标：{{ item.matchedEnzymeName }} 
                    <span class="text-apple-secondary-text font-normal ml-1">({{ item.matchedEnzymeAccession || '未知 accession' }})</span>
                  </p>
                  <p class="text-[10px] text-apple-secondary-text">
                    证据来源：基于 {{ item.matchedFields || '标题/物种/EC/Accession' }} 的多维语义匹配
                  </p>
                </div>

                <div class="flex flex-wrap items-center gap-3 text-xs text-apple-secondary-text font-medium">
                  <span class="text-apple-text">{{ item.authors }}</span>
                  <span>•</span>
                  <span class="italic">{{ item.journal }}, {{ item.publishYear }}</span>
                </div>
              </div>
            </div>
          </div>
        </template>

        <div v-else class="apple-card p-20 text-center border-dashed">
          <Search :size="48" class="mx-auto text-apple-secondary-text opacity-20 mb-4" />
          <h3 class="text-sm font-bold text-apple-text">暂时还没有扫描结果</h3>
          <p class="text-xs text-apple-secondary-text mt-2">先选择扫描范围，再去 PubMed 检索候选文献；下载入库后，它们才会出现在酶库中心。</p>
        </div>
      </div>
    </div>
  </div>
</template>

