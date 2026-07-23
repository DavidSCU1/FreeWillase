import type { MoleculeType, PredictionConfig, PredictionRequest, PredictionResult } from '@/types'

export interface ParsedSequenceRecord {
  name: string
  sequence: string
}

const DEFAULT_BASE_URL: Record<Exclude<PredictionConfig['provider'], 'minifold'>, string> = {
  biohub: 'https://www.biohub.ai',
  nvidia: 'https://health.api.nvidia.com',
  chai1: 'https://api.biolm.ai',
}

const BIOHUB_MODELS = ['esmfold2-fast-2026-05', 'esmfold2-2026-05', 'esm3-open-2024-03'] as const
const NVIDIA_MODELS = ['esmfold'] as const
const CHAI1_MODELS = ['chai1'] as const

const SEQUENCE_RULES = {
  protein: {
    fullPattern: /^[ARNDCQEGHILKMFPSTWYV]*$/,
    charPattern: /[ARNDCQEGHILKMFPSTWYV]/,
    hint: '蛋白序列仅支持 20 种标准氨基酸单字母',
  },
  RNA: {
    fullPattern: /^[ACGUN]*$/,
    charPattern: /[ACGUN]/,
    hint: 'RNA 序列仅支持 A/C/G/U/N',
  },
  DNA: {
    fullPattern: /^[ACGTN]*$/,
    charPattern: /[ACGTN]/,
    hint: 'DNA 序列仅支持 A/C/G/T/N',
  },
} as const

export function getSupportedModels(provider: PredictionConfig['provider']): string[] {
  if (provider === 'biohub') return [...BIOHUB_MODELS]
  if (provider === 'nvidia') return [...NVIDIA_MODELS]
  if (provider === 'chai1') return [...CHAI1_MODELS]
  return []
}

export function getSupportedMoleculeTypes(provider: PredictionConfig['provider']): MoleculeType[] {
  if (provider === 'biohub') return ['protein', 'RNA', 'DNA']
  if (provider === 'nvidia') return ['protein']
  if (provider === 'chai1') return ['protein', 'RNA', 'DNA', 'ligand']
  return ['protein']
}

function getDevProxyBaseUrl(provider: Exclude<PredictionConfig['provider'], 'minifold'>) {
  return `/proxy/${provider}`
}

function pickBaseUrl(config: PredictionConfig): string {
  if (config.provider === 'minifold') throw new Error('MiniFold 已预留接口，不在本次集成范围内')
  const baseUrl = (config.baseUrl || '').trim()
  if (baseUrl) return baseUrl
  if (import.meta.env.DEV) return getDevProxyBaseUrl(config.provider)
  return DEFAULT_BASE_URL[config.provider]
}

function assertApiKey(config: PredictionConfig) {
  if (!config.apiKey?.trim()) throw new Error('请先填写 API Key')
}

function assertSequenceFor(type: MoleculeType, request: PredictionRequest) {
  if (type === 'ligand') {
    if (!request.smiles?.trim()) throw new Error('Ligand 类型需要填写 SMILES')
    return
  }
  if (!request.sequence?.trim() && (!request.sequenceRecords || request.sequenceRecords.length === 0)) {
    throw new Error('请填写序列')
  }
}

function normalizeRecordName(name: string | undefined, index: number) {
  const value = (name || '').trim()
  return value || `Sequence ${index + 1}`
}

function validateSequence(sequence: string, type: Exclude<MoleculeType, 'ligand'>) {
  const rule = SEQUENCE_RULES[type]
  if (!rule.fullPattern.test(sequence)) {
    const invalidChars = Array.from(new Set(sequence.split('').filter(char => !rule.charPattern.test(char)))).join(', ')
    throw new Error(`序列格式不合法。${rule.hint}；当前检测到非法字符: ${invalidChars || '未知字符'}`)
  }
}

export function parseSequenceRecords(raw: string, type: Exclude<MoleculeType, 'ligand'>): ParsedSequenceRecord[] {
  const lines = raw
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean)

  if (lines.length === 0) throw new Error('请填写序列')

  const records: ParsedSequenceRecord[] = []
  let currentName: string | undefined
  let currentChunks: string[] = []
  let sawHeader = false
  let headerCount = 0

  for (const line of lines) {
    if (line.startsWith('>')) {
      sawHeader = true
      headerCount += 1
      if (currentChunks.length > 0) {
        const sequence = currentChunks.join('').replace(/\s+/g, '').toUpperCase()
        if (!sequence) throw new Error(`第 ${records.length + 1} 条记录未解析到有效序列`)
        validateSequence(sequence, type)
        records.push({
          name: normalizeRecordName(currentName, records.length),
          sequence,
        })
        currentChunks = []
      }
      currentName = line.replace(/^>/, '').trim()
      continue
    }
    currentChunks.push(line.replace(/\s+/g, ''))
  }

  if (currentChunks.length > 0) {
    const sequence = currentChunks.join('').replace(/\s+/g, '').toUpperCase()
    if (!sequence) throw new Error(`第 ${records.length + 1} 条记录未解析到有效序列`)
    validateSequence(sequence, type)
    records.push({
      name: normalizeRecordName(currentName, records.length),
      sequence,
    })
  }

  if (!sawHeader) {
    const sequence = records[0]?.sequence || ''
    if (!sequence) throw new Error('未解析到有效序列，请检查输入内容')
    return [{
      name: 'Sequence 1',
      sequence,
    }]
  }

  if (headerCount > 0 && records.length === 0) {
    throw new Error('未解析到有效 FASTA 序列，请检查输入内容')
  }

  return records
}

export function normalizeSequenceInput(raw: string, type: Exclude<MoleculeType, 'ligand'>): string {
  const records = parseSequenceRecords(raw, type)
  if (records.length > 1) {
    throw new Error('检测到多条 FASTA 记录。当前为单条提交模式，请切换为多条提交，或仅保留一条记录。')
  }
  return records[0].sequence
}

function normalizeSequenceRecordsInput(request: PredictionRequest): ParsedSequenceRecord[] {
  if (request.type === 'ligand') return []
  const type = request.type
  const provided = request.sequenceRecords || []
  if (provided.length > 0) {
    return provided.map((record, index) => {
      const sequence = (record.sequence || '').replace(/\s+/g, '').toUpperCase()
      if (!sequence) throw new Error(`第 ${index + 1} 条记录未解析到有效序列`)
      validateSequence(sequence, type)
      return {
        name: normalizeRecordName(record.name, index),
        sequence,
      }
    })
  }

  const sequence = normalizeSequenceInput(request.sequence || '', type)
  return [{
    name: request.name,
    sequence,
  }]
}

function looksLikeJson(text: string) {
  const t = text.trim()
  return t.startsWith('{') || t.startsWith('[')
}

function normalizeName(name: string) {
  const n = name.trim()
  return n || 'Unnamed'
}

function getProviderLabel(provider: Exclude<PredictionConfig['provider'], 'minifold'>) {
  if (provider === 'biohub') return 'Biohub'
  if (provider === 'nvidia') return 'NVIDIA ESMFold'
  return 'Chai-1'
}

function formatHttpError(
  provider: Exclude<PredictionConfig['provider'], 'minifold'>,
  response: Response,
  responseText: string
) {
  const label = getProviderLabel(provider)
  const body = responseText.trim()

  if (response.status === 451) {
    return `${label} 当前返回 451，说明该服务在当前地区、网络出口或合规策略下不可用。请切换其他 Provider，或改为由部署在合规环境中的后端代为请求。`
  }

  if (response.status === 401) {
    return `${label} 鉴权失败，请检查 API Key 是否正确。`
  }

  if (response.status === 403) {
    return `${label} 拒绝访问，请检查 API Key 权限、账户状态或服务白名单配置。`
  }

  if (response.status === 429) {
    return `${label} 请求过于频繁或额度已用尽，请稍后重试。`
  }

  if (response.status >= 500) {
    return `${label} 服务端异常 (${response.status})。${body || '请稍后重试或切换其他 Provider。'}`
  }

  return body || `${label} 请求失败 (${response.status})`
}

async function readResponseText(response: Response) {
  try {
    return await response.text()
  } catch (e) {
    return ''
  }
}

async function requestWithGuidance(
  provider: Exclude<PredictionConfig['provider'], 'minifold'>,
  input: RequestInfo | URL,
  init: RequestInit
) {
  try {
    const response = await fetch(input, init)
    const text = await readResponseText(response)
    if (!response.ok) {
      throw new Error(formatHttpError(provider, response, text))
    }
    return text
  } catch (e: any) {
    if (e instanceof Error) {
      const message = e.message || ''
      if (message.includes('Failed to fetch') || message.includes('ERR_FAILED')) {
        throw new Error(`${getProviderLabel(provider)} 网络请求失败。若你在本地开发环境，请确认 Vite 已重启并走代理；若已走代理仍失败，则可能是目标服务不可达、被网络策略拦截，或仍受上游合规限制。`)
      }
      throw e
    }
    throw new Error(`${getProviderLabel(provider)} 请求失败`)
  }
}

export async function predictStructure(config: PredictionConfig, request: PredictionRequest): Promise<PredictionResult> {
  if (config.provider === 'minifold') throw new Error('MiniFold 已预留接口，你无需操作')

  assertApiKey(config)
  if (!request.name?.trim()) throw new Error('请填写名称')
  if (!request.type) throw new Error('请选择分子类型')
  assertSequenceFor(request.type, request)

  const normalizedRecords = normalizeSequenceRecordsInput(request)
  const normalizedSequence = normalizedRecords[0]?.sequence

  if (config.provider === 'biohub') {
    if (request.type !== 'protein' && request.type !== 'RNA' && request.type !== 'DNA') {
      throw new Error('Biohub 仅支持 protein/RNA/DNA')
    }
    if (normalizedRecords.length !== 1) {
      throw new Error('Biohub 当前仅支持单条序列（单链）预测。多链复合体请切换到 Chai-1。')
    }

    const baseUrl = pickBaseUrl(config)
    const text = await requestWithGuidance(config.provider, `${baseUrl}/api/v1/fold`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${config.apiKey.trim()}`,
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify({
        model: request.model?.trim() || 'esmfold2-fast-2026-05',
        sequence: normalizedSequence,
      }),
    })
    const body = JSON.parse(text) as { pdb?: string; plddt?: number; ptm?: number }
    if (!body?.pdb?.trim()) throw new Error('Biohub 返回结构为空')

    return {
      providerName: 'Biohub',
      modelName: request.model?.trim() || 'esmfold2-fast-2026-05',
      format: 'pdb',
      structure: body.pdb,
      plddt: body.plddt,
      ptm: body.ptm,
    }
  }

  if (config.provider === 'nvidia') {
    if (request.type !== 'protein') throw new Error('NVIDIA ESMFold 仅支持 protein')
    if (normalizedRecords.length !== 1) {
      throw new Error('NVIDIA ESMFold 当前仅支持单条序列（单链）预测。多链复合体请切换到 Chai-1。')
    }

    const baseUrl = pickBaseUrl(config)
    const text = await requestWithGuidance(config.provider, `${baseUrl}/v1/biology/nvidia/esmfold`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${config.apiKey.trim()}`,
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify({ sequence: normalizedSequence }),
    })

    if (looksLikeJson(text)) {
      const body = JSON.parse(text) as {
        pdb?: string
        structure?: string
        pdbs?: string[]
        plddt?: number
        ptm?: number
      }
      const pdb = body.pdb || body.structure || body.pdbs?.[0]
      if (!pdb?.trim()) throw new Error('NVIDIA 返回结构为空')
      return {
        providerName: 'NVIDIA',
        modelName: 'esmfold',
        format: 'pdb',
        structure: pdb,
        plddt: body.plddt,
        ptm: body.ptm,
      }
    }

    if (!text.trim()) throw new Error('NVIDIA 返回结构为空')
    return {
      providerName: 'NVIDIA',
      modelName: 'esmfold',
      format: 'pdb',
      structure: text,
    }
  }

  if (config.provider === 'chai1') {
    const baseUrl = pickBaseUrl(config)
    const item = {
      molecules: [
        ...(request.type === 'ligand'
          ? [{ name: normalizeName(request.name), type: 'ligand', smiles: request.smiles }]
          : normalizedRecords.map(record => ({ name: normalizeName(record.name), type: request.type, sequence: record.sequence }))),
      ],
    }

    const text = await requestWithGuidance(config.provider, `${baseUrl}/api/v3/chai1/predict`, {
      method: 'POST',
      headers: {
        Authorization: `Token ${config.apiKey.trim()}`,
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify({
        params: {
          num_trunk_recycles: 3,
          num_diffusion_timesteps: 180,
          num_diffn_samples: 1,
          use_esm_embeddings: true,
          seed: 42,
          include: [],
        },
        items: [item],
      }),
    })
    const body = JSON.parse(text) as {
      items?: Array<{ outputs?: { cif?: string; avg_plddt?: number } }>
    }
    const cif = body.items?.[0]?.outputs?.cif
    if (!cif?.trim()) throw new Error('Chai-1 返回结构为空')

    return {
      providerName: 'Chai-1 (bioLM.ai)',
      modelName: request.model?.trim() || 'chai1',
      format: 'mmcif',
      structure: cif,
      plddt: body.items?.[0]?.outputs?.avg_plddt,
    }
  }

  throw new Error('不支持的 provider')
}
