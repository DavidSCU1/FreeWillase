import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { getMiniFoldResult, predictMiniFold } from '@/utils/api'
import { normalizeSequenceInput } from '@/utils/predictionProviders'

export type MiniFoldBackend = 'auto' | 'directml' | 'ipex' | 'oneapi_cpu' | 'cuda' | 'cpu'

const STORAGE_KEY = 'minifoldWorkbench'
const RUNTIME_STATE_KEY = 'minifoldRuntimeState'
const TASK_HISTORY_KEY = 'minifoldTaskHistory'

export type MiniFoldTaskStatus = 'running' | 'success' | 'error'

export type MiniFoldTaskRecord = {
  taskId: string
  status: MiniFoldTaskStatus
  startedAt: number
  updatedAt: number
  sequenceLength: number
  targetChains: number | null
  backend: string
  pythonLabel: string
  error: string | null
  structureText: string | null
}

type StoredMiniFoldSettings = {
  sequence?: string
  envText?: string
  targetChains?: number | null
  useAcceleration?: boolean
  backend?: MiniFoldBackend
  condaEnvName?: string
}

type StoredMiniFoldRuntimeState = {
  engineTaskId?: string | null
  status?: 'idle' | 'running' | 'success' | 'error'
  error?: string | null
  taskStartedAt?: number | null
  lastStructureText?: string | null
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
  const condaEnvName = ref('')

  const engineTaskId = ref<string | null>(null)
  const status = ref<'idle' | 'running' | 'success' | 'error'>('idle')
  const error = ref<string | null>(null)

  const viewerUrl = ref<string | null>(null)
  const viewerFormat = ref<'pdb'>('pdb')
  const lastStructureText = ref<string | null>(null)
  const taskStartedAt = ref<number | null>(null)
  const taskHistory = ref<MiniFoldTaskRecord[]>([])

  const isSubmitting = ref(false)

  function loadSettings() {
    const saved = safeParse<StoredMiniFoldSettings>(localStorage.getItem(STORAGE_KEY))
    if (!saved) return
    if (typeof saved.sequence === 'string') sequence.value = saved.sequence
    if (typeof saved.envText === 'string') envText.value = saved.envText
    if (saved.targetChains === null || typeof saved.targetChains === 'number') targetChains.value = saved.targetChains
    if (typeof saved.useAcceleration === 'boolean') useAcceleration.value = saved.useAcceleration
    if (saved.backend) backend.value = saved.backend
    if (typeof saved.condaEnvName === 'string') condaEnvName.value = saved.condaEnvName
  }

  function loadRuntimeState() {
    const saved = safeParse<StoredMiniFoldRuntimeState>(localStorage.getItem(RUNTIME_STATE_KEY))
    if (!saved) return
    if (typeof saved.engineTaskId === 'string') engineTaskId.value = saved.engineTaskId
    if (saved.engineTaskId === null) engineTaskId.value = null
    if (saved.status) status.value = saved.status
    if (typeof saved.error === 'string') error.value = saved.error
    if (saved.error === null) error.value = null
    if (typeof saved.taskStartedAt === 'number') taskStartedAt.value = saved.taskStartedAt
    if (saved.taskStartedAt === null) taskStartedAt.value = null
    if (typeof saved.lastStructureText === 'string' && saved.lastStructureText.trim()) {
      setViewer(saved.lastStructureText)
    }
  }

  function loadTaskHistory() {
    const saved = safeParse<MiniFoldTaskRecord[]>(localStorage.getItem(TASK_HISTORY_KEY))
    if (!saved || !Array.isArray(saved)) return
    taskHistory.value = saved
  }

  function persistSettings() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      sequence: sequence.value,
      envText: envText.value,
      targetChains: targetChains.value,
      useAcceleration: useAcceleration.value,
      backend: backend.value,
      condaEnvName: condaEnvName.value,
    }))
  }

  function persistRuntimeState() {
    localStorage.setItem(RUNTIME_STATE_KEY, JSON.stringify({
      engineTaskId: engineTaskId.value,
      status: status.value,
      error: error.value,
      taskStartedAt: taskStartedAt.value,
      lastStructureText: lastStructureText.value,
    }))
  }

  function persistTaskHistory() {
    localStorage.setItem(TASK_HISTORY_KEY, JSON.stringify(taskHistory.value))
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

  function upsertTaskRecord(record: MiniFoldTaskRecord) {
    const next = taskHistory.value.filter(item => item.taskId !== record.taskId)
    next.unshift(record)
    taskHistory.value = next
      .sort((a, b) => b.updatedAt - a.updatedAt)
      .slice(0, 20)
  }

  function updateCurrentTaskRecord(partial: Partial<MiniFoldTaskRecord>) {
    if (!engineTaskId.value || !taskStartedAt.value) return
    const existing = taskHistory.value.find(item => item.taskId === engineTaskId.value)
    const record: MiniFoldTaskRecord = {
      taskId: engineTaskId.value,
      status: (partial.status || status.value) as MiniFoldTaskStatus,
      startedAt: existing?.startedAt || taskStartedAt.value,
      updatedAt: Date.now(),
      sequenceLength: partial.sequenceLength ?? existing?.sequenceLength ?? sequence.value.replace(/\s+/g, '').length,
      targetChains: partial.targetChains ?? existing?.targetChains ?? targetChains.value,
      backend: partial.backend ?? existing?.backend ?? (useAcceleration.value ? backend.value : 'cpu'),
      pythonLabel: partial.pythonLabel ?? existing?.pythonLabel ?? (condaEnvName.value.trim() || 'MINIFOLD_PYTHON / auto-discovery'),
      error: partial.error ?? existing?.error ?? error.value,
      structureText: partial.structureText ?? existing?.structureText ?? lastStructureText.value,
    }
    upsertTaskRecord(record)
  }

  function activateTask(taskId: string) {
    const task = taskHistory.value.find(item => item.taskId === taskId)
    if (!task) return false

    engineTaskId.value = task.taskId
    status.value = task.status
    error.value = task.error
    taskStartedAt.value = task.startedAt

    if (task.structureText) {
      setViewer(task.structureText)
    } else {
      revokeViewerUrl()
      lastStructureText.value = null
    }
    persistRuntimeState()
    return true
  }

  function resetResult() {
    engineTaskId.value = null
    status.value = 'idle'
    error.value = null
    taskStartedAt.value = null
    revokeViewerUrl()
    lastStructureText.value = null
    persistRuntimeState()
  }

  async function submit() {
    error.value = null
    engineTaskId.value = null

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
    taskStartedAt.value = Date.now()
    status.value = 'running'
    isSubmitting.value = true

    try {
      const payload = {
        sequence: normalizedSequence,
        envText: envText.value.trim(),
        targetChains: targetChains.value ?? undefined,
        useIgpu: useAcceleration.value,
        backend: useAcceleration.value ? backend.value : 'cpu',
        condaEnvName: condaEnvName.value.trim() || undefined,
      }

      const body = await predictMiniFold({
        ...payload,
      })

      if (body?.status === 'running' && body?.taskId) {
        engineTaskId.value = body.taskId
        error.value = null
        updateCurrentTaskRecord({
          status: 'running',
          sequenceLength: normalizedSequence.length,
          targetChains: targetChains.value,
          backend: payload.backend,
          pythonLabel: payload.condaEnvName || 'MINIFOLD_PYTHON / auto-discovery',
          error: null,
          structureText: null,
        })
        persistRuntimeState()
        console.info('[MiniFold] task submitted', {
          taskId: body.taskId,
          backend: payload.backend,
          targetChains: payload.targetChains ?? 'auto',
          python: payload.condaEnvName || 'MINIFOLD_PYTHON / auto-discovery',
        })
        return true
      }

      if (!body?.pdb) {
        throw new Error(body?.error || 'MiniFold 返回结构为空')
      }

      setViewer(body.pdb)
      status.value = 'success'
      updateCurrentTaskRecord({
        status: 'success',
        error: null,
        structureText: body.pdb,
      })
      persistRuntimeState()
      return true
    } catch (e: any) {
      error.value = e?.message || 'MiniFold 预测失败'
      status.value = 'error'
      persistRuntimeState()
      console.error('[MiniFold] submit failed before task creation', {
        message: error.value,
        backend: useAcceleration.value ? backend.value : 'cpu',
        targetChains: targetChains.value ?? 'auto',
        python: condaEnvName.value.trim() || 'MINIFOLD_PYTHON / auto-discovery',
      })
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
        updateCurrentTaskRecord({
          status: 'success',
          error: null,
          structureText: body.pdb,
        })
        persistRuntimeState()
        console.info('[MiniFold] task finished', { taskId: engineTaskId.value })
        return true
      }
      if (body?.status === 'failed') {
        error.value = body.error || '预测失败'
        status.value = 'error'
        updateCurrentTaskRecord({
          status: 'error',
          error: error.value,
        })
        persistRuntimeState()
        console.error('[MiniFold] task failed after creation', {
          taskId: engineTaskId.value,
          message: error.value,
        })
        return true
      }
    } catch (e: any) {
      error.value = e?.message || '结果获取失败'
      status.value = 'error'
      updateCurrentTaskRecord({
        status: 'error',
        error: error.value,
      })
      persistRuntimeState()
      console.error('[MiniFold] result polling failed', {
        taskId: engineTaskId.value,
        message: error.value,
      })
      return true
    }
    return false
  }

  loadSettings()
  loadRuntimeState()
  loadTaskHistory()

  watch([sequence, envText, targetChains, useAcceleration, backend, condaEnvName], () => {
    persistSettings()
  }, { deep: true })

  watch([engineTaskId, status, error, taskStartedAt, lastStructureText], () => {
    persistRuntimeState()
  }, { deep: true })

  watch(taskHistory, () => {
    persistTaskHistory()
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
    condaEnvName,
    engineTaskId,
    status,
    error,
    viewerUrl,
    viewerFormat,
    lastStructureText,
    taskStartedAt,
    taskHistory,
    isSubmitting,
    submit,
    fetchResult,
    activateTask,
    resetResult,
  }
})
