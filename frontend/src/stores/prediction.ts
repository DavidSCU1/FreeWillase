import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import type { MoleculeType, PredictionProvider, PredictionTask } from '@/types'
import { getSupportedModels, getSupportedMoleculeTypes, normalizeSequenceInput, parseSequenceRecords, predictStructure } from '@/utils/predictionProviders'

type StoredSettings = {
  provider: PredictionProvider
  baseUrl?: string
  moleculeType: MoleculeType
  model?: string
  name?: string
  submitMode?: 'single' | 'batch' | 'complex'
  rememberApiKey?: boolean
  apiKey?: string
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

export const usePredictionStore = defineStore('prediction', () => {
  const provider = ref<PredictionProvider>('biohub')
  const apiKey = ref('')
  const rememberApiKey = ref(false)
  const baseUrl = ref('')
  const moleculeType = ref<MoleculeType>('protein')
  const model = ref('')
  const name = ref('Sample')
  const submitMode = ref<'single' | 'batch' | 'complex'>('single')
  const sequence = ref('')
  const smiles = ref('')

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
    if (saved.provider) provider.value = saved.provider
    if (typeof saved.baseUrl === 'string') baseUrl.value = saved.baseUrl
    if (saved.moleculeType) moleculeType.value = saved.moleculeType
    if (typeof saved.model === 'string') model.value = saved.model
    if (typeof saved.name === 'string') name.value = saved.name
    if (saved.submitMode === 'single' || saved.submitMode === 'batch' || saved.submitMode === 'complex') {
      submitMode.value = saved.submitMode
    }
    rememberApiKey.value = !!saved.rememberApiKey
    if (rememberApiKey.value && typeof saved.apiKey === 'string') apiKey.value = saved.apiKey
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
    const types = getSupportedMoleculeTypes(provider.value)
    if (!types.includes(moleculeType.value)) moleculeType.value = types[0]
    const models = getSupportedModels(provider.value)
    if (models.length === 0) {
      model.value = ''
    } else if (!models.includes(model.value)) {
      model.value = models[0]
    }
    if (moleculeType.value === 'ligand' && submitMode.value === 'batch') {
      submitMode.value = 'single'
    }
    if (submitMode.value === 'complex' && provider.value !== 'chai1') {
      submitMode.value = 'single'
    }
    if (moleculeType.value === 'ligand' && submitMode.value === 'complex') {
      submitMode.value = 'single'
    }
  }

  async function submit() {
    error.value = null
    if (provider.value === 'minifold') {
      error.value = 'MiniFold 已预留接口，你无需操作'
      return
    }

    if (submitMode.value !== 'single' && moleculeType.value === 'ligand') {
      error.value = 'Ligand 当前仅支持单条提交'
      return
    }

    if (submitMode.value === 'complex' && provider.value !== 'chai1') {
      error.value = '多链复合体模式当前仅支持 Chai-1，请切换 Provider 或改为单条/多条提交'
      return
    }

    let preparedSequence = sequence.value
    let batchRecords: Array<{ name: string, sequence: string }> = []
    let complexRecords: Array<{ name: string, sequence: string }> = []
    let complexTotalLength = 0
    if (moleculeType.value !== 'ligand') {
      try {
        if (submitMode.value === 'batch' || submitMode.value === 'complex') {
          const records = parseSequenceRecords(sequence.value, moleculeType.value)
          if (records.length < 2) {
            error.value = '多条提交模式至少需要 2 条 FASTA 记录'
            return
          }
          if (submitMode.value === 'batch') {
            batchRecords = records
            preparedSequence = batchRecords[0].sequence
          } else {
            complexRecords = records
            complexTotalLength = complexRecords.reduce((acc, record) => acc + record.sequence.length, 0)
          }
        } else {
          preparedSequence = normalizeSequenceInput(sequence.value, moleculeType.value)
        }
      } catch (e: any) {
        error.value = e?.message || '序列格式错误'
        return
      }
    }

    isSubmitting.value = true

    try {
      const lengthOverride = submitMode.value === 'complex' ? complexTotalLength : undefined
      const records = moleculeType.value === 'ligand'
        ? [{ name: name.value.trim() || 'Unnamed', sequence: '' }]
        : (submitMode.value === 'batch'
          ? batchRecords
          : submitMode.value === 'complex'
            ? [{ name: `${name.value.trim() || 'Complex'} (${complexRecords.length} chains)`, sequence: '' }]
            : [{ name: name.value.trim() || 'Unnamed', sequence: preparedSequence }])

      const entries = records.map(record => ({
        record,
        task: {
          id: makeTaskId(),
          createdAt: new Date().toISOString(),
          status: 'running',
          provider: provider.value,
          moleculeType: moleculeType.value,
          name: record.name,
          sequenceLength: moleculeType.value === 'ligand'
            ? undefined
            : (lengthOverride ?? record.sequence.length),
        } as PredictionTask,
      }))

      tasks.value = [...entries.map(entry => entry.task), ...tasks.value]
      activeTaskId.value = entries[0]?.task.id || null

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
              sequence: (moleculeType.value === 'ligand' || submitMode.value === 'complex') ? undefined : entry.record.sequence,
              sequenceRecords: submitMode.value === 'complex' ? complexRecords : undefined,
              smiles: smiles.value,
            }
          )

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

  watch([provider, baseUrl, moleculeType, model, name, submitMode, rememberApiKey, apiKey], () => {
    persistSettings()
  })

  watch(provider, () => {
    normalizeAfterProviderChange()
  })

  watch(moleculeType, () => {
    if (moleculeType.value === 'ligand' && submitMode.value !== 'single') {
      submitMode.value = 'single'
    }
  })

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
    smiles,
    supportedModels,
    supportedTypes,
    tasks,
    activeTask,
    activeTaskId,
    isSubmitting,
    error,
    viewerUrl,
    viewerFormat,
    lastStructureText,
    submit,
    selectTask,
    clearApiKey,
  }
})
