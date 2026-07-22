<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { 
  Search, 
  ExternalLink, 
  CheckCircle2, 
  AlertCircle, 
  Loader2,
  Database,
  Link as LinkIcon,
  FileText,
  Sparkles
} from 'lucide-vue-next'
import { useLiterature } from '@/composables/useLiterature'
import NcbiCredentialsForm from '@/components/NcbiCredentialsForm.vue'

const { 
  literatures, 
  loading, 
  ncbiEmail, 
  ncbiApiKey, 
  fetchAllLiteratures,
  matchAll
} = useLiterature()

let pollInterval: number | null = null

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

const handleMatchAll = async () => {
  await matchAll()
  startPolling()
  // Stop polling after 2 minutes or if results found
  setTimeout(stopPolling, 120000)
}

onMounted(() => {
  fetchAllLiteratures()
})

const confidenceConfig = {
  STRONG: { color: 'text-apple-green', bg: 'bg-apple-green/10', label: '强关联' },
  WEAK: { color: 'text-amber-500', bg: 'bg-amber-500/10', label: '弱关联' },
  CANDIDATE: { color: 'text-apple-blue', bg: 'bg-apple-blue/10', label: '候选' },
}
</script>

<template>
  <div class="max-w-5xl mx-auto space-y-8 pb-20">
    <!-- Header: Focused on Action -->
    <div class="apple-card p-10 bg-gradient-to-br from-apple-blue/5 via-transparent to-apple-green/5">
      <div class="flex flex-col md:flex-row items-center justify-between gap-8">
        <div class="space-y-4 text-center md:text-left">
          <div class="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-apple-blue/10 text-apple-blue text-[10px] font-bold uppercase tracking-widest">
            <Sparkles :size="12" />
            AI 证据发现引擎
          </div>
          <h1 class="text-4xl font-bold tracking-tight text-apple-text">文献一键关联</h1>
          <p class="text-apple-secondary-text text-sm max-w-md">
            点击下方按钮，系统将自动扫描全库酶条目，通过 PubMed 检索并智能匹配实验证据。
            <span class="italic block mt-2">“让每一颗酶都找到它的自由意志。”</span>
          </p>
        </div>
        
        <div class="flex flex-col gap-4 w-full md:w-auto">
          <button 
            @click="handleMatchAll"
            :disabled="loading"
            class="apple-button-primary !py-4 !px-8 flex items-center justify-center gap-3 text-sm shadow-apple-blue disabled:opacity-50"
          >
            <Loader2 v-if="loading" :size="20" class="animate-spin" />
            <Search v-else :size="20" />
            {{ loading ? '正在搜寻证据...' : '立即开启全库扫描' }}
          </button>
          
          <!-- Compact Credentials Toggle/Status -->
          <div class="flex items-center justify-center md:justify-start gap-2 text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest">
            <Database :size="12" />
            {{ ncbiApiKey ? 'API KEY 已激活 (10次/秒)' : '匿名模式 (3次/秒)' }}
          </div>
        </div>
      </div>
    </div>

    <!-- Main Content Area -->
    <div class="grid grid-cols-1 gap-8">
      <!-- Credentials Section (Collapsible or just simpler) -->
      <div class="apple-card p-6 bg-white/50 dark:bg-black/20">
        <NcbiCredentialsForm 
          v-model:email="ncbiEmail"
          v-model:api-key="ncbiApiKey"
        />
      </div>

      <!-- Simplified Literature List -->
      <div class="space-y-6">
        <div class="flex items-center justify-between px-2">
          <h2 class="text-lg font-bold text-apple-text flex items-center gap-2">
            <FileText :size="18" class="text-apple-blue" />
            匹配结果
            <span v-if="literatures.length" class="text-xs font-normal text-apple-secondary-text ml-2">
              已发现 {{ literatures.length }} 条关联
            </span>
          </h2>
        </div>

        <div v-if="loading && !literatures.length" class="apple-card p-20 flex flex-col items-center justify-center">
          <Loader2 :size="40" class="animate-spin text-apple-blue mb-4" />
          <p class="text-sm font-bold text-apple-text">正在深挖 PubMed 数据库...</p>
        </div>

        <template v-else-if="literatures.length">
          <div class="grid grid-cols-1 gap-4">
            <div
              v-for="item in literatures"
              :key="item.id"
              class="apple-card p-6 group hover:border-apple-blue/30 transition-all flex gap-6"
            >
              <!-- Score Badge -->
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
                    <span class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest">PMID: {{ item.pmid }}</span>
                  </div>
                  <a :href="'https://pubmed.ncbi.nlm.nih.gov/' + item.pmid" target="_blank" class="text-apple-secondary-text hover:text-apple-blue transition-colors">
                    <ExternalLink :size="14" />
                  </a>
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
                    <span class="text-apple-secondary-text font-normal ml-1">({{ item.matchedEnzymeAccession }})</span>
                  </p>
                  <p class="text-[10px] text-apple-secondary-text">
                    证据来源：基于 {{ item.matchedFields }} 的多维语义匹配
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
          <h3 class="text-sm font-bold text-apple-text">尚无酶的“犯罪记录”</h3>
          <p class="text-xs text-apple-secondary-text mt-2">点击上方的“立即开启全库扫描”，让系统去 PubMed 抓捕它们吧！</p>
        </div>
      </div>
    </div>
  </div>
</template>

