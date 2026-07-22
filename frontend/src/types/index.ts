export interface ImportTaskItem {
  accession: string
  status: string
  message: string
  enzymeId?: number
}

export interface ImportTask {
  id: number
  taskName: string
  status: string
  totalCount: number
  successCount: number
  failedCount: number
  duplicateCount: number
  createdAt: string
  finishedAt: string
  items: ImportTaskItem[]
}

export interface EnzymeEntry {
  id: number
  accession: string
  proteinName: string
  organismName: string
  taxId?: string
  sequenceLength: number
  sequenceHash: string
  createdAt: string
}
