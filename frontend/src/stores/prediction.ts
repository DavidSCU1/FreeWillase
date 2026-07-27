import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import type { MoleculeType, PredictionProvider, PredictionTask } from '@/types'
import { getSupportedModels, getSupportedMoleculeTypes, normalizeSequenceInput, parseSequenceRecords, predictStructure } from '@/utils/predictionProviders'
import { getMiniFoldLogs, getMiniFoldResult, getTrRosettaRnaResult } from '@/utils/api'

type StoredSettings = {
  provider: PredictionProvider
  baseUrl?: string
  moleculeType: MoleculeType
  model?: string
  name?: string
  submitMode?: 'single' | 'batch'
  rememberApiKey?: boolean
  apiKey?: string
  tasks?: PredictionTask[]
}

const STORAGE_KEY = 'predictionSettings'

function safeParse(json: string | null): StoredSettings | null {
  if (!json) return null
  try {
    return JSON.parse(json) as StoredSettings
  } catch (e) {
    return null
  }
}

function makeTaskId() {
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function isSupportedProvider(value: unknown): value is PredictionProvider {
  return value === 'nvidia' || value === 'minifold' || value === 'trrosettarna'
}

export const usePredictionStore = defineStore('prediction', () => {
  const provider = ref<PredictionProvider>('nvidia')
  const apiKey = ref('')
  const rememberApiKey = ref(false)
  const baseUrl = ref('')
  const moleculeType = ref<MoleculeType>('protein')
  const model = ref('')
  const name = ref('候选酶结构任务')
  const submitMode = ref<'single' | 'batch'>('single')
  const sequence = ref('')
  const taskLogs = ref<Record<string, string>>({})

  const tasks = ref<PredictionTask[]>([])
  const activeTaskId = ref<string | null>(null)

  const isSubmitting = ref(false)
  const error = ref<string | null>(null)
  
  const viewerUrl = ref<string | null>(null)
  const viewerFormat = ref<'pdb' | 'mmcif'>('pdb')
  const lastStructureText = ref<string | null>(null)

  const supportedModels = computed(() => getSupportedModels(provider.value))
  const supportedTypes = computed(() => getSupportedMoleculeTypes(provider.value))

  const activeTask = computed(() => tasks.value.find(t => t.id === activeTaskId.value) || null)

  function loadSettings() {
    const saved = safeParse(localStorage.getItem(STORAGE_KEY))
    if (!saved) return
    if (isSupportedProvider(saved.provider)) provider.value = saved.provider
    if (typeof saved.baseUrl === 'string') baseUrl.value = saved.baseUrl
    if (saved.moleculeType) moleculeType.value = saved.moleculeType
    if (typeof saved.model === 'string') model.value = saved.model
    if (typeof saved.name === 'string') name.value = saved.name
    if (saved.submitMode === 'single' || saved.submitMode === 'batch') {
      submitMode.value = saved.submitMode
    }
    rememberApiKey.value = !!saved.rememberApiKey
    if (rememberApiKey.value && typeof saved.apiKey === 'string') apiKey.value = saved.apiKey
    if (Array.isArray(saved.tasks)) tasks.value = saved.tasks
  }

  function persistSettings() {
    const payload: StoredSettings = {
      provider: provider.value,
      baseUrl: baseUrl.value || undefined,
      moleculeType: moleculeType.value,
      model: model.value || undefined,
      name: name.value || undefined,
      submitMode: submitMode.value,
      rememberApiKey: rememberApiKey.value,
      apiKey: rememberApiKey.value ? apiKey.value : undefined,
      tasks: tasks.value.slice(0, 20), // Persist last 20 tasks
    }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(payload))
  }

  function revokeViewerUrl() {
    if (!viewerUrl.value) return
    if (viewerUrl.value.startsWith('blob:')) URL.revokeObjectURL(viewerUrl.value)
    viewerUrl.value = null
  }

  function setViewer(structure: string, format: 'pdb' | 'mmcif') {
    revokeViewerUrl()
    const fileName = format === 'pdb' ? 'prediction.pdb' : 'prediction.cif'
    const blob = new Blob([structure], { type: 'text/plain' })
    viewerUrl.value = URL.createObjectURL(blob)
    viewerFormat.value = format
    lastStructureText.value = structure
  }

  function normalizeAfterProviderChange() {
    if (!isSupportedProvider(provider.value)) {
      provider.value = 'nvidia'
    }
    const types = getSupportedMoleculeTypes(provider.value)
    if (!types.includes(moleculeType.value)) moleculeType.value = types[0]
    const models = getSupportedModels(provider.value)
    if (models.length === 0) {
      model.value = ''
    } else if (!models.includes(model.value)) {
      model.value = models[0]
    }
    if (submitMode.value !== 'single' && (provider.value === 'nvidia' || provider.value === 'trrosettarna')) {
      submitMode.value = 'single'
    }
    if (provider.value === 'trrosettarna') {
      moleculeType.value = 'RNA'
      submitMode.value = 'single'
      baseUrl.value = ''
    }
  }

  // Force molecule type for RNA providers
  watch(provider, (newProvider) => {
    if (newProvider === 'trrosettarna') {
      moleculeType.value = 'RNA'
    }
  }, { immediate: true })

  async function submit() {
    error.value = null
    
    // Extra safety: ensure molecule type is correct before processing
    if (provider.value === 'trrosettarna') {
      moleculeType.value = 'RNA'
    }

    let preparedSequence = ''
    let batchRecords: Array<{ name: string, sequence: string }> = []

    try {
      if (submitMode.value === 'batch') {
        const records = parseSequenceRecords(sequence.value, moleculeType.value)
        if (records.length < 2) {
          error.value = '多条提交模式至少需要 2 条 FASTA 记录'
          return
        }
        batchRecords = records
        preparedSequence = batchRecords[0].sequence
      } else {
        preparedSequence = normalizeSequenceInput(sequence.value, moleculeType.value)
      }
    } catch (e: any) {
      error.value = e?.message || '序列格式错误'
      return
    }

    if (provider.value === 'nvidia' && submitMode.value !== 'single') {
      error.value = 'NVIDIA ESMFold 由于模型限制仅支持单条提交'
      return
    }

    if (provider.value === 'trrosettarna') {
      if (moleculeType.value !== 'RNA') {
        error.value = 'trRosettaRNA 仅支持 RNA 序列预测'
        return
      }
      if (submitMode.value !== 'single') {
        error.value = 'trRosettaRNA 当前仅支持单条提交'
        return
      }
      if (provider.value === 'trrosettarna' && preparedSequence.length > 400) {
        error.value = 'trRosettaRNA 网页版仅支持长度 ≤ 400nt 的序列'
        return
      }
    }

    isSubmitting.value = true

    try {
      const records = submitMode.value === 'batch'
        ? batchRecords
        : [{ name: name.value.trim() || '未命名预测任务', sequence: preparedSequence }]

      const entries = records.map(record => ({
        record,
        task: {
          id: makeTaskId(),
          createdAt: new Date().toISOString(),
          status: 'running',
          provider: provider.value,
          moleculeType: moleculeType.value,
          name: record.name,
          inputSequence: record.sequence,
          sequenceLength: record.sequence.length,
        } as PredictionTask,
      }))

      tasks.value = [...entries.map(entry => entry.task), ...tasks.value]
      activeTaskId.value = entries[0]?.task.id || null

      // Initial terminal log
      entries.forEach(entry => {
        taskLogs.value[entry.task.id] = `root@freewillase:~# freewillase-predict --provider "${entry.task.provider}" --name "${entry.task.name}"\n[SYSTEM] Preparing request...`
      })

      for (const entry of entries) {
        try {
          const result = await predictStructure(
            {
              provider: provider.value,
              apiKey: apiKey.value,
              baseUrl: baseUrl.value || undefined,
              rememberApiKey: rememberApiKey.value,
            },
            {
              name: entry.task.name,
              type: moleculeType.value,
              model: model.value,
              sequence: entry.record.sequence,
            }
          )

          if (result.taskId && (result as any).status === 'running') {
            entry.task.engineTaskId = result.taskId
            // Update logs for trrosettarna if available
            if (entry.task.provider === 'trrosettarna' && (result as any).logs) {
              taskLogs.value[entry.task.id] = (result as any).logs.join('\n')
            }
            // For async tasks, we don't mark as success yet
            continue 
          }

          entry.task.status = 'success'
          entry.task.result = result
          if (entry.task.id === activeTaskId.value) {
            setViewer(result.structure, result.format)
          }
        } catch (e: any) {
          const message = e?.message || '预测失败'
          entry.task.status = 'error'
          entry.task.error = message
          error.value = message
        }
      }
    } catch (e: any) {
      error.value = e?.message || '预测失败'
    } finally {
      isSubmitting.value = false
    }
  }

  function selectTask(id: string) {
    activeTaskId.value = id
    const task = tasks.value.find(t => t.id === id)
    if (!task?.result) return
    setViewer(task.result.structure, task.result.format)
  }

  function clearApiKey() {
    apiKey.value = ''
    rememberApiKey.value = false
    persistSettings()
  }

  loadSettings()
  normalizeAfterProviderChange()

  watch([provider, baseUrl, moleculeType, model, name, submitMode, rememberApiKey, apiKey, tasks], () => {
    persistSettings()
  }, { deep: true })

  watch(provider, () => {
    normalizeAfterProviderChange()
  })

  async function fetchTaskLogs(engineTaskId: string, frontendId: string) {
    if (!engineTaskId || !frontendId) return
    try {
      const logs = await getMiniFoldLogs(engineTaskId)
      if (logs) {
        taskLogs.value[frontendId] = logs
      }
    } catch (e) {
      console.error('Failed to fetch logs:', e)
    }
  }

  async function fetchTaskResult(task: PredictionTask) {
    if (!task.engineTaskId) return
    try {
      if (task.provider === 'trrosettarna') {
        const body = await getTrRosettaRnaResult(task.engineTaskId)
        
        // Update logs if available
        if (body.logs && Array.isArray(body.logs)) {
          taskLogs.value[task.id] = body.logs.join('\n')
        }

        if (body.status === 'FINISHED') {
          task.status = 'success'
          task.result = {
            providerName: 'trRosettaRNA',
            modelName: 'trRosettaRNA',
            format: 'pdb',
            structure: body.pdbContent,
            resultPageUrl: body.resultPageUrl,
          }
          if (task.id === activeTaskId.value) {
            setViewer(body.pdbContent, 'pdb')
          }
          return true // Finished
        } else if (body.status === 'ERROR') {
          task.status = 'error'
          task.error = body.message || '预测失败'
          return true // Finished
        }
        return false // Still running
      }

      const body = await getMiniFoldResult(task.engineTaskId)
      if (body.status === 'success') {
        task.status = 'success'
        task.result = {
          providerName: 'MiniFold',
          modelName: 'MiniFold-v1',
          format: 'pdb',
          structure: body.pdb,
          analysis: body.analysis,
        }
        if (task.id === activeTaskId.value) {
          setViewer(body.pdb, 'pdb')
        }
        return true // Finished
      } else if (body.status === 'failed') {
        task.status = 'error'
        task.error = body.error || '预测失败'
        return true // Finished
      }
    } catch (e: any) {
      console.error('Failed to fetch result:', e)
    }
    return false // Still running
  }

  return {
    provider,
    apiKey,
    rememberApiKey,
    baseUrl,
    moleculeType,
    model,
    name,
    submitMode,
    sequence,
    supportedModels,
    supportedTypes,
    tasks,
    activeTask,
    activeTaskId,
    isSubmitting,
    error,
    taskLogs,
    viewerUrl,
    viewerFormat,
    lastStructureText,
    submit,
    selectTask,
    fetchTaskLogs,
    fetchTaskResult,
    clearApiKey,
  }
})
