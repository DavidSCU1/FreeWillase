<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { createPluginUI } from 'molstar/lib/mol-plugin-ui'
import { renderReact18 } from 'molstar/lib/mol-plugin-ui/react18'
import { DefaultPluginUISpec } from 'molstar/lib/mol-plugin-ui/spec'
import type { PluginUIContext } from 'molstar/lib/mol-plugin-ui/context'
import * as loaders from 'molstar/lib/extensions/plugin/loaders'
import { StructureElement, StructureProperties } from 'molstar/lib/mol-model/structure'
import { PluginStateObject } from 'molstar/lib/mol-plugin-state/objects'
import type { EnzymeAnnotation } from '@/types'

import 'molstar/build/viewer/molstar.css'

const props = defineProps<{
  pdbId?: string
  url?: string
  sourceDb?: string
  format?: 'pdb' | 'mmcif'
  selectedAnnotation?: EnzymeAnnotation | null
  pickMode?: boolean
}>()

const emit = defineEmits<{
  (event: 'residue-picked', payload: { residueNumber: number; chainLabel?: string; residueName?: string }): void
}>()

const parentRef = ref<HTMLDivElement | null>(null)
const isLoading = ref(false)
const hasError = ref(false)
const errorMessage = ref('')
let plugin: PluginUIContext | null = null
let clickSubscription: { unsubscribe: () => void } | null = null

const hasStructureSource = () => Boolean(props.pdbId || props.url)

const disposeViewer = () => {
  if (plugin) {
    clickSubscription?.unsubscribe()
    clickSubscription = null
    plugin.dispose()
    plugin = null
  }
}

const initViewer = async () => {
  if (!parentRef.value || plugin || !hasStructureSource()) return

  await nextTick()

  try {
    // #region debug-point B:init-viewer
    fetch('http://127.0.0.1:7777/event', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sessionId: 'structure-empty-annotations', runId: 'post-fix', hypothesisId: 'B', location: 'StructureViewer.vue:initViewer:33', msg: '[DEBUG] init viewer requested', data: { pdbId: props.pdbId, url: props.url, sourceDb: props.sourceDb, width: parentRef.value?.clientWidth ?? null, height: parentRef.value?.clientHeight ?? null }, ts: Date.now() }) }).catch(() => {})
    // #endregion
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
    // #region debug-point B:init-viewer-created
    fetch('http://127.0.0.1:7777/event', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sessionId: 'structure-empty-annotations', runId: 'post-fix', hypothesisId: 'B', location: 'StructureViewer.vue:initViewer:58', msg: '[DEBUG] plugin created', data: { hasPlugin: Boolean(plugin), width: parentRef.value?.clientWidth ?? null, height: parentRef.value?.clientHeight ?? null }, ts: Date.now() }) }).catch(() => {})
    // #endregion
    registerClickHandler()

    await reloadStructure()
  } catch (err) {
    // #region debug-point B:init-viewer-error
    fetch('http://127.0.0.1:7777/event', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sessionId: 'structure-empty-annotations', runId: 'post-fix', hypothesisId: 'B', location: 'StructureViewer.vue:initViewer:65', msg: '[DEBUG] init viewer failed', data: { error: err instanceof Error ? err.message : String(err) }, ts: Date.now() }) }).catch(() => {})
    // #endregion
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

const requestViewerResize = () => {
  plugin?.canvas3d?.requestResize()
}

const buildAnnotationSelection = (annotation?: EnzymeAnnotation | null) => {
  if (!annotation) return null
  const selection: Record<string, string | number> = annotation.annotationType === 'DOMAIN'
    ? {
        beg_label_seq_id: annotation.startResidue,
        end_label_seq_id: annotation.endResidue,
      }
    : {
        label_seq_id: annotation.startResidue,
      }
  if (annotation.chainLabel) {
    selection.label_asym_id = annotation.chainLabel
  }
  return selection
}

const registerClickHandler = () => {
  if (!plugin || clickSubscription) return
  clickSubscription = plugin.behaviors.interaction.click.subscribe(({ current }) => {
    if (!props.pickMode || !current?.loci || !StructureElement.Loci.is(current.loci) || StructureElement.Loci.isEmpty(current.loci)) {
      return
    }
    const location = StructureElement.Loci.getFirstLocation(current.loci)
    if (!location) {
      return
    }
    const residueNumber = StructureProperties.residue.label_seq_id(location)
    if (!residueNumber || residueNumber <= 0) {
      return
    }
    emit('residue-picked', {
      residueNumber,
      chainLabel: StructureProperties.chain.label_asym_id(location) || undefined,
      residueName: StructureProperties.atom.label_comp_id(location) || undefined,
    })
  })
}

const syncAnnotationSelection = () => {
  if (!plugin) return
  const elements = buildAnnotationSelection(props.selectedAnnotation)
  if (!elements) {
    plugin.managers.interactivity.lociSelects.deselectAll()
    plugin.managers.interactivity.lociHighlights.clearHighlights()
    return
  }
  plugin.managers.interactivity.lociSelects.deselectAll()
  const structures = plugin.state.data.selectQ((query) => query.rootsOfType(PluginStateObject.Molecule.Structure))
  for (const structureNode of structures) {
    const structure = structureNode.obj?.data
    if (!structure) continue
    const loci = StructureElement.Loci.fromSchema(structure, elements)
    if (StructureElement.Loci.isEmpty(loci)) continue
    plugin.managers.interactivity.lociSelects.select({ loci }, false)
    plugin.managers.camera.focusLoci(loci, { extraRadius: 4 })
    return
  }
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
  syncAnnotationSelection()
}

const loadStructure = async (id: string, sourceDb?: string) => {
  if (!plugin) return
  isLoading.value = true
  hasError.value = false
  errorMessage.value = ''
  const normalizedSourceDb = normalizeSourceDb(sourceDb)

  try {
    await clearViewer()
    // #region debug-point C:load-structure-start
    fetch('http://127.0.0.1:7777/event', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sessionId: 'structure-empty-annotations', runId: 'post-fix', hypothesisId: 'C', location: 'StructureViewer.vue:loadStructure:143', msg: '[DEBUG] load structure start', data: { id, sourceDb, normalizedSourceDb }, ts: Date.now() }) }).catch(() => {})
    // #endregion

    if (normalizedSourceDb === 'PDB') {
      const pdbUrl = `https://files.rcsb.org/download/${id.toUpperCase()}.cif`
      console.info('[StructureViewer] loading PDB structure', { id, pdbUrl })
      await loaders.loadStructureFromUrl(plugin, pdbUrl, 'mmcif', false)
      // #region debug-point C:load-structure-success-pdb
      fetch('http://127.0.0.1:7777/event', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sessionId: 'structure-empty-annotations', runId: 'post-fix', hypothesisId: 'C', location: 'StructureViewer.vue:loadStructure:150', msg: '[DEBUG] load PDB success', data: { id, pdbUrl }, ts: Date.now() }) }).catch(() => {})
      // #endregion
      requestViewerResize()
      return
    }

    if (normalizedSourceDb === 'ALPHAFOLD' || normalizedSourceDb === 'ALPHAFOLDDB') {
      const alphaFoldUrl = `https://alphafold.ebi.ac.uk/files/AF-${id}-F1-model_v4.pdb`
      console.info('[StructureViewer] loading AlphaFold structure', { id, alphaFoldUrl })
      await loaders.loadStructureFromUrl(plugin, alphaFoldUrl, 'pdb', false)
      // #region debug-point C:load-structure-success-af
      fetch('http://127.0.0.1:7777/event', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sessionId: 'structure-empty-annotations', runId: 'post-fix', hypothesisId: 'C', location: 'StructureViewer.vue:loadStructure:161', msg: '[DEBUG] load AlphaFold success', data: { id, alphaFoldUrl }, ts: Date.now() }) }).catch(() => {})
      // #endregion
      requestViewerResize()
      return
    }

    try {
      const pdbUrl = `https://files.rcsb.org/download/${id.toUpperCase()}.cif`
      console.info('[StructureViewer] trying PDB fallback', { id, pdbUrl })
      await loaders.loadStructureFromUrl(plugin, pdbUrl, 'mmcif', false)
      // #region debug-point C:load-structure-success-fallback-pdb
      fetch('http://127.0.0.1:7777/event', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sessionId: 'structure-empty-annotations', runId: 'post-fix', hypothesisId: 'C', location: 'StructureViewer.vue:loadStructure:171', msg: '[DEBUG] load fallback PDB success', data: { id, pdbUrl }, ts: Date.now() }) }).catch(() => {})
      // #endregion
      requestViewerResize()
    } catch (pdbErr) {
      // #region debug-point C:load-structure-fallback-pdb-error
      fetch('http://127.0.0.1:7777/event', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sessionId: 'structure-empty-annotations', runId: 'post-fix', hypothesisId: 'C', location: 'StructureViewer.vue:loadStructure:175', msg: '[DEBUG] load fallback PDB failed', data: { id, error: pdbErr instanceof Error ? pdbErr.message : String(pdbErr) }, ts: Date.now() }) }).catch(() => {})
      // #endregion
      console.warn(`PDB ${id} not found, trying AlphaFold...`, pdbErr)
      try {
        const alphaFoldUrl = `https://alphafold.ebi.ac.uk/files/AF-${id}-F1-model_v4.pdb`
        console.info('[StructureViewer] trying AlphaFold fallback', { id, alphaFoldUrl })
        await loaders.loadStructureFromUrl(plugin, alphaFoldUrl, 'pdb', false)
        // #region debug-point C:load-structure-success-fallback-af
        fetch('http://127.0.0.1:7777/event', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sessionId: 'structure-empty-annotations', runId: 'post-fix', hypothesisId: 'C', location: 'StructureViewer.vue:loadStructure:181', msg: '[DEBUG] load fallback AlphaFold success', data: { id, alphaFoldUrl }, ts: Date.now() }) }).catch(() => {})
        // #endregion
        requestViewerResize()
      } catch (afErr) {
        // #region debug-point C:load-structure-all-failed
        fetch('http://127.0.0.1:7777/event', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sessionId: 'structure-empty-annotations', runId: 'post-fix', hypothesisId: 'C', location: 'StructureViewer.vue:loadStructure:185', msg: '[DEBUG] all structure sources failed', data: { id, pdbError: pdbErr instanceof Error ? pdbErr.message : String(pdbErr), alphaFoldError: afErr instanceof Error ? afErr.message : String(afErr) }, ts: Date.now() }) }).catch(() => {})
        // #endregion
        console.error('[StructureViewer] all structure sources failed', { id, pdbErr, afErr })
        hasError.value = true
        errorMessage.value = '未能加载该酶的 3D 结构'
      }
    }
  } catch (e) {
    // #region debug-point C:load-structure-error
    fetch('http://127.0.0.1:7777/event', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sessionId: 'structure-empty-annotations', runId: 'post-fix', hypothesisId: 'C', location: 'StructureViewer.vue:loadStructure:193', msg: '[DEBUG] structure loading error', data: { id, error: e instanceof Error ? e.message : String(e) }, ts: Date.now() }) }).catch(() => {})
    // #endregion
    console.error('Structure loading error:', e)
    hasError.value = true
    errorMessage.value = '结构解析失败'
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
    requestViewerResize()
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

watch(() => props.selectedAnnotation, () => {
  syncAnnotationSelection()
}, { deep: true })

onMounted(() => {
  initViewer()
})

onUnmounted(() => {
  disposeViewer()
})
</script>

<template>
  <div class="relative w-full h-[420px] md:h-[520px] rounded-apple-lg overflow-hidden border border-apple-border bg-white shadow-apple">
    <!-- Canvas Container -->
    <div ref="parentRef" class="w-full h-full molstar-viewer-container"></div>

    <div
      v-if="pickMode && !isLoading && !hasError && (pdbId || url)"
      class="absolute top-4 right-4 z-20 px-3 py-2 rounded-apple bg-amber-500/90 text-white text-[10px] font-bold shadow-lg"
    >
      选点模式已开启，请点击结构中的残基
    </div>
    
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
          {{ hasError ? '当前结构文件未能成功加载。可以稍后重试，或检查该条目对应的 PDB / AlphaFold 标识是否有效。' : '请从左侧列表选择条目，查看该酶的三维结构。' }}
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
