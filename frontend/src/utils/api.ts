import type { EnzymeEntry, ImportTask, LiteratureRecord } from '@/types'

interface ImportRequest {
  taskName: string
  accessions: string[]
  ncbiEmail?: string
  ncbiApiKey?: string
}

interface MatchRequest {
  ncbiEmail?: string
  ncbiApiKey?: string
  enzymeIds?: number[]
}

interface RnaFoldRequest {
  name: string
  sequence: string
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const token = localStorage.getItem('token')
  const response = await fetch(url, {
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    ...init,
  })

  if (response.status === 401) {
    localStorage.removeItem('token')
    window.location.href = '/login'
    throw new Error('未授权，请重新登录')
  }

  if (!response.ok) {
    const text = await response.text().catch(() => null)
    let message = '请求失败'
    try {
      if (text) {
        const body = JSON.parse(text)
        message = body?.message ?? message
      }
    } catch (e) {
      message = text || message
    }
    throw new Error(message)
  }

  const text = await response.text()
  if (!text) return null as T
  
  try {
    return JSON.parse(text) as T
  } catch (e) {
    return text as unknown as T
  }
}

export function importAccessions(payload: ImportRequest) {
  return request<ImportTask>('/api/imports/ncbi/accessions', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getImportTask(taskId: number) {
  return request<ImportTask>(`/api/imports/ncbi/tasks/${taskId}`)
}

export async function getLatestImportTask() {
  const token = localStorage.getItem('token')
  const response = await fetch('/api/imports/ncbi/tasks/latest', {
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    }
  })
  if (response.status === 204) {
    return null
  }
  if (response.status === 401) {
    localStorage.removeItem('token')
    window.location.href = '/login'
    return null
  }
  const text = await response.text()
  if (!text.trim()) {
    return null
  }
  return JSON.parse(text) as ImportTask
}

export function listEnzymes() {
  return request<EnzymeEntry[]>('/api/enzymes')
}

export function deleteEnzyme(id: number) {
  return request<void>(`/api/enzymes/${id}`, {
    method: 'DELETE',
  })
}

export function matchLiterature(enzymeId: number, payload: MatchRequest) {
  return request<void>(`/api/enzymes/${enzymeId}/match`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getEnzymeLiteratures(enzymeId: number) {
  return request<LiteratureRecord[]>(`/api/enzymes/${enzymeId}/literatures`)
}

export function importEnzymeLiteratureFile(enzymeId: number, filePath: string) {
  return request<LiteratureRecord>(`/api/enzymes/${enzymeId}/literatures/import`, {
    method: 'POST',
    body: JSON.stringify({ filePath }),
  })
}

export async function uploadEnzymeLiteratureFile(enzymeId: number, file: File) {
  const token = localStorage.getItem('token')
  const formData = new FormData()
  formData.append('file', file)

  const response = await fetch(`/api/enzymes/${enzymeId}/literatures/upload`, {
    method: 'POST',
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: formData,
  })

  if (response.status === 401) {
    localStorage.removeItem('token')
    window.location.href = '/login'
    throw new Error('未授权，请重新登录')
  }

  if (!response.ok) {
    const text = await response.text().catch(() => null)
    throw new Error(text || '上传本地文献失败')
  }

  return response.json() as Promise<LiteratureRecord>
}

export function listAllLiteratures() {
  return request<LiteratureRecord[]>('/api/literatures')
}

export function scanLiteratures(payload: MatchRequest) {
  return request<void>('/api/literatures/scan', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function matchAllLiteratures(data: { ncbiEmail?: string, ncbiApiKey?: string }) {
  return request<void>('/api/literatures/match-all', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export function downloadLiteratureRelation(relationId: number) {
  return request<LiteratureRecord>(`/api/literatures/relations/${relationId}/download`, {
    method: 'POST',
  })
}

export async function downloadLiteratureAttachment(literatureId: number) {
  const token = localStorage.getItem('token')
  const response = await fetch(`/api/literatures/${literatureId}/attachment`, {
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  })

  if (response.status === 401) {
    localStorage.removeItem('token')
    window.location.href = '/login'
    throw new Error('未授权，请重新登录')
  }

  if (!response.ok) {
    throw new Error('下载本地附件失败')
  }

  const disposition = response.headers.get('Content-Disposition') || ''
  const fileNameMatch = disposition.match(/filename="?(.*?)"?$/i)
  const fileName = fileNameMatch?.[1] || `literature-${literatureId}.xml`
  const blob = await response.blob()
  return { blob, fileName }
}

export function getDashboardStats() {
  return request<{
    enzymeCount: number
    successRatio: string
    literatureCoverage: string
    systemStatus: string
  }>('/api/dashboard/stats')
}

export function predictRnaFold(payload: RnaFoldRequest) {
  return request<any>('/api/prediction/rnafold', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function predictMiniFold(payload: any) {
  return request<any>('/api/prediction/minifold', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getMiniFoldLogs(taskId: string) {
  return request<string>(`/api/prediction/minifold/logs/${taskId}`)
}

export function getMiniFoldResult(taskId: string) {
  return request<any>(`/api/prediction/minifold/result/${taskId}`)
}
