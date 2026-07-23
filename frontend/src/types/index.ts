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
  structureType?: string
  structureId?: string
  structureSourceDb?: string
  structureUrl?: string
  ncbiProteinAccession?: string
  ncbiProteinUrl?: string
  uniprotAccession?: string
  uniprotUrl?: string
  pdbId?: string
  pdbUrl?: string
  createdAt: string
}
