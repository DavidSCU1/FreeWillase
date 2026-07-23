<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { createPluginUI } from 'molstar/lib/mol-plugin-ui'
import { renderReact18 } from 'molstar/lib/mol-plugin-ui/react18'
import { DefaultPluginUISpec } from 'molstar/lib/mol-plugin-ui/spec'
import type { PluginUIContext } from 'molstar/lib/mol-plugin-ui/context'
import * as loaders from 'molstar/lib/extensions/plugin/loaders'

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
let plugin: PluginUIContext | null = null

const hasStructureSource = () => Boolean(props.pdbId || props.url)

const disposeViewer = () => {
  if (plugin) {
    plugin.dispose()
    plugin = null
  }
}

const initViewer = async () => {
  if (!parentRef.value || plugin || !hasStructureSource()) return

  await nextTick()

  try {
    const spec = DefaultPluginUISpec()
    spec.components = {
      ...spec.components,
      controls: {
        top: 'none',
        left: 'none',
        right: 'none',
        bottom: 'none'
      },
      remoteState: 'none',
      hideTaskOverlay: true,
      disableDragOverlay: true
    }
    spec.layout = {
      initial: {
        isExpanded: false,
        showControls: false,
        controlsDisplay: 'reactive',
        regionState: {
          top: 'hidden',
          left: 'hidden',
          right: 'hidden',
          bottom: 'hidden'
        }
      }
    }

    plugin = await createPluginUI({
      target: parentRef.value,
      render: renderReact18,
      spec
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
  if (!plugin) return
  await plugin.clear()
}

const reloadStructure = async () => {
  if (!hasStructureSource()) {
    await clearViewer()
    return
  }
  if (shouldLoadFromUrl() && props.url) {
    await loadByUrl(props.url)
  } else if (props.pdbId) {
    await loadStructure(props.pdbId, props.sourceDb)
  }
}

const loadStructure = async (id: string, sourceDb?: string) => {
  if (!plugin) return
  isLoading.value = true
  hasError.value = false
  errorMessage.value = ''
  const normalizedSourceDb = normalizeSourceDb(sourceDb)

  try {
    await clearViewer()

    if (normalizedSourceDb === 'PDB') {
      await loaders.loadPdb(plugin, id.toUpperCase())
      return
    }

    if (normalizedSourceDb === 'ALPHAFOLD' || normalizedSourceDb === 'ALPHAFOLDDB') {
      await loaders.loadAlphaFoldDb(plugin, id)
      return
    }

    try {
      await loaders.loadPdb(plugin, id.toUpperCase())
    } catch (pdbErr) {
      console.warn(`PDB ${id} not found, trying AlphaFold...`)
      try {
        await loaders.loadAlphaFoldDb(plugin, id)
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
  if (!plugin) return
  isLoading.value = true
  hasError.value = false
  errorMessage.value = ''
  try {
    await clearViewer()
    const normalizedUrl = url.toLowerCase()
    const isBinary = normalizedUrl.endsWith('.bcif')
    await loaders.loadStructureFromUrl(plugin, url, props.format || 'pdb', isBinary)
  } catch (e) {
    console.error('URL structure loading error:', e)
    hasError.value = true
    errorMessage.value = 'URL 结构加载失败'
  } finally {
    isLoading.value = false
  }
}

watch(() => props.pdbId, async (newId) => {
  if (newId && !plugin) {
    await initViewer()
    return
  }
  if (newId && plugin && !shouldLoadFromUrl()) await loadStructure(newId, props.sourceDb)
  if (!newId && !props.url) {
    disposeViewer()
  }
})

watch(() => props.url, async (newUrl) => {
  if (newUrl && !plugin) {
    await initViewer()
    return
  }
  if (newUrl && plugin && shouldLoadFromUrl()) await loadByUrl(newUrl)
  if (!newUrl && !props.pdbId) {
    disposeViewer()
  }
})

watch(() => props.format, async () => {
  if (props.url && plugin && shouldLoadFromUrl()) await loadByUrl(props.url)
})

watch(() => props.sourceDb, async () => {
  if (!plugin) return
  await reloadStructure()
})

onMounted(() => {
  initViewer()
})

onUnmounted(() => {
  disposeViewer()
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
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
}
.molstar-viewer-container .msp-plugin {
  width: 100%;
  height: 100%;
}
.molstar-viewer-container .msp-plugin-content,
.molstar-viewer-container .msp-layout-region,
.molstar-viewer-container .msp-layout-static,
.molstar-viewer-container .msp-viewport,
.molstar-viewer-container .msp-viewport-area {
  width: 100%;
  height: 100%;
}
.molstar-viewer-container canvas {
  width: 100% !important;
  height: 100% !important;
  display: block;
}
</style>
