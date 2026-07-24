<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
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
  loading,
  scanLoading,
  downloadingRelationIds,
  ncbiEmail,
  ncbiApiKey,
  fetchAllLiteratures,
  scan,
  downloadLiterature,
} = useLiterature()
const { enzymes, refreshEnzymeLibrary } = useNcbiImport()

const scanScope = ref<'all' | 'selected'>('all')
const selectedEnzymeIds = ref<number[]>([])
let pollInterval: number | null = null

const confidenceConfig = {
  STRONG: { bg: 'bg-apple-green/10 text-apple-green', label: '强关联' },
  WEAK: { bg: 'bg-amber-500/10 text-amber-500', label: '弱关联' },
  CANDIDATE: { bg: 'bg-apple-blue/10 text-apple-blue', label: '候选' },
}

const selectedCount = computed(() => selectedEnzymeIds.value.length)

const orderedLiteratures = computed(() =>
  [...literatures.value].sort((a, b) => Number(Boolean(b.savedToLibrary)) - Number(Boolean(a.savedToLibrary))),
)

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

const startPolling = () => {
  if (pollInterval) return
  pollInterval = window.setInterval(() => {
    fetchAllLiteratures()
  }, 5000)
}

const stopPolling = () => {
  if (pollInterval) {
    clearInterval(pollInterval)
    pollInterval = null
  }
}

const handleScan = async () => {
  if (scanScope.value === 'selected' && !selectedEnzymeIds.value.length) {
    window.alert('请先选择要扫描的酶条目')
    return
  }
  await scan(scanScope.value === 'selected' ? selectedEnzymeIds.value : undefined)
  startPolling()
  window.setTimeout(stopPolling, 120000)
}

const handleDownload = async (relationId?: number) => {
  if (!relationId) return
  await downloadLiterature(relationId)
}

onMounted(async () => {
  await Promise.all([fetchAllLiteratures(), refreshEnzymeLibrary()])
  const enzymeId = Number(route.query.enzymeId)
  if (enzymeId) {
    scanScope.value = 'selected'
    selectedEnzymeIds.value = [enzymeId]
  }
})

onBeforeUnmount(() => {
  stopPolling()
})
</script>

<template>
  <div class="max-w-6xl mx-auto space-y-8 pb-20">
    <div class="apple-card p-10 bg-gradient-to-br from-apple-blue/5 via-transparent to-apple-green/5">
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
            :disabled="loading"
            class="apple-button-primary !py-4 !px-8 flex items-center justify-center gap-3 text-sm shadow-apple-blue disabled:opacity-50"
          >
            <Loader2 v-if="loading" :size="20" class="animate-spin" />
            <Search v-else :size="20" />
            {{ loading ? '正在扫描 PubMed...' : scanScope === 'all' ? '开始全库扫描' : '开始部分扫描' }}
          </button>
          
          <div class="flex items-center justify-center md:justify-start gap-2 text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest">
            <Database :size="12" />
            {{ ncbiApiKey ? 'API KEY 已激活 (10次/秒)' : '匿名模式 (3次/秒)' }}
          </div>
        </div>
      </div>
    </div>

    <div class="grid grid-cols-1 gap-8">
      <div class="apple-card p-6 bg-white/50 dark:bg-black/20">
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
              class="text-left p-4 rounded-apple border transition-all"
              :class="selectedEnzymeIds.includes(enzyme.id) ? 'border-apple-blue bg-apple-blue/5' : 'border-apple-border bg-apple-background dark:bg-white/5'"
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

        <div v-if="loading && !literatures.length" class="apple-card p-20 flex flex-col items-center justify-center">
          <Loader2 :size="40" class="animate-spin text-apple-blue mb-4" />
          <p class="text-sm font-bold text-apple-text">正在深挖 PubMed 数据库...</p>
        </div>

        <template v-else-if="orderedLiteratures.length">
          <div class="grid grid-cols-1 gap-4">
            <div
              v-for="item in orderedLiteratures"
              :key="item.relationId || `${item.id}-${item.matchedEnzymeAccession}`"
              class="apple-card p-6 group hover:border-apple-blue/30 transition-all flex gap-6"
            >
              <div class="hidden md:flex flex-col items-center justify-center w-20 h-20 rounded-apple bg-apple-background border border-apple-border shrink-0">
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

                <div class="p-4 rounded-apple bg-apple-blue/5 border border-apple-blue/10 space-y-2">
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

