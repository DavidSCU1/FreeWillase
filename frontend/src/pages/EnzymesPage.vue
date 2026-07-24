<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { 
  Search, 
  Filter, 
  FlaskConical, 
  Dna, 
  BookOpen, 
  ExternalLink, 
  Database, 
  Info, 
  Layers, 
  MapPin, 
  Tag, 
  Maximize2, 
  Loader2,
  Trash2,
  Sparkles
} from 'lucide-vue-next'
import { useNcbiImport } from '@/composables/useNcbiImport'
import { useLiterature } from '@/composables/useLiterature'
import StructureViewer from '@/components/StructureViewer.vue'

const router = useRouter()
const { enzymes, refreshEnzymeLibrary, removeEnzyme } = useNcbiImport()
const { enzymeLiteratures, fetchEnzymeLiteratures, enzymeLoading: loadingLit } = useLiterature()

const selectedId = ref<number | null>(null)
const searchQuery = ref('')
const showFullscreenViewer = ref(false)
const isDeleting = ref(false)

async function handleDelete(id: number) {
  if (confirm('确定要放走这只酶吗？一旦放归野外（删除），它的自由意志就不再受你掌控了。')) {
    isDeleting.value = true
    const success = await removeEnzyme(id)
    if (success) {
      selectedId.value = null
    }
    isDeleting.value = false
  }
}

function handleOpenMatcher() {
  if (!selectedId.value) {
    router.push('/matcher')
    return
  }
  router.push({
    path: '/matcher',
    query: { enzymeId: String(selectedId.value) },
  })
}

const filteredEnzymes = computed(() => {
  if (!searchQuery.value) return enzymes.value
  const q = searchQuery.value.toLowerCase()
  return enzymes.value.filter(e => 
    e.accession.toLowerCase().includes(q) || 
    e.proteinName.toLowerCase().includes(q) || 
    e.organismName.toLowerCase().includes(q)
  )
})

const selectedEnzyme = computed(() => {
  if (!enzymes.value.length) return null
  if (selectedId.value == null) return enzymes.value[0]
  return enzymes.value.find((item) => item.id === selectedId.value) ?? enzymes.value[0]
})

const selectedStructureId = computed(() => {
  const enzyme = selectedEnzyme.value
  if (!enzyme) return ''
  return enzyme.structureId || enzyme.uniprotAccession || enzyme.accession
})

const selectedStructureUrl = computed(() => {
  const enzyme = selectedEnzyme.value
  if (!enzyme?.structureUrl) return undefined
  if (enzyme.structureSourceDb === 'PDB' || enzyme.structureSourceDb === 'AlphaFold') return undefined
  return enzyme.structureUrl
})

const selectedStructureSource = computed(() => {
  const enzyme = selectedEnzyme.value
  return enzyme?.structureSourceDb || 'AUTO'
})

const selectedStructureFormat = computed<'pdb' | 'mmcif'>(() => {
  const enzyme = selectedEnzyme.value
  const structureUrl = enzyme?.structureUrl?.toLowerCase() || ''
  const structureType = enzyme?.structureType?.toLowerCase() || ''
  if (structureUrl.endsWith('.cif') || structureUrl.endsWith('.mmcif') || structureType.includes('mmcif')) {
    return 'mmcif'
  }
  return 'pdb'
})

const selectedStructureType = computed(() => {
  const enzyme = selectedEnzyme.value
  return enzyme?.structureType || 'AUTO'
})

const selectedStructureStatus = computed(() => {
  const enzyme = selectedEnzyme.value
  if (!enzyme) return 'Auto-Retrieved'
  if (enzyme.structureSourceDb === 'PDB') return 'Experimental (PDB)'
  if (enzyme.structureSourceDb === 'AlphaFold') return 'Predicted (AlphaFold)'
  if (enzyme.structureSourceDb) return `Curated (${enzyme.structureSourceDb})`
  return 'Auto-Retrieved'
})

const selectedNcbiUrl = computed(() => {
  const enzyme = selectedEnzyme.value
  if (!enzyme) return undefined
  return enzyme.ncbiProteinUrl || (enzyme.accession ? `https://www.ncbi.nlm.nih.gov/protein/${enzyme.accession}` : undefined)
})

const selectedUniprotUrl = computed(() => selectedEnzyme.value?.uniprotUrl)

watch(
  () => selectedId.value,
  (id) => {
    if (id) {
      fetchEnzymeLiteratures(id)
    }
  }
)

watch(
  () => enzymes.value,
  (list) => {
    if (list.length && selectedId.value == null) {
      selectedId.value = list[0].id
    }
  },
  { immediate: true },
)

onMounted(async () => {
  try {
    window.scrollTo({ top: 0, left: 0, behavior: 'auto' })
    await refreshEnzymeLibrary()
    if (selectedId.value) {
      fetchEnzymeLiteratures(selectedId.value)
    }
  } catch {
    // Silent
  }
})
</script>

<template>
  <div class="flex flex-col h-full space-y-8">
    <!-- Header with Search and Filter -->
    <div class="flex flex-col md:flex-row md:items-center justify-between gap-4">
      <div class="space-y-1">
        <h1 class="text-3xl font-bold tracking-tight text-apple-text">酶库中心</h1>
        <p class="text-apple-secondary-text text-sm">管理、浏览与分析本地酶条目数据库</p>
      </div>
      <div class="flex items-center gap-3">
        <div class="relative w-64">
          <Search class="absolute left-3 top-1/2 -translate-y-1/2 text-apple-secondary-text" :size="14" />
          <input 
            v-model="searchQuery"
            type="text" 
            placeholder="搜索 Accession 或蛋白名称..." 
            class="apple-input pl-9 pr-4 py-2 text-xs"
          />
        </div>
        <button class="apple-button-secondary flex items-center gap-2 !py-2 !px-4 text-xs">
          <Filter :size="14" />
          筛选器
        </button>
      </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-[380px_1fr] gap-8 items-start">
      <!-- Left List Sidebar -->
      <div class="space-y-6 sticky top-24 h-[calc(100vh-280px)] flex flex-col">
        <div class="apple-card overflow-hidden flex flex-col flex-1">
          <div class="p-4 border-b border-apple-border flex items-center justify-between bg-black/5 dark:bg-white/5">
            <span class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">酶条目列表</span>
            <span class="text-[10px] font-bold text-apple-blue bg-apple-blue/10 px-2 py-0.5 rounded-full">{{ filteredEnzymes.length }}</span>
          </div>
          <div class="flex-1 overflow-y-auto p-2 space-y-1 no-scrollbar">
            <button
              v-for="enzyme in filteredEnzymes"
              :key="enzyme.id"
              @click="selectedId = enzyme.id"
              class="w-full text-left p-3 rounded-apple transition-all group relative overflow-hidden"
              :class="selectedEnzyme?.id === enzyme.id 
                ? 'bg-apple-blue text-white shadow-lg shadow-apple-blue/20' 
                : 'hover:bg-black/5 dark:hover:bg-white/5'"
            >
              <div class="flex justify-between items-start mb-1">
                <span class="text-xs font-bold" :class="selectedEnzyme?.id === enzyme.id ? 'text-white' : 'text-apple-blue'">
                  {{ enzyme.accession }}
                </span>
                <div class="flex items-center gap-2">
                  <span v-if="selectedEnzyme?.id === enzyme.id" class="text-[10px] font-medium opacity-70 italic">
                    {{ enzyme.sequenceLength }} aa
                  </span>
                  <button 
                    @click.stop="handleDelete(enzyme.id)"
                    class="p-1 rounded-full hover:bg-white/20 transition-colors"
                    :class="selectedEnzyme?.id === enzyme.id ? 'text-white' : 'text-red-500 opacity-0 group-hover:opacity-100'"
                    title="删除"
                  >
                    <Trash2 :size="10" />
                  </button>
                </div>
              </div>
              <p class="text-xs font-semibold line-clamp-2 leading-snug" :class="selectedEnzyme?.id === enzyme.id ? 'text-white' : 'text-apple-text'">
                {{ enzyme.proteinName }}
              </p>
              <p class="mt-2 text-[10px] truncate" :class="selectedEnzyme?.id === enzyme.id ? 'text-white/70' : 'text-apple-secondary-text'">
                {{ enzyme.organismName }}
              </p>
            </button>
            
            <div v-if="!filteredEnzymes.length" class="p-12 text-center">
              <FlaskConical :size="32" class="mx-auto text-apple-secondary-text opacity-20 mb-4" />
              <p class="text-xs text-apple-secondary-text">这只酶可能逃出了自由意志的包围圈</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Right Detail Content -->
      <div v-if="selectedEnzyme" class="space-y-8 pb-20">
        <!-- Main Detail Card -->
        <div class="apple-card p-8">
          <div class="flex flex-col md:flex-row justify-between items-start gap-6">
            <div class="space-y-4 flex-1">
              <div class="flex items-center gap-3">
                <span class="px-2 py-1 rounded-full bg-apple-blue/10 text-apple-blue text-[10px] font-bold uppercase tracking-widest">
                  Active Entry
                </span>
                <span class="text-xs font-medium text-apple-secondary-text">ID: {{ selectedEnzyme.id }}</span>
              </div>
              <h2 class="text-3xl font-bold tracking-tight text-apple-text leading-tight">
                {{ selectedEnzyme.proteinName }}
              </h2>
              <div class="flex flex-wrap gap-4 text-sm">
                <div class="flex items-center gap-2 text-apple-secondary-text">
                  <Database :size="16" />
                  <span>Accession: <span class="text-apple-text font-semibold">{{ selectedEnzyme.accession }}</span></span>
                </div>
                <div class="flex items-center gap-2 text-apple-secondary-text">
                  <Tag :size="16" />
                  <span>Organism: <span class="text-apple-text font-semibold italic">{{ selectedEnzyme.organismName }}</span></span>
                </div>
              </div>
            </div>
            <div class="flex gap-3">
              <button 
                @click="selectedEnzyme && handleDelete(selectedEnzyme.id)"
                :disabled="isDeleting"
                class="apple-button-secondary !text-red-500 !border-red-500/20 hover:!bg-red-500/5 flex items-center gap-2 !py-2 !px-4 text-xs disabled:opacity-50"
              >
                <Loader2 v-if="isDeleting" :size="14" class="animate-spin" />
                <Trash2 v-else :size="14" />
                删除条目
              </button>
              <a
                v-if="selectedNcbiUrl"
                :href="selectedNcbiUrl"
                target="_blank"
                rel="noreferrer"
                class="apple-button-secondary flex items-center gap-2 !py-2 !px-4 text-xs"
              >
                <ExternalLink :size="14" />
                NCBI 详情
              </a>
            </div>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-3 gap-6 mt-10">
            <div class="p-5 rounded-apple bg-apple-background dark:bg-white/5 border border-apple-border">
              <div class="flex items-center gap-2 mb-3 text-apple-secondary-text">
                <Layers :size="14" />
                <span class="text-[10px] font-bold uppercase tracking-widest">序列长度</span>
              </div>
              <p class="text-2xl font-bold text-apple-text">{{ selectedEnzyme.sequenceLength }} <span class="text-sm font-medium opacity-50">aa</span></p>
            </div>
            <div class="p-5 rounded-apple bg-apple-background dark:bg-white/5 border border-apple-border">
              <div class="flex items-center gap-2 mb-3 text-apple-secondary-text">
                <MapPin :size="14" />
                <span class="text-[10px] font-bold uppercase tracking-widest">物种 Tax ID</span>
              </div>
              <p class="text-2xl font-bold text-apple-text">{{ selectedEnzyme.taxId || '-' }}</p>
            </div>
            <div class="p-5 rounded-apple bg-apple-background dark:bg-white/5 border border-apple-border overflow-hidden">
              <div class="flex items-center gap-2 mb-3 text-apple-secondary-text">
                <Info :size="14" />
                <span class="text-[10px] font-bold uppercase tracking-widest">序列哈希</span>
              </div>
              <p class="text-xs font-mono text-apple-secondary-text truncate">{{ selectedEnzyme.sequenceHash }}</p>
            </div>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-3 gap-6 mt-6">
            <div class="p-5 rounded-apple bg-apple-background dark:bg-white/5 border border-apple-border">
              <div class="flex items-center gap-2 mb-3 text-apple-secondary-text">
                <Database :size="14" />
                <span class="text-[10px] font-bold uppercase tracking-widest">NCBI</span>
              </div>
              <p class="text-sm font-semibold text-apple-text truncate">{{ selectedEnzyme.ncbiProteinAccession || selectedEnzyme.accession }}</p>
            </div>
            <div class="p-5 rounded-apple bg-apple-background dark:bg-white/5 border border-apple-border">
              <div class="flex items-center gap-2 mb-3 text-apple-secondary-text">
                <Tag :size="14" />
                <span class="text-[10px] font-bold uppercase tracking-widest">UniProt</span>
              </div>
              <p class="text-sm font-semibold text-apple-text truncate">{{ selectedEnzyme.uniprotAccession || '-' }}</p>
            </div>
            <div class="p-5 rounded-apple bg-apple-background dark:bg-white/5 border border-apple-border">
              <div class="flex items-center gap-2 mb-3 text-apple-secondary-text">
                <Dna :size="14" />
                <span class="text-[10px] font-bold uppercase tracking-widest">PDB</span>
              </div>
              <p class="text-sm font-semibold text-apple-text truncate">{{ selectedEnzyme.pdbId || selectedEnzyme.structureId || '-' }}</p>
            </div>
          </div>

          <div class="flex flex-wrap gap-3 mt-6">
            <a
              v-if="selectedNcbiUrl"
              :href="selectedNcbiUrl"
              target="_blank"
              rel="noreferrer"
              class="text-xs font-semibold text-apple-blue hover:underline"
            >
              查看 NCBI 页面
            </a>
            <a
              v-if="selectedUniprotUrl"
              :href="selectedUniprotUrl"
              target="_blank"
              rel="noreferrer"
              class="text-xs font-semibold text-apple-blue hover:underline"
            >
              查看 UniProt 页面
            </a>
            <a
              v-if="selectedEnzyme.pdbUrl"
              :href="selectedEnzyme.pdbUrl"
              target="_blank"
              rel="noreferrer"
              class="text-xs font-semibold text-apple-blue hover:underline"
            >
              查看 PDB 页面
            </a>
          </div>
        </div>

        <!-- Grid for Tabs/Sections -->
        <div class="grid grid-cols-1 xl:grid-cols-2 gap-8">
          <!-- Structure Section -->
          <div class="apple-card p-6 flex flex-col">
            <div class="flex items-center justify-between mb-6">
              <div class="flex items-center gap-3">
                <div class="w-8 h-8 rounded-apple bg-purple-500/10 text-purple-500 flex items-center justify-center">
                  <Dna :size="16" />
                </div>
                <h3 class="text-sm font-bold text-apple-text">3D 结构可视化</h3>
              </div>
              <div class="flex gap-2">
                <button 
                  @click="showFullscreenViewer = true"
                  class="p-2 rounded-full hover:bg-black/5 dark:hover:bg-white/5 text-apple-secondary-text transition-colors"
                  title="全屏查看"
                >
                  <Maximize2 :size="14" />
                </button>
              </div>
            </div>
            
            <div class="flex-1 min-h-[400px] relative group/viewer">
              <StructureViewer 
                :pdb-id="selectedStructureId"
                :url="selectedStructureUrl"
                :source-db="selectedStructureSource"
                :format="selectedStructureFormat"
              />
              
              <div class="absolute top-4 left-4 flex flex-col gap-2">
                <div class="px-3 py-1.5 rounded-apple bg-white/90 dark:bg-black/50 backdrop-blur shadow-sm border border-apple-border text-[10px] font-bold text-apple-text">
                  {{ selectedStructureStatus }}
                </div>
              </div>

              <div class="absolute bottom-4 left-4 right-4 flex gap-2 overflow-x-auto pb-2 no-scrollbar opacity-0 group-hover/viewer:opacity-100 transition-opacity">
                <div class="px-3 py-1.5 rounded-full bg-white/80 dark:bg-black/80 backdrop-blur shadow-sm border border-apple-border text-[10px] font-bold text-apple-text whitespace-nowrap">
                  ID: {{ selectedStructureId }}
                </div>
                <div class="px-3 py-1.5 rounded-full bg-apple-blue text-white shadow-sm text-[10px] font-bold whitespace-nowrap">
                  {{ selectedStructureType }}
                </div>
              </div>
            </div>
            
            <div class="mt-4 grid grid-cols-2 gap-3">
              <div class="p-3 rounded-apple bg-apple-background dark:bg-white/5 border border-apple-border">
                <p class="text-[9px] font-bold text-apple-secondary-text uppercase tracking-widest mb-1">Source</p>
                <p class="text-xs font-bold text-apple-text">{{ selectedStructureSource }}</p>
              </div>
              <div class="p-3 rounded-apple bg-apple-background dark:bg-white/5 border border-apple-border">
                <p class="text-[9px] font-bold text-apple-secondary-text uppercase tracking-widest mb-1">Structure ID</p>
                <p class="text-xs font-bold text-apple-text truncate">{{ selectedStructureId }}</p>
              </div>
            </div>
          </div>

          <!-- Literature Section -->
          <div class="apple-card p-6">
            <div class="flex items-center justify-between mb-6">
              <div class="flex items-center gap-3">
                <div class="w-8 h-8 rounded-apple bg-apple-green/10 text-apple-green flex items-center justify-center">
                  <BookOpen :size="16" />
                </div>
                <h3 class="text-sm font-bold text-apple-text">关联文献</h3>
              </div>
              <button 
                @click="handleOpenMatcher"
                :disabled="loadingLit"
                class="text-[10px] font-bold text-apple-blue hover:underline disabled:opacity-50 flex items-center gap-1"
              >
                <Loader2 v-if="loadingLit" :size="10" class="animate-spin" />
                <Sparkles v-else :size="10" />
                去文献匹配页
              </button>
            </div>
            
            <div class="space-y-4">
              <div v-if="loadingLit" class="py-12 flex flex-col items-center justify-center">
                <Loader2 :size="24" class="animate-spin text-apple-blue mb-2" />
                <p class="text-[10px] text-apple-secondary-text">搜寻证据中...</p>
              </div>

              <template v-else-if="enzymeLiteratures.length">
                <div 
                  v-for="lit in enzymeLiteratures" 
                  :key="lit.id"
                  class="p-4 rounded-apple border border-apple-border bg-apple-background dark:bg-white/5 group hover:border-apple-green/30 transition-all"
                >
                  <div class="flex justify-between items-start mb-2">
                    <span 
                      class="px-2 py-0.5 rounded-full text-[9px] font-bold uppercase tracking-wider"
                      :class="lit.confidenceLevel === 'STRONG' ? 'bg-apple-green/10 text-apple-green' : 'bg-apple-blue/10 text-apple-blue'"
                    >
                      {{ lit.confidenceLevel || 'MATCH' }}
                    </span>
                    <a
                      :href="lit.sourceUrl || `https://pubmed.ncbi.nlm.nih.gov/${lit.pmid}/`"
                      target="_blank"
                      rel="noreferrer"
                      class="text-[9px] text-apple-secondary-text font-bold uppercase hover:text-apple-blue"
                    >
                      PMID: {{ lit.pmid }}
                    </a>
                  </div>
                  <a
                    :href="lit.sourceUrl || `https://pubmed.ncbi.nlm.nih.gov/${lit.pmid}/`"
                    target="_blank"
                    rel="noreferrer"
                    class="block"
                  >
                    <h4 class="text-xs font-bold text-apple-text line-clamp-2 leading-snug group-hover:text-apple-blue transition-colors">
                      {{ lit.title }}
                    </h4>
                  </a>
                  <p class="mt-2 text-[10px] text-apple-secondary-text italic">{{ lit.journal }}, {{ lit.publishYear }}</p>
                </div>
              </template>

              <div v-else class="p-8 text-center border-2 border-dashed border-apple-border rounded-apple">
                <p class="text-xs text-apple-secondary-text italic">尚无已下载文献。请前往“文献匹配”扫描并下载后，再回到这里查看。</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-else class="flex flex-col items-center justify-center h-[calc(100vh-280px)] apple-card">
        <div class="w-20 h-20 bg-apple-light-gray dark:bg-white/5 rounded-full flex items-center justify-center mb-6 text-apple-secondary-text opacity-20">
          <FlaskConical :size="40" />
        </div>
        <h3 class="text-lg font-bold text-apple-text mb-2">捕获一只野生的酶</h3>
        <p class="text-sm text-apple-secondary-text max-w-xs text-center">请从左侧列表点选一个受害者，我们将在这里剥开它的“自由意志”，看看它的序列、结构和被文献实锤的证据。</p>
      </div>
    </div>

    <!-- Fullscreen 3D Viewer Modal -->
    <transition name="fade">
      <div v-if="showFullscreenViewer" class="fixed inset-0 z-[100] bg-black/80 backdrop-blur-md flex flex-col">
        <div class="h-16 px-8 flex items-center justify-between border-b border-white/10">
          <div class="flex items-center gap-4">
            <h3 class="text-white font-bold">{{ selectedEnzyme?.proteinName }}</h3>
            <span class="px-2 py-0.5 rounded-full bg-apple-blue text-white text-[10px] font-bold uppercase tracking-widest">
              {{ selectedStructureId }}
            </span>
          </div>
          <button 
            @click="showFullscreenViewer = false"
            class="w-10 h-10 rounded-full bg-white/10 text-white flex items-center justify-center hover:bg-white/20 transition-all"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="lucide lucide-x"><path d="M18 6 6 18"/><path d="m6 6 12 12"/></svg>
          </button>
        </div>
        <div class="flex-1 p-8">
          <StructureViewer 
            v-if="selectedEnzyme"
            :pdb-id="selectedStructureId"
            :url="selectedStructureUrl"
          />
        </div>
      </div>
    </transition>
  </div>
</template>
