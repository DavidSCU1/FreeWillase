<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { 
  Search, 
  Filter, 
  FlaskConical, 
  Dna, 
  BookOpen, 
  ExternalLink, 
  Database, 
  Info, 
  Layers, 
  MapPin, 
  Tag, 
  Maximize2, 
  Loader2,
  Trash2,
  Sparkles,
  Upload,
  X,
  Plus,
  Pencil,
  Check,
  AlertCircle,
  Wand2,
  MousePointerClick
} from 'lucide-vue-next'
import { createEmptyAnnotationForm, toAnnotationForm, useEnzymeAnnotations } from '@/composables/useEnzymeAnnotations'
import { useLiterature } from '@/composables/useLiterature'
import StructureViewer from '@/components/StructureViewer.vue'
import { deleteEnzyme, downloadLiteratureAttachment, getEnzymeStructure, listEnzymes } from '@/utils/api'
import type { EnzymeAnnotation, EnzymeAnnotationType, EnzymeEntry } from '@/types'

const router = useRouter()
const route = useRoute()
const enzymes = ref<EnzymeEntry[]>([])
const {
  enzymeLiteratures,
  fetchEnzymeLiteratures,
  enzymeLoading: loadingLit,
  importingEnzymeId,
  importLocalLiterature,
} = useLiterature()
const {
  annotations,
  hasAnnotations,
  listLoading: annotationsLoading,
  saving: annotationSaving,
  deletingId: deletingAnnotationId,
  importing: importingAnnotations,
  fetchAnnotations,
  saveAnnotation,
  removeAnnotation,
  importAutomatically,
} = useEnzymeAnnotations()

const selectedId = ref<number | null>(null)
const searchQuery = ref('')
const showFullscreenViewer = ref(false)
const showImportLiteratureModal = ref(false)
const showAnnotationModal = ref(false)
const isDeleting = ref(false)
const selectedLiteratureId = ref<number | null>(null)
const selectedAnnotationId = ref<number | null>(null)
const downloadingAttachmentId = ref<number | null>(null)
const importLiteratureFile = ref<File | null>(null)
const importLiteratureError = ref('')
const annotationForm = ref(createEmptyAnnotationForm())
const annotationError = ref('')
const editingAnnotationId = ref<number | null>(null)
const annotationNotice = ref('')
const predictedStructureUrl = ref<string | null>(null)
const structurePickMode = ref(false)
const attemptedAutoImportAnnotationIds = new Set<number>()

const annotationTypeOptions: Array<{ value: EnzymeAnnotationType; label: string; hint: string; color: string }> = [
  { value: 'DOMAIN', label: '结构域', hint: '连续残基区间', color: '#3B82F6' },
  { value: 'ACTIVE_SITE', label: '活性位点', hint: '关键催化残基', color: '#10B981' },
  { value: 'MUTATION', label: '突变位点', hint: '关注的单个位点', color: '#F97316' },
] as const

const libraryTabs = [
  {
    label: '导入酶库',
    to: '/library/imported',
    hint: 'Accession 导入',
    sourceType: 'NCBI_IMPORT',
  },
  {
    label: '预测成果库',
    to: '/library/predicted',
    hint: 'MiniFold 入库',
    sourceType: 'MINIFOLD_PREDICTION',
  },
] as const

const activeSourceType = computed(() => String(route.meta.librarySourceType || 'NCBI_IMPORT'))
const isPredictedLibrary = computed(() => activeSourceType.value === 'PREDICTED')
const libraryTitle = computed(() => String(route.meta.libraryTitle || '酶库中心'))
const librarySubtitle = computed(() => String(route.meta.librarySubtitle || '管理、浏览与分析本地酶条目数据库'))
const searchPlaceholder = computed(() => isPredictedLibrary.value ? '搜索内部编号或预测名称...' : '搜索 Accession 或条目名称...')
const identifierLabel = computed(() => isPredictedLibrary.value ? '内部编号' : 'Accession')
const selectedEntryBadge = computed(() => isPredictedLibrary.value ? '已入库的预测条目' : 'Accession 导入条目')
const emptyTitle = computed(() => isPredictedLibrary.value ? '还没有确认入库的预测结果' : '这里还没有 accession 导入条目')
const emptyDescription = computed(() => isPredictedLibrary.value
  ? '先去预测工作台拿到结果，确认命名后再放进预测成果库，这里就会出现。'
  : '请先从 NCBI Accession 导入，再回来浏览这批正式入库的酶条目。')

function revokePredictedStructureUrl() {
  if (!predictedStructureUrl.value) return
  if (predictedStructureUrl.value.startsWith('blob:')) {
    URL.revokeObjectURL(predictedStructureUrl.value)
  }
  predictedStructureUrl.value = null
}

async function handleDelete(id: number) {
  if (confirm('确定要放走这只酶吗？一旦放归野外（删除），它的自由意志就不再受你掌控了。')) {
    isDeleting.value = true
    try {
      await deleteEnzyme(id)
      selectedId.value = null
      await refreshEnzymeLibrary()
    } catch (error) {
      console.error('删除失败', error)
    } finally {
      isDeleting.value = false
    }
  }
}

function handleOpenMatcher() {
  if (!selectedId.value) {
    router.push('/matcher')
    return
  }
  router.push({
    path: '/matcher',
    query: { enzymeId: String(selectedId.value) },
  })
}

function handleOpenRnaPrediction() {
  if (!selectedEnzyme.value || !selectedId.value || selectedEnzyme.value.moleculeType !== 'RNA') return
  router.push({
    path: '/prediction/trrosettarna',
    query: {
      enzymeId: String(selectedId.value),
      name: selectedEnzyme.value.proteinName || selectedEnzyme.value.accession,
      accession: selectedEnzyme.value.accession,
    },
  })
}

function openImportLiteratureModal() {
  importLiteratureFile.value = null
  importLiteratureError.value = ''
  showImportLiteratureModal.value = true
}

function resetAnnotationForm(type: EnzymeAnnotationType = 'DOMAIN') {
  const option = annotationTypeOptions.find((item) => item.value === type)
  annotationForm.value = {
    ...createEmptyAnnotationForm(),
    annotationType: type,
    colorHex: option?.color || '#3B82F6',
  }
}

function openAnnotationModal(type: EnzymeAnnotationType = 'DOMAIN') {
  editingAnnotationId.value = null
  annotationError.value = ''
  annotationNotice.value = ''
  resetAnnotationForm(type)
  showAnnotationModal.value = true
}

function editAnnotation(annotation: EnzymeAnnotation) {
  editingAnnotationId.value = annotation.id
  annotationError.value = ''
  annotationNotice.value = ''
  annotationForm.value = toAnnotationForm(annotation)
  showAnnotationModal.value = true
}

function closeAnnotationModal() {
  if (annotationSaving.value) return
  showAnnotationModal.value = false
  annotationError.value = ''
  annotationNotice.value = ''
  editingAnnotationId.value = null
  structurePickMode.value = false
}

function closeImportLiteratureModal() {
  if (importingEnzymeId.value) return
  showImportLiteratureModal.value = false
  importLiteratureError.value = ''
}

function handleImportFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  importLiteratureFile.value = input.files?.[0] ?? null
  importLiteratureError.value = ''
}

async function handleDownloadAttachment(literatureId: number) {
  try {
    downloadingAttachmentId.value = literatureId
    const { blob, fileName } = await downloadLiteratureAttachment(literatureId)
    const objectUrl = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(objectUrl)
  } finally {
    downloadingAttachmentId.value = null
  }
}

async function handleSaveAnnotation() {
  if (!selectedId.value) return
  if (!annotationForm.value.startResidue || annotationForm.value.startResidue <= 0) {
    annotationError.value = '请填写合法的起始残基位点'
    return
  }
  if (annotationForm.value.annotationType === 'DOMAIN' && (!annotationForm.value.endResidue || annotationForm.value.endResidue < annotationForm.value.startResidue)) {
    annotationError.value = '结构域的结束位点不能小于起始位点'
    return
  }

  try {
    annotationError.value = ''
    annotationNotice.value = ''
    const saved = await saveAnnotation(selectedId.value, annotationForm.value, editingAnnotationId.value)
    selectedAnnotationId.value = saved.id
    structurePickMode.value = false
    showAnnotationModal.value = false
  } catch (error) {
    annotationError.value = error instanceof Error ? error.message : '注释保存失败，请重试'
  }
}

async function handleDeleteAnnotation(annotation: EnzymeAnnotation) {
  if (!selectedId.value) return
  if (!confirm(`确定删除注释“${annotation.title}”吗？`)) return
  try {
    await removeAnnotation(selectedId.value, annotation.id)
    if (selectedAnnotationId.value === annotation.id) {
      selectedAnnotationId.value = annotations.value[0]?.id ?? null
    }
  } catch (error) {
    console.error('删除注释失败', error)
  }
}

async function handleImportAnnotations() {
  if (!selectedId.value) return
  annotationError.value = ''
  annotationNotice.value = ''
  try {
    const imported = await importAutomatically(selectedId.value)
    if (imported.length) {
      selectedAnnotationId.value = imported[0].id
      annotationNotice.value = selectedEnzyme.value?.moleculeType === 'RNA'
        ? `已从 NCBI Nucleotide 导入 ${imported.length} 条 RNA 注释`
        : `已从 UniProt / PDB 导入 ${imported.length} 条注释`
      return
    }
    annotationNotice.value = selectedEnzyme.value?.moleculeType === 'RNA'
      ? '没有新的 RNA 注释可导入，当前条目可能缺少可识别 feature 或已存在相同注释'
      : '没有新的 UniProt / PDB 注释可导入，可能已存在或该条目暂无可识别位点'
  } catch (error) {
    annotationError.value = error instanceof Error
      ? error.message
      : (selectedEnzyme.value?.moleculeType === 'RNA' ? 'RNA 注释导入失败' : 'UniProt / PDB 注释导入失败')
  }
}

function handlePickAnnotationFromStructure(type: EnzymeAnnotationType = 'ACTIVE_SITE') {
  annotationError.value = ''
  annotationNotice.value = '请在左侧 3D 结构中点击一个残基，系统会自动回填位点信息'
  if (!showAnnotationModal.value || editingAnnotationId.value) {
    openAnnotationModal(type)
  } else {
    annotationForm.value.annotationType = type
  }
  structurePickMode.value = true
}

function handleResiduePicked(payload: { residueNumber: number; chainLabel?: string; residueName?: string }) {
  if (!showAnnotationModal.value) {
    openAnnotationModal('ACTIVE_SITE')
  }
  annotationForm.value.startResidue = payload.residueNumber
  annotationForm.value.endResidue = payload.residueNumber
  annotationForm.value.chainLabel = payload.chainLabel || annotationForm.value.chainLabel
  if (!annotationForm.value.title) {
    const suffix = payload.residueName ? `${payload.residueName}${payload.residueNumber}` : `${payload.residueNumber}`
    annotationForm.value.title = annotationForm.value.annotationType === 'MUTATION'
      ? `突变位点 ${suffix}`
      : `活性位点 ${suffix}`
  }
  if (annotationForm.value.annotationType === 'MUTATION' && !annotationForm.value.mutationLabel) {
    annotationForm.value.mutationLabel = payload.residueName ? `${payload.residueName}${payload.residueNumber}` : `位点 ${payload.residueNumber}`
  }
  annotationNotice.value = `已选中残基 ${payload.residueNumber}${payload.chainLabel ? `（链 ${payload.chainLabel}）` : ''}`
}

async function handleImportLiterature() {
  if (!selectedId.value) return
  if (!importLiteratureFile.value) {
    importLiteratureError.value = '请选择要导入的本地文件'
    return
  }

  try {
    importLiteratureError.value = ''
    const imported = await importLocalLiterature(selectedId.value, importLiteratureFile.value)
    selectedLiteratureId.value = imported.id
    showImportLiteratureModal.value = false
    importLiteratureFile.value = null
  } catch (error) {
    importLiteratureError.value = error instanceof Error ? error.message : '导入失败，请重试'
  }
}

async function refreshEnzymeLibrary() {
  enzymes.value = await listEnzymes(activeSourceType.value)
}

function applyRouteSelection() {
  const routeEnzymeId = Number(route.query.enzymeId)
  if (routeEnzymeId && enzymes.value.some(item => item.id === routeEnzymeId)) {
    selectedId.value = routeEnzymeId
    return
  }
  if (selectedId.value != null && enzymes.value.some(item => item.id === selectedId.value)) {
    return
  }
  selectedId.value = enzymes.value[0]?.id ?? null
}

const filteredEnzymes = computed(() => {
  if (!searchQuery.value) return enzymes.value
  const q = searchQuery.value.toLowerCase()
  return enzymes.value.filter(e => 
    (e.accession || '').toLowerCase().includes(q) ||
    (e.code || '').toLowerCase().includes(q) ||
    (e.proteinName || '').toLowerCase().includes(q) ||
    (e.organismName || '').toLowerCase().includes(q)
  )
})

const selectedEnzyme = computed(() => {
  if (!enzymes.value.length) return null
  if (selectedId.value == null) return enzymes.value[0]
  return enzymes.value.find((item) => item.id === selectedId.value) ?? enzymes.value[0]
})

const isRnaEntry = computed(() => selectedEnzyme.value?.moleculeType === 'RNA')
const selectedSequenceUnit = computed(() => selectedEnzyme.value?.moleculeType === 'RNA' ? 'nt' : 'aa')
const canImportAutomaticAnnotations = computed(() => !isPredictedLibrary.value)
const selectedNcbiSourceLabel = computed(() => {
  if (isPredictedLibrary.value) return 'SOURCE'
  return selectedEnzyme.value?.moleculeType === 'RNA' ? 'NCBI Nucleotide' : 'NCBI Protein'
})
const selectedSecondarySourceLabel = computed(() => {
  if (isPredictedLibrary.value) return 'Library Code'
  return selectedEnzyme.value?.moleculeType === 'RNA' ? 'RNA 注释源' : 'UniProt'
})
const importedAnnotationSourceLabels = computed(() => {
  const labels = new Set<string>()
  annotations.value.forEach((item) => {
    if (item.sourceDb === 'UNIPROT') labels.add('UniProt')
    else if (item.sourceDb === 'PDB') labels.add('PDB')
    else if (item.sourceDb === 'NCBI_NUCLEOTIDE') labels.add('NCBI Nucleotide')
  })
  return Array.from(labels)
})
const selectedSecondarySourceValue = computed(() => {
  if (isPredictedLibrary.value) return selectedEnzyme.value?.code || '-'
  if (selectedEnzyme.value?.moleculeType === 'RNA') {
    return importedAnnotationSourceLabels.value.length ? importedAnnotationSourceLabels.value.join(' / ') : '可从 NCBI 导入'
  }
  return selectedEnzyme.value?.uniprotAccession || '-'
})
const hasCuratedStructure = computed(() => {
  const enzyme = selectedEnzyme.value
  if (!enzyme) return false
  return Boolean(enzyme.structureUrl || enzyme.structureId || enzyme.pdbId)
})
const canRenderStructureViewer = computed(() => {
  if (isPredictedLibrary.value) return Boolean(predictedStructureUrl.value)
  if (isRnaEntry.value) return hasCuratedStructure.value
  return true
})
const selectedStructureCardTitle = computed(() => isRnaEntry.value ? 'RNA 结构与预测' : '3D 结构可视化')
const selectedStructureSectionDescription = computed(() => {
  if (isRnaEntry.value) {
    return '当前优先展示 RNA 条目的结构可用性与后续预测建议。'
  }
  return '自动展示蛋白结构，并支持与注释联动查看。'
})
const selectedAnnotationToolTitle = computed(() => isRnaEntry.value ? 'RNA 序列注释工具' : '结构注释工具')
const selectedAnnotationToolDescription = computed(() => {
  if (isRnaEntry.value) {
    return '支持从 NCBI Nucleotide 导入基础 RNA 注释，并按序列区间继续手动补充。'
  }
  return '标注结构域、活性位点与突变位点，并联动 3D 视图查看'
})
const selectedImportedAnnotationLabel = computed(() => {
  if (isRnaEntry.value) return '自动导入（NCBI Nucleotide）'
  return '自动导入（UniProt / PDB）'
})
const selectedAnnotationImportButtonLabel = computed(() => {
  if (isRnaEntry.value) return '从 NCBI 导入'
  return '从 UniProt / PDB 导入'
})
const canLaunchRnaPrediction = computed(() => Boolean(
  isRnaEntry.value
  && selectedId.value
  && (selectedEnzyme.value?.sequenceLength || 0) > 0
  && (selectedEnzyme.value?.sequenceLength || 0) <= 400
))
const selectedRnaPredictionButtonLabel = computed(() => {
  if (!isRnaEntry.value) return ''
  return canLaunchRnaPrediction.value ? '送去 RNA 预测' : '超出 RNA 预测上限'
})
const canPickAnnotationFromStructure = computed(() => !isRnaEntry.value && canRenderStructureViewer.value)
const rnaStructureSupportHint = computed(() => {
  const length = selectedEnzyme.value?.sequenceLength || 0
  if (!isRnaEntry.value) return ''
  if (length > 400) {
    return `当前项目内置的 RNA 预测流程仅支持长度 <= 400 nt，本条序列长度为 ${length} nt。`
  }
  return `当前尚未为该 RNA 条目自动接入 3D 结构来源；这条序列长度为 ${length} nt，后续可以补接 RNA 预测入口。`
})

const selectedStructureId = computed(() => {
  const enzyme = selectedEnzyme.value
  if (!enzyme) return ''
  if (isPredictedLibrary.value) return enzyme.code || enzyme.structureId || 'MINIFOLD-LOCAL'
  if (isRnaEntry.value && !hasCuratedStructure.value) return '暂未提供'
  return enzyme.structureId || enzyme.uniprotAccession || enzyme.accession
})

const selectedViewerStructureId = computed(() => {
  if (isRnaEntry.value && !hasCuratedStructure.value) return undefined
  if (isPredictedLibrary.value) return undefined
  return selectedStructureId.value || undefined
})

const selectedStructureUrl = computed(() => {
  const enzyme = selectedEnzyme.value
  if (isPredictedLibrary.value) return predictedStructureUrl.value || undefined
  if (!enzyme?.structureUrl) return undefined
  if (enzyme.structureSourceDb === 'PDB' || enzyme.structureSourceDb === 'AlphaFold') return undefined
  return enzyme.structureUrl
})

const selectedStructureSource = computed(() => {
  const enzyme = selectedEnzyme.value
  if (isPredictedLibrary.value) return 'LOCAL'
  if (isRnaEntry.value && !hasCuratedStructure.value) return 'RNA_PENDING'
  return enzyme?.structureSourceDb || 'AUTO'
})

const selectedStructureFormat = computed<'pdb' | 'mmcif'>(() => {
  const enzyme = selectedEnzyme.value
  const structureUrl = enzyme?.structureUrl?.toLowerCase() || ''
  const structureType = enzyme?.structureType?.toLowerCase() || ''
  if (structureUrl.endsWith('.cif') || structureUrl.endsWith('.mmcif') || structureType.includes('mmcif')) {
    return 'mmcif'
  }
  return 'pdb'
})

const selectedStructureType = computed(() => {
  const enzyme = selectedEnzyme.value
  return enzyme?.structureType || 'AUTO'
})

const selectedStructureStatus = computed(() => {
  const enzyme = selectedEnzyme.value
  if (!enzyme) return '等待加载'
  if (isPredictedLibrary.value) return '预测已确认入库'
  if (isRnaEntry.value && !hasCuratedStructure.value) return 'RNA 结构暂未接入'
  if (enzyme.structureSourceDb === 'PDB') return 'Experimental (PDB)'
  if (enzyme.structureSourceDb === 'AlphaFold') return 'Predicted (AlphaFold)'
  if (enzyme.structureSourceDb) return `Curated (${enzyme.structureSourceDb})`
  return 'Auto-Retrieved'
})

const selectedNcbiUrl = computed(() => {
  const enzyme = selectedEnzyme.value
  if (!enzyme || isPredictedLibrary.value) return undefined
  if (enzyme.ncbiUrl) return enzyme.ncbiUrl
  if (!enzyme.accession) return undefined
  return enzyme.moleculeType === 'RNA'
    ? `https://www.ncbi.nlm.nih.gov/nuccore/${enzyme.accession}`
    : `https://www.ncbi.nlm.nih.gov/protein/${enzyme.accession}`
})

const selectedUniprotUrl = computed(() => isPredictedLibrary.value ? undefined : selectedEnzyme.value?.uniprotUrl)
const selectedLiterature = computed(() => {
  if (!enzymeLiteratures.value.length) return null
  if (selectedLiteratureId.value == null) return enzymeLiteratures.value[0]
  return enzymeLiteratures.value.find((item) => item.id === selectedLiteratureId.value) ?? enzymeLiteratures.value[0]
})
const selectedAnnotation = computed(() => {
  if (!annotations.value.length) return null
  if (selectedAnnotationId.value == null) return annotations.value[0]
  return annotations.value.find((item) => item.id === selectedAnnotationId.value) ?? annotations.value[0]
})
const annotationLegend = computed(() => {
  return annotationTypeOptions.map((option) => ({
    ...option,
    count: annotations.value.filter((item) => item.annotationType === option.value).length,
  }))
})
const annotationSequenceSegments = computed(() => {
  const sequenceLength = Math.max(selectedEnzyme.value?.sequenceLength || 0, 1)
  return annotations.value.map((annotation) => {
    const start = Math.max(1, annotation.startResidue)
    const end = Math.max(start, annotation.endResidue)
    const width = Math.max(((end - start + 1) / sequenceLength) * 100, 1.5)
    return {
      ...annotation,
      left: `${((start - 1) / sequenceLength) * 100}%`,
      width: `${Math.min(width, 100)}%`,
    }
  })
})
const selectedAnnotationSummary = computed(() => {
  if (!selectedAnnotation.value) return null
  if (selectedAnnotation.value.annotationType === 'DOMAIN') {
    return `残基 ${selectedAnnotation.value.startResidue}-${selectedAnnotation.value.endResidue}`
  }
  if (selectedAnnotation.value.annotationType === 'ACTIVE_SITE') {
    return `活性位点残基 ${selectedAnnotation.value.startResidue}`
  }
  return selectedAnnotation.value.mutationLabel || `突变位点 ${selectedAnnotation.value.startResidue}`
})
const annotationImportedCount = computed(() => annotations.value.filter((item) => ['UNIPROT', 'PDB', 'NCBI_NUCLEOTIDE'].includes(item.sourceDb || '')).length)
const manualAnnotationCount = computed(() => annotations.value.filter((item) => !['UNIPROT', 'PDB', 'NCBI_NUCLEOTIDE'].includes(item.sourceDb || '')).length)

watch(
  () => selectedId.value,
  (id) => {
    selectedAnnotationId.value = null
    if (id) {
      fetchAnnotations(id)
    } else {
      selectedAnnotationId.value = null
    }
    if (id && !isPredictedLibrary.value) {
      selectedLiteratureId.value = null
      fetchEnzymeLiteratures(id)
    } else {
      enzymeLiteratures.value = []
      selectedLiteratureId.value = null
    }
    if (!id) {
      selectedLiteratureId.value = null
    }
  }
)

watch(
  () => annotationForm.value.annotationType,
  (type) => {
    const option = annotationTypeOptions.find((item) => item.value === type)
    if (!editingAnnotationId.value && option) {
      annotationForm.value.colorHex = option.color
    }
    if (type === 'MUTATION' && annotationForm.value.startResidue) {
      annotationForm.value.endResidue = annotationForm.value.startResidue
    }
    if (type === 'ACTIVE_SITE' && annotationForm.value.startResidue && !annotationForm.value.endResidue) {
      annotationForm.value.endResidue = annotationForm.value.startResidue
    }
  },
)

watch(
  () => annotationForm.value.startResidue,
  (startResidue) => {
    if (!startResidue) return
    if (annotationForm.value.annotationType !== 'DOMAIN') {
      annotationForm.value.endResidue = startResidue
    }
  },
)

watch(
  () => enzymeLiteratures.value,
  (list) => {
    selectedLiteratureId.value = list.length ? list[0].id : null
  },
  { immediate: true },
)

watch(
  () => annotations.value,
  (list) => {
    selectedAnnotationId.value = list.length ? (list.find((item) => item.id === selectedAnnotationId.value)?.id ?? list[0].id) : null
  },
  { immediate: true },
)

watch(
  () => [selectedEnzyme.value?.id, annotationsLoading.value, annotations.value.length, isPredictedLibrary.value] as const,
  async ([enzymeId, loading, annotationCount, predicted]) => {
    // #region debug-point D:auto-import-gate
    fetch('http://127.0.0.1:7777/event', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sessionId: 'structure-empty-annotations', runId: 'post-fix', hypothesisId: 'D', location: 'EnzymesPage.vue:autoImportWatch:514', msg: '[DEBUG] auto import watch evaluated', data: { enzymeId, loading, annotationCount, predicted, hasAttempted: enzymeId ? attemptedAutoImportAnnotationIds.has(enzymeId) : false, uniprotAccession: selectedEnzyme.value?.uniprotAccession || null, pdbId: selectedEnzyme.value?.pdbId || null }, ts: Date.now() }) }).catch(() => {})
    // #endregion
    if (!enzymeId || loading || predicted || annotationCount > 0 || attemptedAutoImportAnnotationIds.has(enzymeId)) {
      return
    }
    const enzyme = selectedEnzyme.value
    if (!enzyme) {
      return
    }
    if (enzyme.moleculeType !== 'RNA' && !enzyme.uniprotAccession && !enzyme.pdbId) {
      // #region debug-point D:auto-import-skipped
      fetch('http://127.0.0.1:7777/event', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sessionId: 'structure-empty-annotations', runId: 'post-fix', hypothesisId: 'D', location: 'EnzymesPage.vue:autoImportWatch:520', msg: '[DEBUG] auto import skipped for missing uniprot accession', data: { enzymeId, pdbId: enzyme?.pdbId || null, structureId: enzyme?.structureId || null }, ts: Date.now() }) }).catch(() => {})
      // #endregion
      attemptedAutoImportAnnotationIds.add(enzymeId)
      return
    }
    attemptedAutoImportAnnotationIds.add(enzymeId)
    try {
      // #region debug-point E:auto-import-start
      fetch('http://127.0.0.1:7777/event', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sessionId: 'structure-empty-annotations', runId: 'post-fix', hypothesisId: 'E', location: 'EnzymesPage.vue:autoImportWatch:526', msg: '[DEBUG] auto import started', data: { enzymeId, uniprotAccession: enzyme.uniprotAccession, pdbId: enzyme.pdbId || null }, ts: Date.now() }) }).catch(() => {})
      // #endregion
      const imported = await importAutomatically(enzymeId)
      // #region debug-point E:auto-import-result
      fetch('http://127.0.0.1:7777/event', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sessionId: 'structure-empty-annotations', runId: 'post-fix', hypothesisId: 'E', location: 'EnzymesPage.vue:autoImportWatch:528', msg: '[DEBUG] auto import finished', data: { enzymeId, importedCount: imported.length, sourceDbs: imported.map((item) => item.sourceDb || null) }, ts: Date.now() }) }).catch(() => {})
      // #endregion
      if (imported.length) {
        annotationNotice.value = enzyme.moleculeType === 'RNA'
          ? `已自动从 NCBI Nucleotide 补充 ${imported.length} 条初始 RNA 注释`
          : `已自动从 UniProt / PDB 补充 ${imported.length} 条初始注释`
      }
    } catch (error) {
      // #region debug-point E:auto-import-error
      fetch('http://127.0.0.1:7777/event', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sessionId: 'structure-empty-annotations', runId: 'post-fix', hypothesisId: 'E', location: 'EnzymesPage.vue:autoImportWatch:533', msg: '[DEBUG] auto import failed', data: { enzymeId, error: error instanceof Error ? error.message : String(error) }, ts: Date.now() }) }).catch(() => {})
      // #endregion
      console.error('自动补充注释失败', error)
    }
  },
  { immediate: true },
)

watch(
  () => enzymes.value,
  () => {
    applyRouteSelection()
  },
  { immediate: true },
)

watch(
  () => route.query.enzymeId,
  () => {
    applyRouteSelection()
  },
)

watch(
  () => activeSourceType.value,
  async () => {
    searchQuery.value = ''
    selectedId.value = null
    revokePredictedStructureUrl()
    await refreshEnzymeLibrary()
    applyRouteSelection()
  },
)

watch(
  () => [selectedEnzyme.value?.id, isPredictedLibrary.value] as const,
  async ([enzymeId, predicted]) => {
    revokePredictedStructureUrl()
    if (!predicted || !enzymeId) return
    try {
      const structureText = await getEnzymeStructure(enzymeId)
      const blob = new Blob([structureText], { type: 'text/plain' })
      predictedStructureUrl.value = URL.createObjectURL(blob)
    } catch (error) {
      console.error('读取预测结构失败', error)
    }
  },
  { immediate: true },
)

watch(
  () => selectedEnzyme.value,
  (enzyme) => {
    // #region debug-point A:selected-enzyme
    fetch('http://127.0.0.1:7777/event', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sessionId: 'structure-empty-annotations', runId: 'post-fix', hypothesisId: 'A', location: 'EnzymesPage.vue:selectedEnzymeWatch:560', msg: '[DEBUG] selected enzyme payload prepared for viewer', data: enzyme ? { enzymeId: enzyme.id, accession: enzyme.accession, structureId: enzyme.structureId, structureSourceDb: enzyme.structureSourceDb, structureUrl: enzyme.structureUrl, pdbId: enzyme.pdbId, uniprotAccession: enzyme.uniprotAccession, selectedViewerStructureId: selectedViewerStructureId.value, selectedStructureUrl: selectedStructureUrl.value, selectedStructureSource: selectedStructureSource.value } : null, ts: Date.now() }) }).catch(() => {})
    // #endregion
  },
  { immediate: true, deep: true },
)

onMounted(async () => {
  try {
    window.scrollTo({ top: 0, left: 0, behavior: 'auto' })
    await refreshEnzymeLibrary()
    applyRouteSelection()
    if (selectedId.value && !isPredictedLibrary.value) {
      fetchEnzymeLiteratures(selectedId.value)
    }
    if (selectedId.value) {
      fetchAnnotations(selectedId.value)
    }
  } catch {
    // Silent
  }
})

onUnmounted(() => {
  revokePredictedStructureUrl()
})
</script>

<template>
  <div class="flex flex-col h-full space-y-8">
    <!-- Header with Search and Filter -->
    <div class="flex flex-col md:flex-row md:items-center justify-between gap-4">
      <div class="space-y-3">
        <div class="space-y-1">
          <h1 class="text-3xl font-bold tracking-tight text-apple-text">{{ libraryTitle }}</h1>
          <p class="text-apple-secondary-text text-sm">{{ librarySubtitle }}</p>
        </div>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="tab in libraryTabs"
            :key="tab.to"
            type="button"
            class="rounded-apple border px-4 py-2 text-left transition-all"
            :class="activeSourceType === tab.sourceType ? 'border-apple-blue bg-apple-blue/5 text-apple-blue' : 'border-apple-border text-apple-secondary-text hover:text-apple-text hover:bg-apple-background'"
            @click="router.push(tab.to)"
          >
            <p class="text-xs font-bold">{{ tab.label }}</p>
            <p class="text-[10px] uppercase tracking-widest">{{ tab.hint }}</p>
          </button>
        </div>
      </div>
      <div class="flex items-center gap-3">
        <div class="relative w-64">
          <Search class="absolute left-3 top-1/2 -translate-y-1/2 text-apple-secondary-text" :size="14" />
          <input 
            v-model="searchQuery"
            type="text" 
            :placeholder="searchPlaceholder"
            class="apple-input pl-9 pr-4 py-2 text-xs"
          />
        </div>
        <button class="apple-button-secondary flex items-center gap-2 !py-2 !px-4 text-xs">
          <Filter :size="14" />
          筛选器
        </button>
      </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-[380px_1fr] gap-8 items-start">
      <!-- Left List Sidebar -->
      <div class="space-y-6 sticky top-24 h-[calc(100vh-280px)] flex flex-col">
        <div class="apple-card overflow-hidden flex flex-col flex-1">
          <div class="p-4 border-b border-apple-border flex items-center justify-between bg-black/5 dark:bg-white/5">
            <span class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">酶条目列表</span>
            <span class="text-[10px] font-bold text-apple-blue bg-apple-blue/10 px-2 py-0.5 rounded-full">{{ filteredEnzymes.length }}</span>
          </div>
          <div class="flex-1 overflow-y-auto p-2 space-y-1 no-scrollbar">
            <button
              v-for="enzyme in filteredEnzymes"
              :key="enzyme.id"
              @click="selectedId = enzyme.id"
              class="w-full text-left p-3 rounded-apple transition-all group relative overflow-hidden"
              :class="selectedEnzyme?.id === enzyme.id 
                ? 'bg-apple-blue text-white shadow-lg shadow-apple-blue/20' 
                : 'hover:bg-black/5 dark:hover:bg-white/5'"
            >
              <div class="flex justify-between items-start mb-1">
                <span class="text-xs font-bold" :class="selectedEnzyme?.id === enzyme.id ? 'text-white' : 'text-apple-blue'">
                  {{ isPredictedLibrary ? enzyme.code : enzyme.accession }}
                </span>
                <div class="flex items-center gap-2">
                  <span v-if="selectedEnzyme?.id === enzyme.id" class="text-[10px] font-medium opacity-70 italic">
                    {{ enzyme.sequenceLength }} {{ enzyme.moleculeType === 'RNA' ? 'nt' : 'aa' }}
                  </span>
                  <button 
                    @click.stop="handleDelete(enzyme.id)"
                    class="p-1 rounded-full hover:bg-white/20 transition-colors"
                    :class="selectedEnzyme?.id === enzyme.id ? 'text-white' : 'text-red-500 opacity-0 group-hover:opacity-100'"
                    title="删除"
                  >
                    <Trash2 :size="10" />
                  </button>
                </div>
              </div>
              <p class="text-xs font-semibold line-clamp-2 leading-snug" :class="selectedEnzyme?.id === enzyme.id ? 'text-white' : 'text-apple-text'">
                {{ enzyme.proteinName }}
              </p>
              <p class="mt-2 text-[10px] truncate" :class="selectedEnzyme?.id === enzyme.id ? 'text-white/70' : 'text-apple-secondary-text'">
                {{ enzyme.organismName }}
              </p>
            </button>
            
            <div v-if="!filteredEnzymes.length" class="p-12 text-center">
              <FlaskConical :size="32" class="mx-auto text-apple-secondary-text opacity-20 mb-4" />
              <p class="text-xs text-apple-secondary-text">这只酶可能逃出了自由意志的包围圈</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Right Detail Content -->
      <div v-if="selectedEnzyme" class="space-y-8 pb-20">
        <!-- Main Detail Card -->
        <div class="apple-card p-8">
          <div class="flex flex-col md:flex-row justify-between items-start gap-6">
            <div class="space-y-4 flex-1">
              <div class="flex items-center gap-3">
                <span class="px-2 py-1 rounded-full bg-apple-blue/10 text-apple-blue text-[10px] font-bold uppercase tracking-widest">
                  {{ selectedEntryBadge }}
                </span>
                <span class="text-xs font-medium text-apple-secondary-text">ID: {{ selectedEnzyme.id }}</span>
              </div>
              <h2 class="text-3xl font-bold tracking-tight text-apple-text leading-tight">
                {{ selectedEnzyme.proteinName }}
              </h2>
              <div class="flex flex-wrap gap-4 text-sm">
                <div class="flex items-center gap-2 text-apple-secondary-text">
                  <Database :size="16" />
                  <span>{{ identifierLabel }}: <span class="text-apple-text font-semibold">{{ isPredictedLibrary ? selectedEnzyme.code : selectedEnzyme.accession }}</span></span>
                </div>
                <div class="flex items-center gap-2 text-apple-secondary-text">
                  <Tag :size="16" />
                  <span>Organism: <span class="text-apple-text font-semibold italic">{{ selectedEnzyme.organismName }}</span></span>
                </div>
              </div>
            </div>
            <div class="flex gap-3">
              <button 
                @click="selectedEnzyme && handleDelete(selectedEnzyme.id)"
                :disabled="isDeleting"
                class="apple-button-secondary !text-red-500 !border-red-500/20 hover:!bg-red-500/5 flex items-center gap-2 !py-2 !px-4 text-xs disabled:opacity-50"
              >
                <Loader2 v-if="isDeleting" :size="14" class="animate-spin" />
                <Trash2 v-else :size="14" />
                删除条目
              </button>
              <a
                v-if="selectedNcbiUrl"
                :href="selectedNcbiUrl"
                target="_blank"
                rel="noreferrer"
                class="apple-button-secondary flex items-center gap-2 !py-2 !px-4 text-xs"
              >
                <ExternalLink :size="14" />
                NCBI 详情
              </a>
            </div>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-3 gap-6 mt-10">
            <div class="p-5 rounded-apple bg-apple-background dark:bg-white/5 border border-apple-border">
              <div class="flex items-center gap-2 mb-3 text-apple-secondary-text">
                <Layers :size="14" />
                <span class="text-[10px] font-bold uppercase tracking-widest">序列长度</span>
              </div>
              <p class="text-2xl font-bold text-apple-text">{{ selectedEnzyme.sequenceLength }} <span class="text-sm font-medium opacity-50">{{ selectedSequenceUnit }}</span></p>
            </div>
            <div class="p-5 rounded-apple bg-apple-background dark:bg-white/5 border border-apple-border">
              <div class="flex items-center gap-2 mb-3 text-apple-secondary-text">
                <MapPin :size="14" />
                <span class="text-[10px] font-bold uppercase tracking-widest">物种 Tax ID</span>
              </div>
              <p class="text-2xl font-bold text-apple-text">{{ selectedEnzyme.taxId || '-' }}</p>
            </div>
            <div class="p-5 rounded-apple bg-apple-background dark:bg-white/5 border border-apple-border overflow-hidden">
              <div class="flex items-center gap-2 mb-3 text-apple-secondary-text">
                <Info :size="14" />
                <span class="text-[10px] font-bold uppercase tracking-widest">序列哈希</span>
              </div>
              <p class="text-xs font-mono text-apple-secondary-text truncate">{{ selectedEnzyme.sequenceHash }}</p>
            </div>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-3 gap-6 mt-6">
            <div class="p-5 rounded-apple bg-apple-background dark:bg-white/5 border border-apple-border">
              <div class="flex items-center gap-2 mb-3 text-apple-secondary-text">
                <Database :size="14" />
                  <span class="text-[10px] font-bold uppercase tracking-widest">{{ selectedNcbiSourceLabel }}</span>
              </div>
              <p class="text-sm font-semibold text-apple-text truncate">{{ isPredictedLibrary ? 'Local Confirmed' : (selectedEnzyme.ncbiAccession || selectedEnzyme.accession) }}</p>
            </div>
            <div class="p-5 rounded-apple bg-apple-background dark:bg-white/5 border border-apple-border">
              <div class="flex items-center gap-2 mb-3 text-apple-secondary-text">
                <Tag :size="14" />
                <span class="text-[10px] font-bold uppercase tracking-widest">{{ selectedSecondarySourceLabel }}</span>
              </div>
              <p class="text-sm font-semibold text-apple-text truncate">{{ selectedSecondarySourceValue }}</p>
            </div>
            <div class="p-5 rounded-apple bg-apple-background dark:bg-white/5 border border-apple-border">
              <div class="flex items-center gap-2 mb-3 text-apple-secondary-text">
                <Dna :size="14" />
                <span class="text-[10px] font-bold uppercase tracking-widest">{{ isPredictedLibrary ? 'Structure' : (isRnaEntry ? 'RNA 结构' : 'PDB') }}</span>
              </div>
              <p class="text-sm font-semibold text-apple-text truncate">
                {{ isRnaEntry ? (hasCuratedStructure ? (selectedEnzyme.pdbId || selectedEnzyme.structureId || '已接入') : '暂未接入') : (selectedEnzyme.pdbId || selectedEnzyme.structureId || '-') }}
              </p>
            </div>
          </div>

          <div class="flex flex-wrap gap-3 mt-6">
            <a
              v-if="selectedNcbiUrl"
              :href="selectedNcbiUrl"
              target="_blank"
              rel="noreferrer"
              class="text-xs font-semibold text-apple-blue hover:underline"
            >
              查看 NCBI 页面
            </a>
            <a
              v-if="selectedUniprotUrl && canImportUniProtAnnotations"
              :href="selectedUniprotUrl"
              target="_blank"
              rel="noreferrer"
              class="text-xs font-semibold text-apple-blue hover:underline"
            >
              查看 UniProt 页面
            </a>
            <a
              v-if="selectedEnzyme.pdbUrl"
              :href="selectedEnzyme.pdbUrl"
              target="_blank"
              rel="noreferrer"
              class="text-xs font-semibold text-apple-blue hover:underline"
            >
              查看 PDB 页面
            </a>
          </div>

          <div v-if="selectedEnzyme.description" class="mt-6 rounded-apple border border-apple-border bg-apple-background/35 p-4">
            <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">说明</p>
            <p class="mt-2 text-sm leading-6 text-apple-text whitespace-pre-wrap">{{ selectedEnzyme.description }}</p>
          </div>
        </div>

        <!-- Grid for Tabs/Sections -->
        <div class="grid grid-cols-1 xl:grid-cols-2 gap-8">
          <div class="space-y-8">
            <!-- Structure Section -->
            <div class="apple-card p-6 flex flex-col">
              <div class="flex items-center justify-between mb-6">
                <div class="flex items-center gap-3">
                  <div class="w-8 h-8 rounded-apple bg-purple-500/10 text-purple-500 flex items-center justify-center">
                    <Dna :size="16" />
                  </div>
                  <div>
                    <h3 class="text-sm font-bold text-apple-text">{{ selectedStructureCardTitle }}</h3>
                    <p class="text-xs text-apple-secondary-text mt-1">{{ selectedStructureSectionDescription }}</p>
                  </div>
                </div>
                <div class="flex gap-2">
                  <button
                    v-if="canRenderStructureViewer"
                    @click="showFullscreenViewer = true"
                    class="p-2 rounded-full hover:bg-black/5 dark:hover:bg-white/5 text-apple-secondary-text transition-colors"
                    title="全屏查看"
                  >
                    <Maximize2 :size="14" />
                  </button>
                </div>
              </div>

              <div class="flex-1 min-h-[400px] relative group/viewer">
                <template v-if="canRenderStructureViewer">
                  <StructureViewer
                    :pdb-id="selectedViewerStructureId"
                    :url="selectedStructureUrl"
                    :source-db="selectedStructureSource"
                    :format="selectedStructureFormat"
                    :selected-annotation="selectedAnnotation"
                    :pick-mode="structurePickMode"
                    @residue-picked="handleResiduePicked"
                  />

                  <div class="absolute top-4 left-4 flex flex-col gap-2">
                    <div class="px-3 py-1.5 rounded-apple bg-white/90 dark:bg-black/50 backdrop-blur shadow-sm border border-apple-border text-[10px] font-bold text-apple-text">
                      {{ selectedStructureStatus }}
                    </div>
                    <div
                      v-if="selectedAnnotation"
                      class="px-3 py-1.5 rounded-apple bg-white/90 dark:bg-black/50 backdrop-blur shadow-sm border border-apple-border text-[10px] font-bold text-apple-text"
                    >
                      聚焦注释: {{ selectedAnnotation.title }}
                    </div>
                  </div>

                  <div class="absolute bottom-4 left-4 right-4 flex gap-2 overflow-x-auto pb-2 no-scrollbar opacity-0 group-hover/viewer:opacity-100 transition-opacity">
                    <div class="px-3 py-1.5 rounded-full bg-white/80 dark:bg-black/80 backdrop-blur shadow-sm border border-apple-border text-[10px] font-bold text-apple-text whitespace-nowrap">
                      ID: {{ selectedStructureId }}
                    </div>
                    <div class="px-3 py-1.5 rounded-full bg-apple-blue text-white shadow-sm text-[10px] font-bold whitespace-nowrap">
                      {{ selectedStructureType }}
                    </div>
                  </div>
                </template>
                <div
                  v-else
                  class="h-full min-h-[400px] rounded-apple border border-dashed border-apple-border bg-apple-background/40 dark:bg-white/[0.03] p-6 flex flex-col justify-between"
                >
                  <div class="space-y-3">
                    <div class="inline-flex px-3 py-1.5 rounded-full bg-white dark:bg-black/20 border border-apple-border text-[10px] font-bold text-apple-secondary-text">
                      {{ selectedStructureStatus }}
                    </div>
                    <div>
                      <p class="text-sm font-semibold text-apple-text">当前没有可直接展示的 RNA 三维结构</p>
                      <p class="mt-2 text-xs leading-6 text-apple-secondary-text">{{ rnaStructureSupportHint }}</p>
                    </div>
                  </div>
                  <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div class="p-4 rounded-apple border border-apple-border bg-white/70 dark:bg-black/20">
                      <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">当前状态</p>
                      <p class="mt-2 text-sm font-semibold text-apple-text">{{ selectedStructureStatus }}</p>
                    </div>
                    <div class="p-4 rounded-apple border border-apple-border bg-white/70 dark:bg-black/20">
                      <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">后续建议</p>
                      <p class="mt-2 text-sm font-semibold text-apple-text">
                        {{ selectedEnzyme.sequenceLength > 400 ? '建议后续补接外部 RNA 结构来源' : '可直接送往 RNA 预测或人工上传结构' }}
                      </p>
                    </div>
                  </div>
                  <div class="mt-4 flex items-center gap-3">
                    <button
                      type="button"
                      class="apple-button-secondary !py-2.5 !px-4 text-xs flex items-center gap-2"
                      :disabled="!canLaunchRnaPrediction"
                      @click="handleOpenRnaPrediction"
                    >
                      <Sparkles :size="14" />
                      {{ selectedRnaPredictionButtonLabel }}
                    </button>
                    <p class="text-[11px] text-apple-secondary-text">
                      {{ canLaunchRnaPrediction ? '会自动带入条目名称和 RNA 主序列。' : '当前长度超过 trRosettaRNA 支持范围，先保留为注释条目更稳。' }}
                    </p>
                  </div>
                </div>
              </div>

              <div class="mt-4 grid grid-cols-2 gap-3">
                <div class="p-3 rounded-apple bg-apple-background dark:bg-white/5 border border-apple-border">
                  <p class="text-[9px] font-bold text-apple-secondary-text uppercase tracking-widest mb-1">Source</p>
                  <p class="text-xs font-bold text-apple-text">{{ selectedStructureSource }}</p>
                </div>
                <div class="p-3 rounded-apple bg-apple-background dark:bg-white/5 border border-apple-border">
                  <p class="text-[9px] font-bold text-apple-secondary-text uppercase tracking-widest mb-1">Structure ID</p>
                  <p class="text-xs font-bold text-apple-text truncate">{{ selectedStructureId }}</p>
                </div>
              </div>
            </div>

            <div class="apple-card p-6 space-y-5">
              <div class="flex items-center justify-between gap-4">
                <div class="flex items-center gap-3">
                  <div class="w-8 h-8 rounded-apple bg-amber-500/10 text-amber-500 flex items-center justify-center">
                    <Layers :size="16" />
                  </div>
                  <div>
                    <h3 class="text-sm font-bold text-apple-text">{{ selectedAnnotationToolTitle }}</h3>
                    <p class="text-xs text-apple-secondary-text">{{ selectedAnnotationToolDescription }}</p>
                  </div>
                </div>
                <div class="flex items-center gap-2">
                  <button
                    v-if="canImportAutomaticAnnotations"
                    type="button"
                    class="apple-button-secondary !py-2 !px-3 text-[10px] flex items-center gap-1"
                    :disabled="importingAnnotations"
                    @click="handleImportAnnotations"
                  >
                    <Loader2 v-if="importingAnnotations" :size="12" class="animate-spin" />
                    <Wand2 v-else :size="12" />
                    {{ selectedAnnotationImportButtonLabel }}
                  </button>
                  <button
                    v-if="canPickAnnotationFromStructure"
                    type="button"
                    class="apple-button-secondary !py-2 !px-3 text-[10px] flex items-center gap-1"
                    @click="handlePickAnnotationFromStructure()"
                  >
                    <MousePointerClick :size="12" />
                    从 3D 选点
                  </button>
                  <button
                    v-for="option in annotationTypeOptions"
                    :key="option.value"
                    type="button"
                    class="apple-button-secondary !py-2 !px-3 text-[10px] flex items-center gap-1"
                    @click="openAnnotationModal(option.value)"
                  >
                    <Plus :size="12" />
                    {{ option.label }}
                  </button>
                </div>
              </div>

              <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div
                  v-for="item in annotationLegend"
                  :key="item.value"
                  class="p-3 rounded-apple border border-apple-border bg-apple-background dark:bg-white/5"
                >
                  <div class="flex items-center gap-2 mb-2">
                    <span class="w-3 h-3 rounded-full" :style="{ backgroundColor: item.color }"></span>
                    <p class="text-xs font-bold text-apple-text">{{ item.label }}</p>
                  </div>
                  <p class="text-[10px] text-apple-secondary-text">{{ item.hint }}</p>
                  <p class="mt-2 text-lg font-bold text-apple-text">{{ item.count }}</p>
                </div>
              </div>

              <div class="flex flex-wrap gap-3 text-[10px]">
                <div class="px-3 py-2 rounded-apple border border-apple-border bg-apple-background dark:bg-white/5 text-apple-secondary-text">
                  手动注释 <span class="ml-1 font-bold text-apple-text">{{ manualAnnotationCount }}</span>
                </div>
                <div class="px-3 py-2 rounded-apple border border-apple-border bg-apple-background dark:bg-white/5 text-apple-secondary-text">
                  {{ selectedImportedAnnotationLabel }} <span class="ml-1 font-bold text-apple-text">{{ annotationImportedCount }}</span>
                </div>
                <div
                  v-if="structurePickMode"
                  class="px-3 py-2 rounded-apple border border-amber-500/30 bg-amber-500/10 text-amber-600 dark:text-amber-300 font-semibold"
                >
                  3D 选点模式开启中
                </div>
              </div>

              <p v-if="annotationNotice" class="text-xs text-apple-blue">{{ annotationNotice }}</p>
              <p v-else-if="annotationError && !showAnnotationModal" class="text-xs text-red-500">{{ annotationError }}</p>
              <p v-if="isRnaEntry" class="text-xs text-apple-secondary-text">
                RNA 条目当前可从 NCBI Nucleotide 导入基础注释，并继续基于整条序列坐标补充手动区间标注。
              </p>

              <div class="rounded-apple border border-apple-border bg-apple-background/35 p-4">
                <div class="flex items-center justify-between gap-4 mb-3">
                  <div>
                    <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">序列注释视图</p>
                    <p class="text-xs text-apple-secondary-text mt-1">
                      {{ isRnaEntry ? '当前按 RNA 全长序列坐标展示注释区间，便于先做功能区整理。' : '点击下方彩色区段可同步选中对应注释，并在 3D 结构中聚焦查看。' }}
                    </p>
                  </div>
                  <p class="text-xs font-semibold text-apple-text">Length: {{ selectedEnzyme.sequenceLength }} {{ selectedSequenceUnit }}</p>
                </div>
                <div class="relative h-12 rounded-full bg-white dark:bg-black/20 border border-apple-border overflow-hidden">
                  <div
                    v-for="segment in annotationSequenceSegments"
                    :key="segment.id"
                    class="absolute top-1/2 -translate-y-1/2 h-8 rounded-full border transition-all cursor-pointer"
                    :class="selectedAnnotation?.id === segment.id ? 'ring-2 ring-offset-1 ring-apple-blue border-white/80' : 'border-white/40 hover:opacity-90'"
                    :style="{ left: segment.left, width: segment.width, backgroundColor: segment.colorHex }"
                    :title="`${segment.title} (${segment.startResidue}-${segment.endResidue})`"
                    @click="selectedAnnotationId = segment.id"
                  ></div>
                </div>
                <div class="mt-2 flex justify-between text-[10px] text-apple-secondary-text">
                  <span>1</span>
                  <span>{{ Math.max(1, Math.floor(selectedEnzyme.sequenceLength / 2)) }}</span>
                  <span>{{ selectedEnzyme.sequenceLength }}</span>
                </div>
              </div>

              <div v-if="annotationsLoading" class="py-10 flex flex-col items-center justify-center">
                <Loader2 :size="24" class="animate-spin text-apple-blue mb-2" />
                <p class="text-[10px] text-apple-secondary-text">读取注释中...</p>
              </div>

              <template v-else-if="hasAnnotations">
                <div class="space-y-3">
                  <button
                    v-for="annotation in annotations"
                    :key="annotation.id"
                    @click="selectedAnnotationId = annotation.id"
                    class="w-full text-left p-4 rounded-apple border bg-apple-background dark:bg-white/5 transition-all"
                    :class="selectedAnnotation?.id === annotation.id ? 'border-apple-blue bg-apple-blue/5' : 'border-apple-border hover:border-apple-blue/30'"
                  >
                    <div class="flex items-start justify-between gap-4">
                      <div class="space-y-2 flex-1">
                        <div class="flex items-center gap-2 flex-wrap">
                          <span class="inline-flex items-center gap-2 px-2 py-0.5 rounded-full text-[9px] font-bold uppercase tracking-wider bg-white dark:bg-black/20 border border-apple-border text-apple-text">
                            <span class="w-2.5 h-2.5 rounded-full" :style="{ backgroundColor: annotation.colorHex }"></span>
                            {{ annotation.annotationType === 'DOMAIN' ? '结构域' : annotation.annotationType === 'ACTIVE_SITE' ? '活性位点' : '突变位点' }}
                          </span>
                          <span class="text-[10px] text-apple-secondary-text uppercase tracking-widest">
                            {{ annotation.startResidue }}{{ annotation.endResidue !== annotation.startResidue ? `-${annotation.endResidue}` : '' }}
                          </span>
                          <span v-if="annotation.chainLabel" class="text-[10px] text-apple-secondary-text uppercase tracking-widest">
                            Chain {{ annotation.chainLabel }}
                          </span>
                          <span
                            v-if="annotation.sourceDb"
                            class="text-[9px] px-2 py-0.5 rounded-full bg-white dark:bg-black/20 border border-apple-border text-apple-secondary-text"
                          >
                            {{ annotation.sourceDb }}
                          </span>
                        </div>
                        <h4 class="text-sm font-bold text-apple-text">{{ annotation.title }}</h4>
                        <p class="text-xs text-apple-secondary-text">
                          {{ annotation.description || (annotation.annotationType === 'MUTATION' ? (annotation.mutationLabel || '未填写突变说明') : '未填写说明') }}
                        </p>
                      </div>
                      <div class="flex items-center gap-2 shrink-0">
                        <button
                          type="button"
                          class="p-2 rounded-full hover:bg-black/5 dark:hover:bg-white/5 text-apple-secondary-text"
                          @click.stop="editAnnotation(annotation)"
                          title="编辑注释"
                        >
                          <Pencil :size="14" />
                        </button>
                        <button
                          type="button"
                          class="p-2 rounded-full hover:bg-red-500/10 text-red-500 disabled:opacity-50"
                          :disabled="deletingAnnotationId === annotation.id"
                          @click.stop="handleDeleteAnnotation(annotation)"
                          title="删除注释"
                        >
                          <Loader2 v-if="deletingAnnotationId === annotation.id" :size="14" class="animate-spin" />
                          <Trash2 v-else :size="14" />
                        </button>
                      </div>
                    </div>
                  </button>
                </div>

                <div v-if="selectedAnnotation" class="rounded-apple border border-apple-blue/20 bg-apple-blue/5 p-4 space-y-3">
                  <div class="flex items-center justify-between gap-4">
                    <div>
                      <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">当前选中注释</p>
                      <h4 class="mt-1 text-sm font-bold text-apple-text">{{ selectedAnnotation.title }}</h4>
                    </div>
                    <span class="inline-flex items-center gap-2 px-2 py-0.5 rounded-full bg-white dark:bg-black/20 border border-apple-border text-[10px] font-bold text-apple-text">
                      <span class="w-2.5 h-2.5 rounded-full" :style="{ backgroundColor: selectedAnnotation.colorHex }"></span>
                      {{ selectedAnnotationSummary }}
                    </span>
                  </div>
                  <div class="flex flex-wrap gap-2 text-[10px]">
                    <span
                      v-if="selectedAnnotation.sourceDb"
                      class="px-2 py-0.5 rounded-full bg-white dark:bg-black/20 border border-apple-border text-apple-secondary-text"
                    >
                      来源: {{ selectedAnnotation.sourceDb }}
                    </span>
                    <span
                      v-if="selectedAnnotation.sourceRef"
                      class="px-2 py-0.5 rounded-full bg-white dark:bg-black/20 border border-apple-border text-apple-secondary-text"
                    >
                      标识: {{ selectedAnnotation.sourceRef }}
                    </span>
                  </div>
                  <p class="text-xs leading-6 text-apple-text">
                    {{ selectedAnnotation.description || '该注释暂无补充说明。你可以点击右上角编辑按钮补充结构功能解释、实验依据或突变备注。' }}
                  </p>
                </div>
              </template>

              <div v-else class="p-8 text-center border-2 border-dashed border-apple-border rounded-apple">
                <AlertCircle :size="24" class="mx-auto text-apple-secondary-text opacity-40 mb-3" />
                <p class="text-xs text-apple-secondary-text italic">还没有任何结构注释。先添加一个结构域、活性位点或突变位点试试看。</p>
              </div>
            </div>
          </div>

          <!-- Literature Section -->
          <div v-if="!isPredictedLibrary" class="apple-card p-6">
            <div class="flex items-center justify-between mb-6">
              <div class="flex items-center gap-3">
                <div class="w-8 h-8 rounded-apple bg-apple-green/10 text-apple-green flex items-center justify-center">
                  <BookOpen :size="16" />
                </div>
                <h3 class="text-sm font-bold text-apple-text">关联文献</h3>
              </div>
              <div class="flex items-center gap-3">
                <button
                  @click="openImportLiteratureModal"
                  :disabled="!selectedId || !!importingEnzymeId"
                  class="text-[10px] font-bold text-apple-blue hover:underline disabled:opacity-50 flex items-center gap-1"
                >
                  <Loader2 v-if="!!importingEnzymeId" :size="10" class="animate-spin" />
                  <Upload v-else :size="10" />
                  导入文献
                </button>
                <button
                  @click="handleOpenMatcher"
                  :disabled="loadingLit"
                  class="text-[10px] font-bold text-apple-blue hover:underline disabled:opacity-50 flex items-center gap-1"
                >
                  <Loader2 v-if="loadingLit" :size="10" class="animate-spin" />
                  <Sparkles v-else :size="10" />
                  去文献匹配页
                </button>
              </div>
            </div>
            
            <div class="space-y-4">
              <div v-if="loadingLit" class="py-12 flex flex-col items-center justify-center">
                <Loader2 :size="24" class="animate-spin text-apple-blue mb-2" />
                <p class="text-[10px] text-apple-secondary-text">搜寻证据中...</p>
              </div>

              <template v-else-if="enzymeLiteratures.length">
                <div class="space-y-3">
                  <button
                    v-for="lit in enzymeLiteratures"
                    :key="lit.id"
                    @click="selectedLiteratureId = lit.id"
                    class="w-full text-left p-4 rounded-apple border bg-apple-background dark:bg-white/5 group transition-all"
                    :class="selectedLiterature?.id === lit.id ? 'border-apple-blue bg-apple-blue/5' : 'border-apple-border hover:border-apple-green/30'"
                  >
                    <div class="flex justify-between items-start mb-2">
                      <span
                        class="px-2 py-0.5 rounded-full text-[9px] font-bold uppercase tracking-wider"
                        :class="lit.confidenceLevel === 'STRONG' ? 'bg-apple-green/10 text-apple-green' : lit.confidenceLevel === 'MANUAL' ? 'bg-purple-500/10 text-purple-500' : 'bg-apple-blue/10 text-apple-blue'"
                      >
                        {{ lit.confidenceLevel === 'MANUAL' ? 'LOCAL' : (lit.confidenceLevel || 'MATCH') }}
                      </span>
                      <span class="text-[9px] text-apple-secondary-text font-bold uppercase">PMID: {{ lit.pmid }}</span>
                    </div>
                    <h4 class="text-xs font-bold text-apple-text line-clamp-2 leading-snug group-hover:text-apple-blue transition-colors">
                      {{ lit.title }}
                    </h4>
                    <p class="mt-2 text-[10px] text-apple-secondary-text italic">{{ lit.journal }}, {{ lit.publishYear }}</p>
                  </button>
                </div>

                <div v-if="selectedLiterature" class="mt-2 p-5 rounded-apple border border-apple-blue/20 bg-apple-blue/5 space-y-4">
                  <div class="flex items-start justify-between gap-4">
                    <div class="space-y-2">
                      <div class="flex flex-wrap items-center gap-2">
                        <span
                          class="px-2 py-0.5 rounded-full text-[9px] font-bold uppercase tracking-wider"
                          :class="selectedLiterature.confidenceLevel === 'STRONG' ? 'bg-apple-green/10 text-apple-green' : selectedLiterature.confidenceLevel === 'MANUAL' ? 'bg-purple-500/10 text-purple-500' : 'bg-apple-blue/10 text-apple-blue'"
                        >
                          {{ selectedLiterature.confidenceLevel === 'MANUAL' ? 'LOCAL' : (selectedLiterature.confidenceLevel || 'MATCH') }}
                        </span>
                        <span class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">
                          已保存到本地数据库
                        </span>
                        <span
                          class="text-[10px] font-bold uppercase tracking-widest"
                          :class="selectedLiterature.attachmentStatus === 'DOWNLOADED' ? 'text-apple-green' : 'text-apple-secondary-text'"
                        >
                          {{ selectedLiterature.attachmentStatus === 'DOWNLOADED' ? '全文附件已入库' : selectedLiterature.attachmentStatus === 'NOT_OPEN_ACCESS' ? '暂无开放全文' : selectedLiterature.attachmentStatus === 'FAILED' ? '附件抓取失败' : '尚未抓取全文附件' }}
                        </span>
                      </div>
                      <h4 class="text-sm font-bold text-apple-text leading-snug">
                        {{ selectedLiterature.title }}
                      </h4>
                    </div>
                    <div class="flex gap-2 shrink-0">
                      <button
                        v-if="selectedLiterature.attachmentStatus === 'DOWNLOADED'"
                        @click="handleDownloadAttachment(selectedLiterature.id)"
                        class="apple-button-secondary !py-2 !px-3 text-xs flex items-center gap-2"
                        :disabled="downloadingAttachmentId === selectedLiterature.id"
                      >
                        <Loader2 v-if="downloadingAttachmentId === selectedLiterature.id" :size="12" class="animate-spin" />
                        <Sparkles v-else :size="12" />
                        下载本地附件
                      </button>
                      <a
                        v-if="selectedLiterature.sourceDb !== 'LOCAL_UPLOAD'"
                        :href="selectedLiterature.sourceUrl || `https://pubmed.ncbi.nlm.nih.gov/${selectedLiterature.pmid}/`"
                        target="_blank"
                        rel="noreferrer"
                        class="apple-button-secondary !py-2 !px-3 text-xs flex items-center gap-2"
                      >
                        <ExternalLink :size="12" />
                        官网链接
                      </a>
                    </div>
                  </div>

                  <div class="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs">
                    <div class="p-3 rounded-apple bg-white/80 dark:bg-black/20 border border-apple-border">
                      <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text mb-1">作者</p>
                      <p class="text-apple-text">{{ selectedLiterature.authors || '暂无作者信息' }}</p>
                    </div>
                    <div class="p-3 rounded-apple bg-white/80 dark:bg-black/20 border border-apple-border">
                      <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text mb-1">期刊</p>
                      <p class="text-apple-text">{{ selectedLiterature.journal || '未知期刊' }}，{{ selectedLiterature.publishYear || '未知年份' }}</p>
                    </div>
                    <div class="p-3 rounded-apple bg-white/80 dark:bg-black/20 border border-apple-border">
                      <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text mb-1">PMID</p>
                      <p class="text-apple-text">{{ selectedLiterature.pmid }}</p>
                    </div>
                    <div class="p-3 rounded-apple bg-white/80 dark:bg-black/20 border border-apple-border">
                      <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text mb-1">DOI</p>
                      <p class="text-apple-text break-all">{{ selectedLiterature.doi || '暂无 DOI' }}</p>
                    </div>
                  </div>

                  <div class="p-4 rounded-apple bg-white/80 dark:bg-black/20 border border-apple-border">
                    <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text mb-2">本地入库内容</p>
                    <p class="text-xs leading-6 text-apple-text">
                      {{
                        selectedLiterature.attachmentStatus === 'DOWNLOADED'
                          ? '该文献的开放全文附件已经抓取到本地，可通过上方“下载本地附件”按钮获取。当前下方展示的是数据库中的摘要/说明信息。'
                          : selectedLiterature.abstractText && selectedLiterature.abstractText !== 'PubMed metadata matching...'
                          ? selectedLiterature.abstractText
                          : '当前已下载到本地数据库的是 PubMed 文献元数据（标题、作者、期刊、年份、PMID、DOI 和匹配关系）。如果这篇文献没有开放 PMC 全文，系统会保留元数据并标记“暂无开放全文”。'
                      }}
                    </p>
                  </div>
                </div>
              </template>

              <div v-else class="p-8 text-center border-2 border-dashed border-apple-border rounded-apple">
                <p class="text-xs text-apple-secondary-text italic">尚无已下载文献。请前往“文献匹配”扫描并下载后，再回到这里查看。</p>
              </div>
            </div>
          </div>

          <div v-else class="apple-card p-6">
            <div class="flex items-center gap-3 mb-6">
              <div class="w-8 h-8 rounded-apple bg-apple-green/10 text-apple-green flex items-center justify-center">
                <Sparkles :size="16" />
              </div>
              <h3 class="text-sm font-bold text-apple-text">预测入库说明</h3>
            </div>

            <div class="space-y-4">
              <div class="rounded-apple border border-apple-border bg-apple-background/35 p-4">
                <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">当前来源</p>
                <p class="mt-2 text-sm font-semibold text-apple-text">本地/云端预测结果</p>
                <p class="mt-2 text-xs leading-6 text-apple-secondary-text">
                  这个页面只保留已经由你确认命名并正式入库的预测结构。它们和 accession 导入条目分仓管理，避免后续检索、展示和结构判断时互相干扰。
                </p>
              </div>

              <div class="rounded-apple border border-apple-border bg-apple-background/35 p-4">
                <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">建议下一步</p>
                <div class="mt-3 flex flex-wrap gap-2">
                  <button
                    type="button"
                    class="apple-button-secondary !py-2 !px-4 text-xs"
                    @click="router.push('/prediction/minifold')"
                  >
                    去 MiniFold
                  </button>
                  <button
                    type="button"
                    class="apple-button-secondary !py-2 !px-4 text-xs"
                    @click="router.push('/prediction/esmfold')"
                  >
                    去 ESMFold
                  </button>
                  <button
                    type="button"
                    class="apple-button-secondary !py-2 !px-4 text-xs"
                    @click="router.push('/prediction/trrosettarna')"
                  >
                    去 trRosettaRNA
                  </button>
                  <button
                    type="button"
                    class="apple-button-secondary !py-2 !px-4 text-xs"
                    @click="router.push('/library/imported')"
                  >
                    查看导入酶库
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-else class="flex flex-col items-center justify-center h-[calc(100vh-280px)] apple-card">
        <div class="w-20 h-20 bg-apple-light-gray dark:bg-white/5 rounded-full flex items-center justify-center mb-6 text-apple-secondary-text opacity-20">
          <FlaskConical :size="40" />
        </div>
        <h3 class="text-lg font-bold text-apple-text mb-2">{{ emptyTitle }}</h3>
        <p class="text-sm text-apple-secondary-text max-w-xs text-center">{{ emptyDescription }}</p>
      </div>
    </div>

    <!-- Fullscreen 3D Viewer Modal -->
    <transition name="fade">
      <div v-if="showFullscreenViewer" class="fixed inset-0 z-[100] bg-black/80 backdrop-blur-md flex flex-col">
        <div class="h-16 px-8 flex items-center justify-between border-b border-white/10">
          <div class="flex items-center gap-4">
            <h3 class="text-white font-bold">{{ selectedEnzyme?.proteinName }}</h3>
            <span class="px-2 py-0.5 rounded-full bg-apple-blue text-white text-[10px] font-bold uppercase tracking-widest">
              {{ selectedStructureId }}
            </span>
          </div>
          <button 
            @click="showFullscreenViewer = false"
            class="w-10 h-10 rounded-full bg-white/10 text-white flex items-center justify-center hover:bg-white/20 transition-all"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="lucide lucide-x"><path d="M18 6 6 18"/><path d="m6 6 12 12"/></svg>
          </button>
        </div>
        <div class="flex-1 p-8">
          <StructureViewer 
            v-if="selectedEnzyme"
            :pdb-id="selectedViewerStructureId"
            :url="selectedStructureUrl"
            :source-db="selectedStructureSource"
            :format="selectedStructureFormat"
            :selected-annotation="selectedAnnotation"
            :pick-mode="structurePickMode"
            @residue-picked="handleResiduePicked"
          />
        </div>
      </div>
    </transition>

    <transition name="fade">
      <div
        v-if="showImportLiteratureModal"
        class="fixed inset-0 z-[110] bg-black/50 backdrop-blur-sm flex items-center justify-center p-4"
        @click.self="closeImportLiteratureModal"
      >
        <div class="w-full max-w-xl apple-card p-6 space-y-5">
          <div class="flex items-start justify-between gap-4">
            <div class="space-y-1">
              <h3 class="text-lg font-bold text-apple-text">导入本地文献</h3>
              <p class="text-sm text-apple-secondary-text">
                为当前酶条目添加本地文献附件。系统会把文件复制到平台存储目录，并在关联文献里显示。
              </p>
            </div>
            <button
              @click="closeImportLiteratureModal"
              :disabled="!!importingEnzymeId"
              class="w-9 h-9 rounded-full hover:bg-black/5 dark:hover:bg-white/5 text-apple-secondary-text flex items-center justify-center disabled:opacity-50"
            >
              <X :size="16" />
            </button>
          </div>

          <div class="space-y-2">
            <label class="text-xs font-bold uppercase tracking-widest text-apple-secondary-text">选择文件</label>
            <input
              type="file"
              class="apple-input w-full"
              @change="handleImportFileChange"
              :disabled="!!importingEnzymeId"
            />
            <p class="text-xs text-apple-secondary-text">
              支持直接从本机选择文件。导入后文件会复制到平台本地存储目录，不依赖原始文件继续存在。
            </p>
            <p v-if="importLiteratureFile" class="text-xs text-apple-text">
              已选择：{{ importLiteratureFile.name }}
            </p>
            <p v-if="importLiteratureError" class="text-xs text-red-500">
              {{ importLiteratureError }}
            </p>
          </div>

          <div class="flex justify-end gap-3">
            <button
              @click="closeImportLiteratureModal"
              :disabled="!!importingEnzymeId"
              class="apple-button-secondary !py-2 !px-4 text-xs disabled:opacity-50"
            >
              取消
            </button>
            <button
              @click="handleImportLiterature"
              :disabled="!!importingEnzymeId"
              class="apple-button !py-2 !px-4 text-xs flex items-center gap-2 disabled:opacity-50"
            >
              <Loader2 v-if="!!importingEnzymeId" :size="14" class="animate-spin" />
              <Upload v-else :size="14" />
              导入到当前酶
            </button>
          </div>
        </div>
      </div>
    </transition>

    <transition name="fade">
      <div
        v-if="showAnnotationModal"
        class="fixed inset-0 z-[115] bg-black/50 backdrop-blur-sm flex items-center justify-center p-4"
        @click.self="closeAnnotationModal"
      >
        <div class="w-full max-w-2xl apple-card p-6 space-y-5">
          <div class="flex items-start justify-between gap-4">
            <div class="space-y-1">
              <h3 class="text-lg font-bold text-apple-text">{{ editingAnnotationId ? '编辑结构注释' : '新增结构注释' }}</h3>
              <p class="text-sm text-apple-secondary-text">
                为当前酶条目记录结构域、活性位点或突变位点。保存后可在 3D 结构中直接聚焦查看。
              </p>
              <p v-if="structurePickMode" class="text-xs text-amber-600 dark:text-amber-300">
                当前已开启 3D 选点模式。请点击左侧结构中的残基，系统会自动把位点和链号带回表单。
              </p>
            </div>
            <button
              @click="closeAnnotationModal"
              :disabled="annotationSaving"
              class="w-9 h-9 rounded-full hover:bg-black/5 dark:hover:bg-white/5 text-apple-secondary-text flex items-center justify-center disabled:opacity-50"
            >
              <X :size="16" />
            </button>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <label class="space-y-2">
              <span class="text-xs font-bold uppercase tracking-widest text-apple-secondary-text">注释类型</span>
              <div class="flex items-center gap-2">
                <select v-model="annotationForm.annotationType" class="apple-input w-full">
                  <option v-for="option in annotationTypeOptions" :key="option.value" :value="option.value">
                    {{ option.label }}
                  </option>
                </select>
                <button
                  type="button"
                  class="apple-button-secondary !py-2 !px-3 text-[10px] flex items-center gap-1 shrink-0"
                  @click="handlePickAnnotationFromStructure(annotationForm.annotationType)"
                >
                  <MousePointerClick :size="12" />
                  选点
                </button>
              </div>
            </label>

            <label class="space-y-2">
              <span class="text-xs font-bold uppercase tracking-widest text-apple-secondary-text">注释标题</span>
              <input v-model="annotationForm.title" type="text" class="apple-input w-full" placeholder="例如：催化核心区域 / Ser128 突变位点" />
            </label>

            <label class="space-y-2">
              <span class="text-xs font-bold uppercase tracking-widest text-apple-secondary-text">起始残基</span>
              <input v-model.number="annotationForm.startResidue" type="number" min="1" class="apple-input w-full" placeholder="例如 128" />
            </label>

            <label class="space-y-2">
              <span class="text-xs font-bold uppercase tracking-widest text-apple-secondary-text">
                {{ annotationForm.annotationType === 'MUTATION' ? '结束残基（自动等于起始位点）' : '结束残基' }}
              </span>
              <input
                v-model.number="annotationForm.endResidue"
                type="number"
                min="1"
                class="apple-input w-full"
                :disabled="annotationForm.annotationType === 'MUTATION'"
                :placeholder="annotationForm.annotationType === 'MUTATION' ? '突变位点固定为单残基' : '例如 196'"
              />
            </label>

            <label class="space-y-2">
              <span class="text-xs font-bold uppercase tracking-widest text-apple-secondary-text">链标识</span>
              <input v-model="annotationForm.chainLabel" type="text" class="apple-input w-full" placeholder="可选，例如 A" />
            </label>

            <label class="space-y-2">
              <span class="text-xs font-bold uppercase tracking-widest text-apple-secondary-text">颜色</span>
              <div class="flex items-center gap-3">
                <input v-model="annotationForm.colorHex" type="color" class="h-10 w-14 rounded border border-apple-border bg-transparent" />
                <input v-model="annotationForm.colorHex" type="text" class="apple-input flex-1" placeholder="#3B82F6" />
              </div>
            </label>

            <label v-if="annotationForm.annotationType === 'MUTATION'" class="md:col-span-2 space-y-2">
              <span class="text-xs font-bold uppercase tracking-widest text-apple-secondary-text">突变说明</span>
              <input v-model="annotationForm.mutationLabel" type="text" class="apple-input w-full" placeholder="例如：S128A / G45D" />
            </label>

            <label class="md:col-span-2 space-y-2">
              <span class="text-xs font-bold uppercase tracking-widest text-apple-secondary-text">备注说明</span>
              <textarea
                v-model="annotationForm.description"
                rows="4"
                class="apple-input w-full resize-none"
                placeholder="可填写功能解释、实验依据、保守性分析结论等"
              ></textarea>
            </label>
          </div>

          <p v-if="annotationNotice" class="text-xs text-apple-blue">{{ annotationNotice }}</p>
          <p v-if="annotationError" class="text-xs text-red-500">{{ annotationError }}</p>

          <div class="flex justify-end gap-3">
            <button
              @click="closeAnnotationModal"
              :disabled="annotationSaving"
              class="apple-button-secondary !py-2 !px-4 text-xs disabled:opacity-50"
            >
              取消
            </button>
            <button
              @click="handleSaveAnnotation"
              :disabled="annotationSaving"
              class="apple-button !py-2 !px-4 text-xs flex items-center gap-2 disabled:opacity-50"
            >
              <Loader2 v-if="annotationSaving" :size="14" class="animate-spin" />
              <Check v-else :size="14" />
              保存注释
            </button>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>
