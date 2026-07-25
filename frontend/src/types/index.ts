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
  code: string
  sourceType: string
  moleculeType?: 'protein' | 'RNA'
  accession: string
  proteinName: string
  organismName: string
  description?: string
  taxId?: string
  sequenceLength: number
  sequenceHash: string
  structureType?: string
  structureId?: string
  structureSourceDb?: string
  structureUrl?: string
  ncbiAccession?: string
  ncbiUrl?: string
  ncbiProteinAccession?: string
  ncbiProteinUrl?: string
  uniprotAccession?: string
  uniprotUrl?: string
  pdbId?: string
  pdbUrl?: string
  createdAt: string
}

export type EnzymeAnnotationType = 'DOMAIN' | 'ACTIVE_SITE' | 'MUTATION'

export interface EnzymeAnnotation {
  id: number
  enzymeId: number
  annotationType: EnzymeAnnotationType
  title: string
  startResidue: number
  endResidue: number
  chainLabel?: string
  mutationLabel?: string
  colorHex: string
  description?: string
  sourceDb?: string
  sourceRef?: string
  createdAt: string
  updatedAt: string
}

export interface LiteratureRecord {
  id: number
  relationId?: number
  enzymeId?: number
  title: string
  authors: string
  journal: string
  publishYear: number
  doi?: string
  pmid: string
  abstractText?: string
  sourceDb?: string
  sourceUrl?: string
  attachmentStatus?: string
  attachmentFileName?: string
  attachmentContentType?: string
  attachmentSize?: number
  attachmentSourceUrl?: string
  createdAt: string
  confidenceScore?: number
  confidenceLevel?: string
  matchedEnzymeName?: string
  matchedEnzymeAccession?: string
  matchedFields?: string
  savedToLibrary?: boolean
}

export type PredictionProvider = 'nvidia' | 'minifold' | 'trrosettarna'

export type MoleculeType = 'protein' | 'RNA' | 'DNA'

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
  envText?: string
}

export interface PredictionResult {
  providerName: string
  modelName: string
  format: 'pdb' | 'mmcif'
  structure: string
  sequence?: string
  plddt?: number
  ptm?: number
  resultPageUrl?: string
  analysis?: string
  taskId?: string
}

export interface PredictionTask {
  id: string
  engineTaskId?: string
  createdAt: string
  status: 'running' | 'success' | 'error'
  provider: PredictionProvider
  moleculeType: MoleculeType
  name: string
  inputSequence?: string
  sequenceLength?: number
  error?: string
  result?: PredictionResult
}
