<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { Viewer } from 'molstar/lib/apps/viewer/app'

import 'molstar/build/viewer/molstar.css'

const props = defineProps<{
  pdbId?: string
  url?: string
  sourceDb?: string
  format?: 'pdb' | 'mmcif'
}>()

const parentRef = ref<HTMLDivElement | null>(null)
const isLoading = ref(false)
const hasError = ref(false)
const errorMessage = ref('')
let viewer: Viewer | null = null

const initViewer = async () => {
  if (!parentRef.value) return

  await nextTick()

  try {
    viewer = await Viewer.create(parentRef.value, {
      layoutShowControls: false,
      layoutShowRemoteState: false,
      layoutShowSequence: false,
      layoutShowLog: false,
      viewportShowAnimation: false,
      viewportShowExpand: false,
      viewportShowSelectionMode: false,
      viewportShowControls: true,
      viewportBackgroundColor: 'white'
    })

    await reloadStructure()
  } catch (err) {
    console.error('Molstar init error:', err)
    hasError.value = true
    errorMessage.value = '3D 渲染引擎启动失败'
  }
}

const normalizeSourceDb = (value?: string) => (value || '').trim().toUpperCase()

const shouldLoadFromUrl = () => Boolean(props.url) && !['PDB', 'ALPHAFOLD', 'ALPHAFOLDDB'].includes(normalizeSourceDb(props.sourceDb))

const clearViewer = async () => {
  if (!viewer) return
  await viewer.plugin.clear()
}

const reloadStructure = async () => {
  if (shouldLoadFromUrl() && props.url) {
    await loadByUrl(props.url)
  } else if (props.pdbId) {
    await loadStructure(props.pdbId, props.sourceDb)
  }
}

const loadStructure = async (id: string, sourceDb?: string) => {
  if (!viewer) return
  isLoading.value = true
  hasError.value = false
  errorMessage.value = ''
  const normalizedSourceDb = normalizeSourceDb(sourceDb)

  try {
    await clearViewer()

    if (normalizedSourceDb === 'PDB') {
      await viewer.loadPdb(id.toUpperCase())
      return
    }

    if (normalizedSourceDb === 'ALPHAFOLD' || normalizedSourceDb === 'ALPHAFOLDDB') {
      await viewer.loadAlphaFoldDb(id)
      return
    }

    try {
      await viewer.loadPdb(id.toUpperCase())
    } catch (pdbErr) {
      console.warn(`PDB ${id} not found, trying AlphaFold...`)
      try {
        await viewer.loadAlphaFoldDb(id)
      } catch (afErr) {
        hasError.value = true
        errorMessage.value = '看来这只酶很有“自由意志”'
      }
    }
  } catch (e) {
    console.error('Structure loading error:', e)
    hasError.value = true
    errorMessage.value = '结构解析出现了叛逆'
  } finally {
    isLoading.value = false
  }
}

const loadByUrl = async (url: string) => {
  if (!viewer) return
  isLoading.value = true
  hasError.value = false
  errorMessage.value = ''
  try {
    await clearViewer()
    const normalizedUrl = url.toLowerCase()
    const isBinary = normalizedUrl.endsWith('.bcif')
    await viewer.loadStructureFromUrl(url, props.format || 'pdb', isBinary)
  } catch (e) {
    console.error('URL structure loading error:', e)
    hasError.value = true
    errorMessage.value = 'URL 结构加载失败'
  } finally {
    isLoading.value = false
  }
}

watch(() => props.pdbId, async (newId) => {
  if (newId && viewer && !shouldLoadFromUrl()) await loadStructure(newId, props.sourceDb)
})

watch(() => props.url, async (newUrl) => {
  if (newUrl && viewer && shouldLoadFromUrl()) await loadByUrl(newUrl)
})

watch(() => props.format, async () => {
  if (props.url && viewer && shouldLoadFromUrl()) await loadByUrl(props.url)
})

watch(() => props.sourceDb, async () => {
  if (!viewer) return
  await reloadStructure()
})

onMounted(() => {
  initViewer()
})

onUnmounted(() => {
  if (viewer) {
    viewer.dispose()
    viewer = null
  }
})
</script>

<template>
  <div class="relative w-full h-full rounded-apple-lg overflow-hidden border border-apple-border bg-white shadow-apple min-h-[400px]">
    <!-- Canvas Container -->
    <div ref="parentRef" class="absolute inset-0 molstar-viewer-container"></div>
    
    <!-- Loading State -->
    <div v-if="isLoading" class="absolute inset-0 flex items-center justify-center bg-white/60 dark:bg-black/40 backdrop-blur-[2px] z-20">
      <div class="flex flex-col items-center gap-3">
        <div class="w-10 h-10 border-4 border-apple-blue/20 border-t-apple-blue rounded-full animate-spin"></div>
        <p class="text-[10px] font-bold text-apple-blue uppercase tracking-widest animate-pulse">Fetching Structure...</p>
      </div>
    </div>

    <!-- Error/Empty State -->
    <div v-if="hasError || (!pdbId && !url)" class="absolute inset-0 flex items-center justify-center bg-apple-background/80 backdrop-blur-sm z-30">
      <div class="text-center px-6">
        <div class="w-12 h-12 rounded-full flex items-center justify-center mx-auto mb-3" 
             :class="hasError ? 'bg-red-500/10 text-red-500' : 'bg-apple-blue/10 text-apple-blue'">
          <svg v-if="hasError" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="lucide lucide-alert-triangle"><path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/><path d="M12 9v4"/><path d="M12 17h.01"/></svg>
          <svg v-else xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="lucide lucide-dna"><path d="m8 8-4 4 4 4"/><path d="m16 8 4 4-4 4"/><path d="M7 21h10"/><path d="M7 3h10"/><path d="M12 7v10"/></svg>
        </div>
        <h4 class="text-xs font-bold text-apple-text mb-1 uppercase tracking-wider">
          {{ hasError ? errorMessage : '等待加载结构' }}
        </h4>
        <p class="text-[10px] text-apple-secondary-text leading-relaxed max-w-[200px] mx-auto">
          {{ hasError ? '它竟然在 PDB 和 AlphaFold 里都玩起了失踪。快去“预测接口”给它安排个“数字模型”吧！' : '请从左侧列表选择条目，看看它的“自由意志”长什么样。' }}
        </p>
      </div>
    </div>
  </div>
</template>

<style>
/* Style the container and handle canvas properly */
.molstar-viewer-container {
  width: 100%;
  height: 100%;
}
.molstar-viewer-container :deep(.msp-plugin) {
  width: 100%;
  height: 100%;
}
.molstar-viewer-container canvas {
  width: 100% !important;
  height: 100% !important;
  display: block;
}
</style>
