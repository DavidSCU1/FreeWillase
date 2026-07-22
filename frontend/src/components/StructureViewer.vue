<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch, nextTick } from 'vue'
import { lib } from 'molstar/lib/apps/viewer/lib'
import { ColorNames } from 'molstar/lib/mol-util/color/names'
import * as loaders from 'molstar/lib/extensions/plugin/loaders'

// Import pre-compiled CSS
import 'molstar/build/viewer/molstar.css'

const { PluginContext, DefaultPluginSpec } = lib.plugin

const props = defineProps<{
  pdbId?: string
  url?: string
}>()

const parentRef = ref<HTMLDivElement | null>(null)
const isLoading = ref(false)
const hasError = ref(false)
const errorMessage = ref('')
let plugin: any = null // Using any because of the complex internal types

const initViewer = async () => {
  if (!parentRef.value) return
  
  await nextTick()

  try {
    // 1. Create Plugin Context with a minimal spec (no UI)
    const spec = DefaultPluginSpec()
    plugin = new PluginContext(spec)
    
    // 2. Initialize the plugin
    await plugin.init()

    // 3. Attach the canvas to our DOM element
    if (plugin.canvas3d && parentRef.value) {
      const canvas = plugin.canvas3d.handle.canvas
      canvas.style.width = '100%'
      canvas.style.height = '100%'
      parentRef.value.appendChild(canvas)
      
      // Set background color
      plugin.canvas3d.setProps({
        renderer: { backgroundColor: ColorNames.white }
      })
    }

    // 4. Load initial structure
    if (props.pdbId) {
      await loadStructure(props.pdbId)
    } else if (props.url) {
      await loadByUrl(props.url)
    }
  } catch (err) {
    console.error('Molstar init error:', err)
    hasError.value = true
    errorMessage.value = '3D 渲染引擎启动失败'
  }
}

const loadStructure = async (id: string) => {
  if (!plugin) return
  isLoading.value = true
  hasError.value = false
  errorMessage.value = ''

  try {
    // Clear existing models
    await plugin.clear()

    // 1. Try PDB (Experimental)
    try {
      await loaders.loadPdb(plugin, id.toUpperCase())
    } catch (pdbErr) {
      console.warn(`PDB ${id} not found, trying AlphaFold...`)
      
      // 2. Try AlphaFold (Predicted)
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
  try {
    await plugin.clear()
    await loaders.loadUrl(plugin, url, 'pdb')
  } catch (e) {
    hasError.value = true
    errorMessage.value = 'URL 结构加载失败'
  } finally {
    isLoading.value = false
  }
}

watch(() => props.pdbId, async (newId) => {
  if (newId && plugin) await loadStructure(newId)
})

watch(() => props.url, async (newUrl) => {
  if (newUrl && plugin) await loadByUrl(newUrl)
})

onMounted(() => {
  initViewer()
})

onUnmounted(() => {
  if (plugin) {
    plugin.dispose()
    plugin = null
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
  display: flex;
  width: 100%;
  height: 100%;
}
.molstar-viewer-container canvas {
  width: 100% !important;
  height: 100% !important;
  display: block;
}
</style>
