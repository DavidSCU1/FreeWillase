import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { getMiniFoldResult, predictMiniFold } from '@/utils/api'
import { normalizeSequenceInput } from '@/utils/predictionProviders'

export type MiniFoldBackend = 'auto' | 'directml' | 'ipex' | 'oneapi_cpu' | 'cuda' | 'cpu'

const STORAGE_KEY = 'minifoldWorkbench'

type StoredMiniFoldSettings = {
  sequence?: string
  envText?: string
  targetChains?: number | null
  useAcceleration?: boolean
  backend?: MiniFoldBackend
}

function safeParse<T>(value: string | null): T | null {
  if (!value) return null
  try {
    return JSON.parse(value) as T
  } catch {
    return null
  }
}

export const useMiniFoldStore = defineStore('minifold', () => {
  const sequence = ref('')
  const envText = ref('')
  const targetChains = ref<number | null>(null)
  const useAcceleration = ref(true)
  const backend = ref<MiniFoldBackend>('auto')

  const engineTaskId = ref<string | null>(null)
  const status = ref<'idle' | 'running' | 'success' | 'error'>('idle')
  const error = ref<string | null>(null)

  const viewerUrl = ref<string | null>(null)
  const viewerFormat = ref<'pdb'>('pdb')
  const lastStructureText = ref<string | null>(null)

  const isSubmitting = ref(false)

  function loadSettings() {
    const saved = safeParse<StoredMiniFoldSettings>(localStorage.getItem(STORAGE_KEY))
    if (!saved) return
    if (typeof saved.sequence === 'string') sequence.value = saved.sequence
    if (typeof saved.envText === 'string') envText.value = saved.envText
    if (saved.targetChains === null || typeof saved.targetChains === 'number') targetChains.value = saved.targetChains
    if (typeof saved.useAcceleration === 'boolean') useAcceleration.value = saved.useAcceleration
    if (saved.backend) backend.value = saved.backend
  }

  function persistSettings() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      sequence: sequence.value,
      envText: envText.value,
      targetChains: targetChains.value,
      useAcceleration: useAcceleration.value,
      backend: backend.value,
    }))
  }

  function revokeViewerUrl() {
    if (!viewerUrl.value) return
    if (viewerUrl.value.startsWith('blob:')) URL.revokeObjectURL(viewerUrl.value)
    viewerUrl.value = null
  }

  function setViewer(structure: string) {
    revokeViewerUrl()
    const blob = new Blob([structure], { type: 'text/plain' })
    viewerUrl.value = URL.createObjectURL(blob)
    viewerFormat.value = 'pdb'
    lastStructureText.value = structure
  }

  function resetResult() {
    engineTaskId.value = null
    status.value = 'idle'
    error.value = null
    revokeViewerUrl()
    lastStructureText.value = null
  }

  async function submit() {
    error.value = null

    let normalizedSequence = ''
    try {
      normalizedSequence = normalizeSequenceInput(sequence.value, 'protein')
    } catch (e: any) {
      error.value = e?.message || '序列格式错误'
      status.value = 'error'
      return false
    }

    revokeViewerUrl()
    lastStructureText.value = null
    status.value = 'running'
    isSubmitting.value = true

    try {
      const body = await predictMiniFold({
        sequence: normalizedSequence,
        envText: envText.value.trim(),
        targetChains: targetChains.value ?? undefined,
        useIgpu: useAcceleration.value,
        backend: useAcceleration.value ? backend.value : 'cpu',
      })

      if (body?.status === 'running' && body?.taskId) {
        engineTaskId.value = body.taskId
        return true
      }

      if (!body?.pdb) {
        throw new Error(body?.error || 'MiniFold 返回结构为空')
      }

      setViewer(body.pdb)
      status.value = 'success'
      return true
    } catch (e: any) {
      error.value = e?.message || 'MiniFold 预测失败'
      status.value = 'error'
      return false
    } finally {
      isSubmitting.value = false
    }
  }

  async function fetchResult() {
    if (!engineTaskId.value) return false
    try {
      const body = await getMiniFoldResult(engineTaskId.value)
      if (body?.status === 'success' && body?.pdb) {
        setViewer(body.pdb)
        status.value = 'success'
        return true
      }
      if (body?.status === 'failed') {
        error.value = body.error || '预测失败'
        status.value = 'error'
        return true
      }
    } catch (e: any) {
      error.value = e?.message || '结果获取失败'
      status.value = 'error'
      return true
    }
    return false
  }

  loadSettings()

  watch([sequence, envText, targetChains, useAcceleration, backend], () => {
    persistSettings()
  }, { deep: true })

  watch(useAcceleration, enabled => {
    if (!enabled) {
      backend.value = 'cpu'
    } else if (backend.value === 'cpu') {
      backend.value = 'auto'
    }
  })

  return {
    sequence,
    envText,
    targetChains,
    useAcceleration,
    backend,
    engineTaskId,
    status,
    error,
    viewerUrl,
    viewerFormat,
    lastStructureText,
    isSubmitting,
    submit,
    fetchResult,
    resetResult,
  }
})
