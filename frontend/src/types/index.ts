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
  createdAt: string
  confidenceScore?: number
  confidenceLevel?: string
  matchedEnzymeName?: string
  matchedEnzymeAccession?: string
  matchedFields?: string
  savedToLibrary?: boolean
}

export type PredictionProvider = 'biohub' | 'nvidia' | 'chai1' | 'rnafold' | 'minifold'

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
  envText?: string
  ssn?: number
  threshold?: number
}

export interface PredictionResult {
  providerName: string
  modelName: string
  format: 'pdb' | 'mmcif' | 'dot-bracket'
  structure: string
  sequence?: string
  plddt?: number
  ptm?: number
  resultPageUrl?: string
  mfeStructure?: string
  mfeEnergy?: number
  ensembleFreeEnergy?: number
  mfeFrequency?: number
  ensembleDiversity?: number
  centroidStructure?: string
  centroidEnergy?: number
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
  sequenceLength?: number
  error?: string
  result?: PredictionResult
}
