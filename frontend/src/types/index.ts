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

export type PredictionProvider = 'biohub' | 'nvidia' | 'chai1' | 'minifold'

export type MoleculeType = 'protein' | 'RNA' | 'DNA' | 'ligand'

export interface PredictionConfig {
  provider: PredictionProvider
  apiKey: string
  baseUrl?: string
  rememberApiKey?: boolean
}

export interface PredictionRequest {
  name: string
  type: MoleculeType
  model?: string
  sequence?: string
  sequenceRecords?: Array<{ name: string; sequence: string }>
  smiles?: string
}

export interface PredictionResult {
  providerName: string
  modelName: string
  format: 'pdb' | 'mmcif'
  structure: string
  plddt?: number
  ptm?: number
}

export interface PredictionTask {
  id: string
  createdAt: string
  status: 'running' | 'success' | 'error'
  provider: PredictionProvider
  moleculeType: MoleculeType
  name: string
  sequenceLength?: number
  error?: string
  result?: PredictionResult
}
