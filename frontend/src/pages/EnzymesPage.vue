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
  MousePointerClick,
  ArrowRight,
  ChevronDown
} from 'lucide-vue-next'
import { createEmptyAnnotationForm, toAnnotationForm, useEnzymeAnnotations } from '@/composables/useEnzymeAnnotations'
import { useLiterature } from '@/composables/useLiterature'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import StructureViewer from '@/components/StructureViewer.vue'
import { deleteEnzyme, downloadLiteratureAttachment, getEnzymeStructure, listEnzymes } from '@/utils/api'
import type { EnzymeAnnotation, EnzymeAnnotationType, EnzymeEntry, LiteratureRecord } from '@/types'

const router = useRouter()
const route = useRoute()
const enzymes = ref<EnzymeEntry[]>([])
const {
  enzymeLiteratures,
  fetchEnzymeLiteratures,
  enzymeLoading: loadingLit,
  deletingRelationIds,
  importingEnzymeId,
  importLocalLiterature,
  removeEnzymeLiterature,
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
const showDeleteEnzymeDialog = ref(false)
const showDeleteAnnotationDialog = ref(false)
const showDeleteLiteratureDialog = ref(false)
const isDeleting = ref(false)
const selectedLiteratureId = ref<number | null>(null)
const selectedAnnotationId = ref<number | null>(null)
const isAnnotationToolCollapsed = ref(true)
const downloadingAttachmentId = ref<number | null>(null)
const importLiteratureFile = ref<File | null>(null)
const importLiteratureError = ref('')
const annotationForm = ref(createEmptyAnnotationForm())
const annotationError = ref('')
const editingAnnotationId = ref<number | null>(null)
const annotationNotice = ref('')
const predictedStructureUrl = ref<string | null>(null)
const structurePickMode = ref(false)
const pendingDeleteEnzymeId = ref<number | null>(null)
const pendingDeleteAnnotation = ref<EnzymeAnnotation | null>(null)
const pendingDeleteLiterature = ref<LiteratureRecord | null>(null)
const attemptedAutoImportAnnotationIds = new Set<number>()

const annotationTypeOptions: Array<{ value: EnzymeAnnotationType; label: string; hint: string; color: string }> = [
  { value: 'DOMAIN', label: '结构域', hint: '连续残基区间', color: '#3B82F6' },
  { value: 'ACTIVE_SITE', label: '活性位点', hint: '关键催化残基', color: '#10B981' },
  { value: 'MUTATION', label: '突变位点', hint: '关注的单个位点', color: '#F97316' },
] as const

const activeSourceType = computed(() => String(route.meta.librarySourceType || 'NCBI_IMPORT'))
const isPredictedLibrary = computed(() => activeSourceType.value === 'PREDICTED')
const libraryTitle = computed(() => String(route.meta.libraryTitle || '酶库中心'))
const librarySubtitle = computed(() => String(route.meta.librarySubtitle || '管理、浏览与分析本地酶条目数据库'))
const searchPlaceholder = computed(() => isPredictedLibrary.value ? '搜索内部编号或预测名称...' : '搜索 Accession 或条目名称...')
const identifierLabel = computed(() => isPredictedLibrary.value ? '内部编号' : 'Accession')
const selectedEntryBadge = computed(() => isPredictedLibrary.value ? '已入库的预测条目' : 'Accession 导入条目')
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

function requestDeleteEnzyme(id: number) {
  pendingDeleteEnzymeId.value = id
  showDeleteEnzymeDialog.value = true
}

function closeDeleteEnzymeDialog() {
  if (isDeleting.value) return
  pendingDeleteEnzymeId.value = null
  showDeleteEnzymeDialog.value = false
}

async function confirmDeleteEnzyme() {
  if (pendingDeleteEnzymeId.value == null) return
  isDeleting.value = true
  try {
    await deleteEnzyme(pendingDeleteEnzymeId.value)
    selectedId.value = null
    annotations.value = []
    selectedAnnotationId.value = null
    pendingDeleteEnzymeId.value = null
    showDeleteEnzymeDialog.value = false
    await refreshEnzymeLibrary()
  } catch (error) {
    console.error('删除失败', error)
  } finally {
    isDeleting.value = false
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

function isLocalUploadedLiterature(literature: LiteratureRecord | null | undefined) {
  return literature?.sourceDb === 'LOCAL_UPLOAD' && literature?.attachmentStatus === 'DOWNLOADED'
}

function normalizePreviewMimeType(fileName: string, contentType: string) {
  if (contentType && contentType !== 'application/octet-stream') {
    return contentType
  }

  const extension = fileName.split('.').pop()?.toLowerCase()
  switch (extension) {
    case 'pdf':
      return 'application/pdf'
    case 'txt':
      return 'text/plain'
    case 'html':
    case 'htm':
      return 'text/html'
    case 'xml':
      return 'application/xml'
    case 'png':
      return 'image/png'
    case 'jpg':
    case 'jpeg':
      return 'image/jpeg'
    default:
      return contentType || 'application/octet-stream'
  }
}

async function handlePreviewAttachment(literature: LiteratureRecord) {
  try {
    downloadingAttachmentId.value = literature.id
    const { blob, fileName, contentType } = await downloadLiteratureAttachment(literature.id)
    const previewBlob = new Blob([blob], {
      type: normalizePreviewMimeType(fileName, contentType),
    })
    const objectUrl = window.URL.createObjectURL(previewBlob)
    const previewWindow = window.open(objectUrl, '_blank', 'noopener,noreferrer')

    if (!previewWindow) {
      window.location.href = objectUrl
    }

    window.setTimeout(() => {
      window.URL.revokeObjectURL(objectUrl)
    }, 60000)
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

function requestDeleteAnnotation(annotation: EnzymeAnnotation) {
  pendingDeleteAnnotation.value = annotation
  showDeleteAnnotationDialog.value = true
}

function closeDeleteAnnotationDialog() {
  if (deletingAnnotationId.value) return
  pendingDeleteAnnotation.value = null
  showDeleteAnnotationDialog.value = false
}

async function confirmDeleteAnnotation() {
  if (!selectedId.value || !pendingDeleteAnnotation.value) return
  try {
    await removeAnnotation(selectedId.value, pendingDeleteAnnotation.value.id)
    if (selectedAnnotationId.value === pendingDeleteAnnotation.value.id) {
      selectedAnnotationId.value = annotations.value[0]?.id ?? null
    }
    closeDeleteAnnotationDialog()
  } catch (error) {
    console.error('删除注释失败', error)
  }
}

function requestDeleteLiterature(literature: LiteratureRecord) {
  pendingDeleteLiterature.value = literature
  showDeleteLiteratureDialog.value = true
}

function closeDeleteLiteratureDialog() {
  if (pendingDeleteLiterature.value?.relationId && deletingRelationIds.value.includes(pendingDeleteLiterature.value.relationId)) {
    return
  }
  pendingDeleteLiterature.value = null
  showDeleteLiteratureDialog.value = false
}

async function confirmDeleteLiterature() {
  if (!selectedId.value || !pendingDeleteLiterature.value?.relationId) return
  try {
    await removeEnzymeLiterature(selectedId.value, pendingDeleteLiterature.value.relationId)
    if (selectedLiteratureId.value === pendingDeleteLiterature.value.id) {
      selectedLiteratureId.value = enzymeLiteratures.value[0]?.id ?? null
    }
    closeDeleteLiteratureDialog()
  } catch (error) {
    console.error('删除关联文献失败', error)
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
  selectedId.value = null
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
  if (selectedId.value == null) return null
  return enzymes.value.find((item) => item.id === selectedId.value) ?? null
})

const isRnaEntry = computed(() => selectedEnzyme.value?.moleculeType === 'RNA')
const selectedSequenceUnit = computed(() => selectedEnzyme.value?.moleculeType === 'RNA' ? 'nt' : 'aa')
const canImportAutomaticAnnotations = computed(() => !isPredictedLibrary.value)
const importedAnnotationSourceLabels = computed(() => {
  const labels = new Set<string>()
  annotations.value.forEach((item) => {
    if (item.sourceDb === 'UNIPROT') labels.add('UniProt')
    else if (item.sourceDb === 'PDB') labels.add('PDB')
    else if (item.sourceDb === 'NCBI_NUCLEOTIDE') labels.add('NCBI Nucleotide')
  })
  return Array.from(labels)
})

const selectedSecondarySourceLabel = computed(() => {
  if (isPredictedLibrary.value) return 'Library Code'
  return selectedEnzyme.value?.moleculeType === 'RNA' ? 'RNA 注释源' : 'UniProt'
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

const selectedStructureStatus = computed(() => {
  const enzyme = selectedEnzyme.value
  if (!enzyme) return '等待加载'
  if (isPredictedLibrary.value) return '预测已确认入库'
  if (isRnaEntry.value && !hasCuratedStructure.value) return 'RNA 结构暂未接入'
  if (enzyme.structureSourceDb === 'PDB') return '实验结构（PDB）'
  if (enzyme.structureSourceDb === 'AlphaFold') return '预测结构（AlphaFold）'
  if (enzyme.structureSourceDb) return `已整理结构（${enzyme.structureSourceDb}）`
  return '系统自动补齐'
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
const annotationSummaryText = computed(() => annotationLegend.value
  .map((item) => `${item.label} ${item.count}`)
  .join(' · '))
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

watch(
  () => selectedId.value,
  (id) => {
    selectedAnnotationId.value = null
    if (id) {
      fetchAnnotations(id)
    } else {
      annotations.value = []
    }
    if (id && !isPredictedLibrary.value) {
      selectedLiteratureId.value = null
      fetchEnzymeLiteratures(id)
    } else {
      enzymeLiteratures.value = []
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
  () => selectedEnzyme.value?.id,
  () => {
    isAnnotationToolCollapsed.value = true
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
    if (!enzymeId || loading || predicted || annotationCount > 0 || attemptedAutoImportAnnotationIds.has(enzymeId)) {
      return
    }
    const enzyme = selectedEnzyme.value
    if (!enzyme) return
    if (enzyme.moleculeType !== 'RNA' && !enzyme.uniprotAccession && !enzyme.pdbId) {
      attemptedAutoImportAnnotationIds.add(enzymeId)
      return
    }
    attemptedAutoImportAnnotationIds.add(enzymeId)
    try {
      const imported = await importAutomatically(enzymeId)
      if (imported.length) {
        annotationNotice.value = enzyme.moleculeType === 'RNA'
          ? `已自动从 NCBI Nucleotide 补充 ${imported.length} 条初始 RNA 注释`
          : `已自动从 UniProt / PDB 补充 ${imported.length} 条初始注释`
      }
    } catch (error) {
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
  <div class="max-w-4xl mx-auto w-full pb-20 px-4">
    <transition name="card-switch" mode="out-in">
      <div
        :key="selectedEnzyme ? `detail-${activeSourceType}-${selectedEnzyme.id}` : `list-${activeSourceType}`"
        class="motion-stagger"
      >
        <!-- 视图 1: 独立的居中列表界面 (当 !selectedId 时展示) -->
        <template v-if="!selectedId">
          <div class="min-h-[80vh] flex flex-col items-center justify-center py-12">
        <!-- 列表头部：极简设计 -->
            <div class="text-center mb-16 space-y-4">
              <h1 class="text-5xl font-extrabold tracking-tight text-apple-text">{{ libraryTitle }}</h1>
              <p class="text-apple-secondary-text text-xl max-w-2xl mx-auto">{{ librarySubtitle }}</p>
            </div>

        <!-- 搜索与操作栏：居中布局 -->
            <div class="w-full max-w-2xl flex flex-col sm:flex-row items-center gap-4 mb-12">
              <div class="relative flex-1 group w-full">
                <Search class="absolute left-5 top-1/2 -translate-y-1/2 text-apple-secondary-text group-focus-within:text-apple-blue transition-colors" :size="20" />
                <input
                  v-model="searchQuery"
                  type="text"
                  :placeholder="searchPlaceholder"
                  class="apple-input pl-14 pr-6 py-5 text-lg w-full shadow-apple transition-all border-none"
                />
              </div>
              <button
                type="button"
                class="apple-button-secondary !py-5 !px-10 text-base flex items-center gap-2 whitespace-nowrap shadow-apple-sm hover:shadow-apple-md transition-all shrink-0"
                @click="router.push('/library')"
              >
                <Database :size="18" />
                切换仓库
              </button>
            </div>

            <!-- 条目列表卡片 -->
            <div class="w-full max-w-2xl overflow-hidden rounded-[28px] border border-apple-border/50 bg-[rgba(9,14,25,0.6)] shadow-[0_26px_72px_-52px_rgba(2,6,23,0.82)]">
              <div class="px-8 py-6 border-b border-apple-border/50 flex items-center justify-between bg-apple-background/[0.2]">
                <div class="flex items-center gap-3">
                  <FlaskConical :size="20" class="text-apple-blue" />
                  <span class="text-xs font-bold uppercase tracking-widest text-apple-secondary-text">可用条目</span>
                </div>
                <span class="text-[10px] font-bold text-apple-blue bg-apple-blue/[0.08] px-4 py-1.5 rounded-full uppercase tracking-wider border border-apple-blue/[0.14]">
                  共 {{ filteredEnzymes.length }} 项
                </span>
              </div>

              <div class="divide-y divide-apple-border/50">
                <button
                  v-for="enzyme in filteredEnzymes"
                  :key="enzyme.id"
                  @click="selectedId = enzyme.id"
                  class="w-full text-left px-8 py-10 transition-all hover:bg-white/[0.02] group flex items-center justify-between gap-8"
                >
                  <div class="flex-1 space-y-4">
                    <div class="flex items-center gap-4">
                      <span class="text-[10px] font-bold text-apple-blue bg-apple-blue/8 px-3 py-1 rounded border border-apple-blue/10 uppercase tracking-widest">
                        {{ isPredictedLibrary ? enzyme.code : enzyme.accession }}
                      </span>
                      <div class="h-1 w-1 rounded-full bg-apple-border"></div>
                      <span class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest opacity-60">
                        {{ enzyme.moleculeType === 'RNA' ? 'RNA' : '蛋白' }} • {{ enzyme.sequenceLength }} {{ enzyme.moleculeType === 'RNA' ? 'nt' : 'aa' }}
                      </span>
                    </div>
                    <h3 class="text-2xl font-bold text-apple-text group-hover:text-apple-blue transition-colors">
                      {{ enzyme.proteinName }}
                    </h3>
                    <p class="text-sm text-apple-secondary-text flex items-center gap-2 opacity-80">
                      <Tag :size="16" class="opacity-40" />
                      {{ enzyme.organismName }}
                    </p>
                  </div>

                  <div class="w-14 h-14 rounded-full bg-apple-background/[0.26] flex items-center justify-center text-apple-secondary-text group-hover:bg-apple-blue/[0.08] group-hover:text-apple-blue transition-all shadow-[inset_0_0_0_1px_rgba(255,255,255,0.04)]">
                    <ArrowRight :size="24" />
                  </div>
                </button>

                <div v-if="!filteredEnzymes.length" class="p-32 text-center space-y-8">
                  <div class="w-24 h-24 bg-apple-blue/8 rounded-full flex items-center justify-center mx-auto text-apple-secondary-text opacity-40">
                    <FlaskConical :size="48" />
                  </div>
                  <div class="space-y-3">
                    <p class="text-2xl font-bold text-apple-text">未找到相关条目</p>
                    <p class="text-sm text-apple-secondary-text max-w-xs mx-auto leading-relaxed">{{ searchQuery ? '请尝试使用其他关键词重新搜索' : emptyDescription }}</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>

        <!-- 视图 2: 居中的详情界面 (当 selectedId 存在时展示) -->
        <template v-else-if="selectedEnzyme">
      <!-- 详情页导航栏 -->
          <div class="flex items-center justify-between gap-6 py-12">
        <div class="flex items-center gap-8">
          <button 
            @click="selectedId = null"
            class="w-14 h-14 rounded-full bg-[linear-gradient(180deg,rgba(15,23,42,0.6),rgba(9,14,25,0.58))] flex items-center justify-center text-apple-text hover:bg-[linear-gradient(135deg,rgba(9,14,25,0.88),rgba(92,199,245,0.58))] hover:text-white transition-all shadow-[inset_0_0_0_1px_rgba(255,255,255,0.04),0_18px_42px_-30px_rgba(2,6,23,0.8)] group"
            title="返回条目列表"
          >
            <X :size="24" class="group-hover:rotate-90 transition-transform duration-300" />
          </button>
          <div class="space-y-2">
            <div class="flex items-center gap-3">
              <span class="px-3 py-1 rounded-full bg-apple-blue/10 text-apple-blue text-[10px] font-bold uppercase tracking-widest border border-apple-blue/20">
                {{ selectedEntryBadge }}
              </span>
              <div class="h-1 w-1 rounded-full bg-apple-border"></div>
              <span class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest opacity-60">{{ activeSourceType === 'PREDICTED' ? 'AI 预测资产' : '实验数据资产' }}</span>
            </div>
            <h1 class="text-4xl font-extrabold tracking-tight text-apple-text">{{ selectedEnzyme.proteinName }}</h1>
          </div>
        </div>
        
        <div class="flex items-center gap-4">
          <button 
            @click="requestDeleteEnzyme(selectedEnzyme.id)"
            :disabled="isDeleting"
            class="apple-button-secondary !text-red-500 !border-red-500/20 hover:!bg-red-500/5 flex items-center gap-2 !py-4 !px-8 text-sm disabled:opacity-50 shadow-apple-sm"
          >
            <Loader2 v-if="isDeleting" :size="16" class="animate-spin" />
            <Trash2 v-else :size="16" />
            移除条目
          </button>
          <a
            v-if="selectedNcbiUrl"
            :href="selectedNcbiUrl"
            target="_blank"
            rel="noreferrer"
            class="apple-button-secondary flex items-center gap-2 !py-4 !px-8 text-sm shadow-apple-sm"
          >
            <ExternalLink :size="16" />
            NCBI 源
          </a>
        </div>
      </div>

      <!-- 条目元数据概览：极简行内布局 -->
      <div class="flex flex-wrap items-center justify-center gap-x-12 gap-y-6 px-10 py-8 mb-12 rounded-apple-2xl text-sm bg-apple-background/[0.22] shadow-[inset_0_0_0_1px_rgba(255,255,255,0.028)]">
        <div class="flex items-center gap-3">
          <Layers :size="16" class="text-apple-secondary-text opacity-50" />
          <span class="text-apple-secondary-text">序列长度:</span>
          <span class="font-bold text-apple-text">{{ selectedEnzyme.sequenceLength }} {{ selectedSequenceUnit }}</span>
        </div>
        <div class="h-4 w-px bg-apple-border/50 hidden md:block"></div>
        <div class="flex items-center gap-3">
          <Tag :size="16" class="text-apple-secondary-text opacity-50" />
          <span class="text-apple-secondary-text">物种:</span>
          <span class="font-bold text-apple-text italic">{{ selectedEnzyme.organismName }}</span>
        </div>
        <div class="h-4 w-px bg-apple-border/50 hidden md:block"></div>
        <div class="flex items-center gap-3">
          <Database :size="16" class="text-apple-secondary-text opacity-50" />
          <span class="text-apple-secondary-text">{{ selectedSecondarySourceLabel }}:</span>
          <span class="font-bold text-apple-text">{{ selectedSecondarySourceValue }}</span>
        </div>
      </div>

      <!-- 详情内容垂直堆叠：三个版块大小均匀居中 -->
      <div class="space-y-16">
        <!-- 1. 3D 结构可视化 (核心版块) -->
        <div class="apple-card overflow-hidden shadow-[0_28px_72px_-52px_rgba(2,6,23,0.9)] border-none flex flex-col min-h-[800px]">
          <div class="px-10 py-8 border-b border-apple-border/50 flex items-center justify-between bg-apple-background/[0.18]">
            <div class="flex items-center gap-5">
              <div class="w-14 h-14 rounded-apple bg-violet-400/10 text-violet-300 flex items-center justify-center shadow-inner">
                <Dna :size="28" />
              </div>
              <div>
                <h3 class="text-xl font-bold text-apple-text">{{ selectedStructureCardTitle }}</h3>
                <p class="text-xs text-apple-secondary-text mt-1 opacity-80">{{ selectedStructureSectionDescription }}</p>
              </div>
            </div>
            <div class="flex items-center gap-4">
              <div class="px-5 py-2.5 rounded-full bg-apple-background/[0.26] text-[10px] font-bold text-apple-text uppercase tracking-wider shadow-[inset_0_0_0_1px_rgba(255,255,255,0.04)]">
                {{ selectedStructureStatus }}
              </div>
              <button
                v-if="canRenderStructureViewer"
                @click="showFullscreenViewer = true"
                class="w-14 h-14 rounded-full bg-apple-background/[0.24] flex items-center justify-center text-apple-secondary-text hover:bg-apple-blue/[0.08] hover:text-apple-blue transition-all shadow-[inset_0_0_0_1px_rgba(255,255,255,0.04)]"
                title="全屏分析"
              >
                <Maximize2 :size="20" />
              </button>
            </div>
          </div>

          <div class="flex-1 relative group/viewer bg-apple-background/[0.16]">
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

              <!-- Overlay Info -->
              <div class="absolute bottom-10 left-10 right-10 flex items-center justify-between pointer-events-none">
                <div class="flex gap-4">
                  <div class="px-6 py-3 rounded-full bg-[rgba(9,14,25,0.58)] backdrop-blur-xl shadow-[inset_0_0_0_1px_rgba(255,255,255,0.04),0_18px_42px_-30px_rgba(2,6,23,0.84)] text-[10px] font-bold text-apple-text pointer-events-auto flex items-center gap-2">
                    <Info :size="14" class="text-apple-blue" />
                    结构 ID：{{ selectedStructureId }}
                  </div>
                  <div v-if="selectedAnnotation" class="px-6 py-3 rounded-full bg-apple-blue/90 text-white shadow-[0_18px_42px_-30px_rgba(92,199,245,0.24)] text-[10px] font-bold pointer-events-auto flex items-center gap-2">
                    <MapPin :size="14" />
                    聚焦: {{ selectedAnnotation.title }}
                  </div>
                </div>
              </div>
            </template>
            
            <div v-else class="h-full p-24 flex flex-col items-center justify-center text-center space-y-10">
              <div class="w-40 h-40 bg-apple-blue/8 rounded-full flex items-center justify-center text-apple-blue/30">
                <Dna :size="80" />
              </div>
              <div class="max-w-md space-y-4">
                <p class="text-3xl font-bold text-apple-text">暂无可用的 3D 结构</p>
                <p class="text-base text-apple-secondary-text leading-relaxed">{{ rnaStructureSupportHint }}</p>
              </div>
              <button
                v-if="canLaunchRnaPrediction"
                type="button"
                class="apple-button !py-5 !px-12 text-base flex items-center gap-2 shadow-[0_18px_42px_-28px_rgba(56,189,248,0.24)] transition-transform hover:scale-105"
                @click="handleOpenRnaPrediction"
              >
                <Sparkles :size="20" />
                {{ selectedRnaPredictionButtonLabel }}
              </button>
            </div>
          </div>
        </div>

        <!-- 2. 注释工具 (功能版块) -->
        <div
          class="apple-card p-12 shadow-[0_28px_72px_-52px_rgba(2,6,23,0.9)] border-none flex flex-col transition-all duration-300"
          :class="isAnnotationToolCollapsed ? 'space-y-6' : 'space-y-12 min-h-[600px]'"
        >
          <div class="flex flex-col lg:flex-row lg:items-center justify-between gap-8">
            <div class="flex items-center gap-6">
              <div class="w-14 h-14 rounded-apple bg-apple-green/[0.12] text-apple-green flex items-center justify-center shadow-inner">
                <Layers :size="28" />
              </div>
              <div>
                <h3 class="text-xl font-bold text-apple-text">{{ selectedAnnotationToolTitle }}</h3>
                <p class="text-xs text-apple-secondary-text mt-1 opacity-80">{{ selectedAnnotationToolDescription }}</p>
                <p
                  v-if="isAnnotationToolCollapsed"
                  class="mt-3 text-xs text-apple-secondary-text"
                >
                  {{ annotationSummaryText }} · 序列长度 {{ selectedEnzyme.sequenceLength }} {{ selectedSequenceUnit }}
                </p>
              </div>
            </div>
            
            <div class="flex flex-wrap items-center gap-4">
              <template v-if="!isAnnotationToolCollapsed">
                <button
                  v-if="canImportAutomaticAnnotations"
                  type="button"
                  class="apple-button-secondary !py-4 !px-8 text-sm flex items-center gap-2 shadow-apple-sm"
                  :disabled="importingAnnotations"
                  @click="handleImportAnnotations"
                >
                  <Loader2 v-if="importingAnnotations" :size="16" class="animate-spin" />
                  <Wand2 v-else :size="16" />
                  {{ selectedAnnotationImportButtonLabel }}
                </button>
                <button
                  v-if="canPickAnnotationFromStructure"
                  type="button"
                  class="apple-button-secondary !py-4 !px-8 text-sm flex items-center gap-2 shadow-apple-sm"
                  @click="handlePickAnnotationFromStructure()"
                >
                  <MousePointerClick :size="16" />
                  3D 空间选点
                </button>
                <div class="h-8 w-px bg-apple-border mx-2 hidden sm:block"></div>
                <button
                  v-for="option in annotationTypeOptions"
                  :key="option.value"
                  type="button"
                  class="apple-button !py-4 !px-8 text-sm flex items-center gap-2 shadow-[0_18px_42px_-28px_rgba(56,189,248,0.24)] transition-transform hover:scale-105"
                  @click="openAnnotationModal(option.value)"
                >
                  <Plus :size="16" />
                  新增{{ option.label }}
                </button>
              </template>
              <button
                type="button"
                class="apple-button-secondary !py-4 !px-6 text-sm flex items-center gap-2 shadow-apple-sm"
                :aria-expanded="!isAnnotationToolCollapsed"
                @click="isAnnotationToolCollapsed = !isAnnotationToolCollapsed"
              >
                {{ isAnnotationToolCollapsed ? '展开卡片' : '收起卡片' }}
                <ChevronDown
                  :size="16"
                  class="transition-transform duration-300"
                  :class="isAnnotationToolCollapsed ? '' : 'rotate-180'"
                />
              </button>
            </div>
          </div>

          <transition name="annotation-panel">
            <div v-if="!isAnnotationToolCollapsed" class="space-y-12 flex-1">
              <!-- Sequence Map & Visualization -->
              <div class="space-y-12 flex-1">
                <div class="grid grid-cols-1 sm:grid-cols-3 gap-8">
                  <div
                    v-for="item in annotationLegend"
                    :key="item.value"
                    class="p-8 rounded-apple-xl bg-apple-background/[0.22] flex flex-col justify-between min-h-[160px] shadow-[inset_0_0_0_1px_rgba(255,255,255,0.03),0_16px_38px_-34px_rgba(2,6,23,0.82)] transition-all hover:shadow-[inset_0_0_0_1px_rgba(92,199,245,0.1),0_18px_42px_-34px_rgba(2,6,23,0.82)]"
                  >
                    <div class="flex items-center gap-3">
                      <span class="w-4 h-4 rounded-full shadow-[0_8px_18px_-14px_rgba(2,6,23,0.8)]" :style="{ backgroundColor: item.color }"></span>
                      <p class="text-xs font-bold text-apple-text uppercase tracking-widest">{{ item.label }}</p>
                    </div>
                    <div>
                      <p class="text-5xl font-extrabold text-apple-text tracking-tighter">{{ item.count }}</p>
                      <p class="text-[10px] text-apple-secondary-text mt-3 font-medium opacity-60 uppercase tracking-widest">{{ item.hint }}</p>
                    </div>
                  </div>
                </div>

                <!-- Sequence Bar -->
                <div class="p-12 rounded-apple-xl bg-apple-background/[0.22] space-y-10 shadow-[inset_0_0_0_1px_rgba(255,255,255,0.03)]">
                  <div class="flex items-center justify-between">
                    <div class="space-y-2">
                      <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text opacity-60">全长序列映射视图</p>
                      <p class="text-base text-apple-text font-semibold">
                        {{ isRnaEntry ? '按 RNA 全长坐标展示注释区间' : '点击彩色区段可在 3D 视图中快速定位' }}
                      </p>
                    </div>
                    <div class="px-6 py-2.5 rounded-full bg-apple-background/36 text-[10px] font-bold text-apple-blue uppercase tracking-wider shadow-[inset_0_0_0_1px_rgba(255,255,255,0.05)]">
                      {{ selectedEnzyme.sequenceLength }} {{ selectedSequenceUnit }}
                    </div>
                  </div>

                  <div class="relative h-24 rounded-full bg-apple-background/[0.24] shadow-[inset_0_0_0_1px_rgba(255,255,255,0.04)] p-3 group/seq">
                    <div
                      v-for="segment in annotationSequenceSegments"
                      :key="segment.id"
                      class="absolute top-3 bottom-3 rounded-full shadow-[inset_0_0_0_1px_rgba(255,255,255,0.12)] transition-all cursor-pointer hover:brightness-110 active:scale-95 z-10"
                      :class="selectedAnnotation?.id === segment.id ? 'ring-8 ring-apple-blue/12 shadow-[0_18px_40px_-26px_rgba(92,199,245,0.18)] z-20' : 'hover:z-30 hover:scale-y-110'"
                      :style="{ left: segment.left, width: segment.width, backgroundColor: segment.colorHex }"
                      :title="`${segment.title} (${segment.startResidue}-${segment.endResidue})`"
                      @click="selectedAnnotationId = segment.id"
                    ></div>
                    <!-- 刻度线辅助 -->
                    <div class="absolute inset-0 flex justify-between px-10 pointer-events-none opacity-[0.03]">
                      <div v-for="i in 10" :key="i" class="w-px h-full bg-black dark:bg-white"></div>
                    </div>
                  </div>

                  <div class="flex justify-between text-[10px] font-bold text-apple-secondary-text px-6 opacity-40 uppercase tracking-widest">
                    <span>起始位点</span>
                    <span>{{ Math.max(1, Math.floor(selectedEnzyme.sequenceLength / 2)) }}</span>
                    <span>终止位点</span>
                  </div>
                </div>
              </div>

              <!-- Annotation Items -->
              <div class="space-y-6 pt-6">
                <template v-if="hasAnnotations">
                  <div v-for="annotation in annotations" :key="annotation.id" class="group">
                    <button
                      @click="selectedAnnotationId = annotation.id"
                      class="w-full text-left p-10 rounded-apple-xl border border-transparent transition-all flex items-start justify-between gap-10"
                      :class="selectedAnnotation?.id === annotation.id
                        ? 'border-transparent bg-apple-blue/[0.05] shadow-[0_24px_60px_-40px_rgba(92,199,245,0.18)]'
                      : 'bg-apple-background/[0.2] shadow-[inset_0_0_0_1px_rgba(255,255,255,0.02),0_16px_36px_-34px_rgba(2,6,23,0.84)] hover:border-transparent hover:shadow-[inset_0_0_0_1px_rgba(92,199,245,0.06),0_18px_40px_-34px_rgba(2,6,23,0.84)]'"
                    >
                      <div class="flex-1 space-y-6">
                        <div class="flex items-center gap-4 flex-wrap">
                          <span class="inline-flex items-center gap-3 px-4 py-2 rounded-full text-[10px] font-bold uppercase tracking-widest bg-apple-background/[0.28] text-apple-text shadow-[inset_0_0_0_1px_rgba(255,255,255,0.04)]">
                            <span class="w-3.5 h-3.5 rounded-full shadow-inner" :style="{ backgroundColor: annotation.colorHex }"></span>
                            {{ annotation.annotationType === 'DOMAIN' ? '结构域' : annotation.annotationType === 'ACTIVE_SITE' ? '活性位点' : '突变位点' }}
                          </span>
                          <span class="text-[10px] font-bold text-apple-blue bg-apple-blue/5 border border-apple-blue/10 px-4 py-2 rounded-full uppercase tracking-widest">
                            位点：{{ annotation.startResidue }}{{ annotation.endResidue !== annotation.startResidue ? `-${annotation.endResidue}` : '' }}
                          </span>
                          <span v-if="annotation.chainLabel" class="text-[10px] font-bold text-apple-secondary-text bg-apple-background/[0.22] px-4 py-2 rounded-full uppercase tracking-widest">
                            链：{{ annotation.chainLabel }}
                          </span>
                        </div>
                        <div>
                          <h4 class="text-2xl font-bold text-apple-text">{{ annotation.title }}</h4>
                          <p class="text-base text-apple-secondary-text mt-3 leading-relaxed opacity-80">
                            {{ annotation.description || (annotation.annotationType === 'MUTATION' ? (annotation.mutationLabel || '未填写突变详细说明') : '该位点暂无详细功能描述') }}
                          </p>
                        </div>
                      </div>

                      <div class="flex items-center gap-4 opacity-0 group-hover:opacity-100 transition-all translate-x-6 group-hover:translate-x-0">
                        <button
                          type="button"
                          class="w-12 h-12 rounded-full bg-apple-background/[0.22] flex items-center justify-center text-apple-secondary-text hover:bg-apple-blue/[0.08] hover:text-apple-blue transition-all shadow-[inset_0_0_0_1px_rgba(255,255,255,0.04)]"
                          @click.stop="editAnnotation(annotation)"
                        >
                          <Pencil :size="18" />
                        </button>
                        <button
                          type="button"
                          class="w-12 h-12 rounded-full bg-apple-background/[0.22] flex items-center justify-center text-red-400 hover:bg-red-500/90 hover:text-white transition-all shadow-[inset_0_0_0_1px_rgba(255,255,255,0.04)]"
                          :disabled="deletingAnnotationId === annotation.id"
                          @click.stop="requestDeleteAnnotation(annotation)"
                        >
                          <Loader2 v-if="deletingAnnotationId === annotation.id" :size="18" class="animate-spin" />
                          <Trash2 v-else :size="18" />
                        </button>
                      </div>
                    </button>
                  </div>
                </template>

                <div v-else class="p-32 text-center rounded-apple-xl space-y-8 bg-black/[0.1] shadow-[inset_0_0_0_1px_rgba(255,255,255,0.05)]">
                  <div class="w-24 h-24 bg-apple-blue/8 rounded-full flex items-center justify-center mx-auto text-apple-secondary-text opacity-40">
                    <AlertCircle :size="48" />
                  </div>
                  <p class="text-lg text-apple-secondary-text font-medium italic">尚未建立结构注释。请通过上方工具栏开始标注功能位点。</p>
                </div>
              </div>
            </div>
          </transition>
        </div>

        <!-- 3. 关联文献 (证据版块) -->
        <div v-if="!isPredictedLibrary" class="apple-card p-12 shadow-[0_28px_72px_-52px_rgba(2,6,23,0.9)] border-none space-y-12 min-h-[600px] flex flex-col">
          <div class="flex flex-col lg:flex-row lg:items-center justify-between gap-8">
            <div class="flex items-center gap-6">
              <div class="w-14 h-14 rounded-apple bg-apple-green/10 text-apple-green flex items-center justify-center shadow-inner">
                <BookOpen :size="28" />
              </div>
              <div>
                <h3 class="text-xl font-bold text-apple-text">关联文献证据</h3>
                <p class="text-xs text-apple-secondary-text mt-1 opacity-80">基于多维打分模型自动关联的 PubMed 文献</p>
              </div>
            </div>
            
            <div class="flex items-center gap-4">
              <button
                @click="openImportLiteratureModal"
                :disabled="!selectedId || !!importingEnzymeId"
                class="apple-button-secondary !py-4 !px-8 text-sm flex items-center gap-2 shadow-apple-sm"
              >
                <Loader2 v-if="!!importingEnzymeId" :size="16" class="animate-spin" />
                <Upload v-else :size="16" />
                导入本地文献
              </button>
              <button
                @click="handleOpenMatcher"
                :disabled="loadingLit"
                class="apple-button !py-4 !px-8 text-sm flex items-center gap-2 shadow-[0_18px_42px_-28px_rgba(56,189,248,0.24)] transition-transform hover:scale-105"
              >
                <Loader2 v-if="loadingLit" :size="16" class="animate-spin" />
                <Sparkles v-else :size="16" />
                文献匹配工作站
              </button>
            </div>
          </div>

          <div class="space-y-10 flex-1">
            <div v-if="loadingLit" class="py-32 flex flex-col items-center justify-center space-y-8">
              <Loader2 :size="48" class="animate-spin text-apple-blue" />
              <p class="text-sm text-apple-secondary-text font-bold uppercase tracking-widest animate-pulse">正在搜寻全球文献数据库...</p>
            </div>

            <template v-else-if="enzymeLiteratures.length">
              <!-- Top Highlighted Literature -->
              <div v-if="selectedLiterature" class="p-12 rounded-apple-xl bg-apple-background/[0.22] space-y-10 shadow-[inset_0_0_0_1px_rgba(92,199,245,0.1)]">
                <div class="flex flex-col xl:flex-row justify-between items-start gap-12">
                  <div class="flex-1 space-y-6">
                    <div class="flex flex-wrap items-center gap-5">
                      <span
                        class="px-5 py-2 rounded-full text-[10px] font-bold uppercase tracking-widest shadow-[inset_0_0_0_1px_rgba(255,255,255,0.05)]"
                        :class="selectedLiterature.confidenceLevel === 'STRONG' ? 'bg-apple-green/[0.16] text-apple-green' : selectedLiterature.confidenceLevel === 'MANUAL' ? 'bg-violet-400/10 text-violet-300' : 'bg-apple-blue/[0.14] text-apple-blue'"
                      >
                        {{ selectedLiterature.confidenceLevel === 'MANUAL' ? '本地上传' : (selectedLiterature.confidenceLevel || '证据匹配') }}
                      </span>
                      <span class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text bg-apple-background/[0.24] px-5 py-2 rounded-full shadow-[inset_0_0_0_1px_rgba(255,255,255,0.04)]">
                        PMID: {{ selectedLiterature.pmid }}
                      </span>
                    </div>
                    <h4 class="text-3xl font-extrabold text-apple-text leading-tight tracking-tight">
                      {{ selectedLiterature.title }}
                    </h4>
                  </div>
                  
                  <div class="flex gap-4 shrink-0">
                    <button
                      v-if="selectedLiterature.attachmentStatus === 'DOWNLOADED'"
                      @click="isLocalUploadedLiterature(selectedLiterature) ? handlePreviewAttachment(selectedLiterature) : handleDownloadAttachment(selectedLiterature.id)"
                      class="apple-button !py-4 !px-8 text-sm flex items-center gap-2 shadow-[0_18px_42px_-28px_rgba(56,189,248,0.24)] transition-transform hover:scale-105"
                      :disabled="downloadingAttachmentId === selectedLiterature.id"
                    >
                      <Loader2 v-if="downloadingAttachmentId === selectedLiterature.id" :size="16" class="animate-spin" />
                      <Sparkles v-else :size="16" />
                      {{ isLocalUploadedLiterature(selectedLiterature) ? '浏览器查看' : '阅读 PDF 附件' }}
                    </button>
                    <a
                      v-if="selectedLiterature.sourceDb !== 'LOCAL_UPLOAD'"
                      :href="selectedLiterature.sourceUrl || `https://pubmed.ncbi.nlm.nih.gov/${selectedLiterature.pmid}/`"
                      target="_blank"
                      rel="noreferrer"
                      class="apple-button-secondary !py-4 !px-8 text-sm flex items-center gap-2 shadow-apple-sm"
                    >
                      <ExternalLink :size="16" />
                      PubMed 官网
                    </a>
                    <button
                      v-if="selectedLiterature.relationId"
                      type="button"
                      class="apple-button-secondary !py-4 !px-8 text-sm flex items-center gap-2 !text-red-500 !border-red-500/20 hover:!bg-red-500/5 shadow-apple-sm"
                      :disabled="deletingRelationIds.includes(selectedLiterature.relationId)"
                      @click="requestDeleteLiterature(selectedLiterature)"
                    >
                      <Loader2 v-if="deletingRelationIds.includes(selectedLiterature.relationId)" :size="16" class="animate-spin" />
                      <Trash2 v-else :size="16" />
                      删除关联
                    </button>
                  </div>
                </div>

                <div class="grid grid-cols-1 md:grid-cols-2 gap-8">
                  <div class="p-8 rounded-apple-xl bg-apple-background/[0.22] shadow-[inset_0_0_0_1px_rgba(255,255,255,0.03)] space-y-5">
                    <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text opacity-60">作者与发表信息</p>
                    <div class="space-y-3">
                      <p class="text-base font-bold text-apple-text leading-relaxed">{{ selectedLiterature.authors }}</p>
                      <p class="text-sm text-apple-secondary-text italic">{{ selectedLiterature.journal }} · {{ selectedLiterature.publishYear }}</p>
                    </div>
                  </div>
                  <div class="p-8 rounded-apple-xl bg-apple-background/[0.22] shadow-[inset_0_0_0_1px_rgba(255,255,255,0.03)] space-y-5">
                    <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text opacity-60">内容摘要</p>
                    <p class="text-sm leading-relaxed text-apple-text line-clamp-5 opacity-80">
                      {{ selectedLiterature.abstractText || '该条目暂未抓取到摘要内容。' }}
                    </p>
                  </div>
                </div>
              </div>

              <!-- Secondary Literatures List -->
              <div class="grid grid-cols-1 md:grid-cols-2 gap-8 pt-6">
                <button
                  v-for="lit in enzymeLiteratures"
                  :key="lit.id"
                  @click="selectedLiteratureId = lit.id"
                  class="text-left p-8 rounded-apple-xl transition-all flex flex-col justify-between min-h-[200px] group"
                  :class="selectedLiterature?.id === lit.id 
                    ? 'border-apple-blue/30 bg-apple-blue/[0.05] shadow-[0_24px_60px_-40px_rgba(92,199,245,0.18)]'
                    : 'bg-apple-background/[0.2] shadow-[inset_0_0_0_1px_rgba(255,255,255,0.03)] hover:shadow-[inset_0_0_0_1px_rgba(92,199,245,0.1)]'"
                >
                  <div class="space-y-6">
                    <div class="flex justify-between items-center">
                      <span
                        class="px-3 py-1.5 rounded-full text-[9px] font-bold uppercase tracking-widest"
                        :class="lit.confidenceLevel === 'STRONG' ? 'bg-apple-green/10 text-apple-green' : lit.confidenceLevel === 'MANUAL' ? 'bg-violet-400/10 text-violet-300' : 'bg-apple-blue/10 text-apple-blue'"
                      >
                        {{ lit.confidenceLevel === 'MANUAL' ? '本地文献' : (lit.confidenceLevel || '匹配结果') }}
                      </span>
                      <div class="flex items-center gap-3">
                        <span class="text-[9px] text-apple-secondary-text font-bold uppercase tracking-widest opacity-40">PMID: {{ lit.pmid }}</span>
                        <button
                          v-if="lit.relationId"
                          type="button"
                          class="w-8 h-8 rounded-full bg-black/18 flex items-center justify-center text-red-400 hover:bg-red-500 hover:text-white transition-all shadow-[inset_0_0_0_1px_rgba(255,255,255,0.05)]"
                          :disabled="deletingRelationIds.includes(lit.relationId)"
                          @click.stop="requestDeleteLiterature(lit)"
                          title="删除当前关联"
                        >
                          <Loader2 v-if="deletingRelationIds.includes(lit.relationId)" :size="14" class="animate-spin" />
                          <Trash2 v-else :size="14" />
                        </button>
                      </div>
                    </div>
                    <h4 class="text-lg font-bold text-apple-text line-clamp-2 leading-tight group-hover:text-apple-blue transition-colors">
                      {{ lit.title }}
                    </h4>
                  </div>
                  <div class="pt-6 space-y-3">
                    <p class="text-[10px] text-apple-secondary-text italic font-medium opacity-60 truncate uppercase tracking-wider">{{ lit.journal }}, {{ lit.publishYear }}</p>
                    <button
                      v-if="isLocalUploadedLiterature(lit)"
                      type="button"
                      class="text-xs font-semibold text-apple-blue hover:text-apple-text transition-colors inline-flex items-center gap-2"
                      :disabled="downloadingAttachmentId === lit.id"
                      @click.stop="handlePreviewAttachment(lit)"
                    >
                      <Loader2 v-if="downloadingAttachmentId === lit.id" :size="14" class="animate-spin" />
                      <ExternalLink v-else :size="14" />
                      在浏览器中查看
                    </button>
                  </div>
                </button>
              </div>
            </template>

            <div v-else class="p-32 text-center rounded-apple-xl space-y-8 bg-black/[0.1] shadow-[inset_0_0_0_1px_rgba(255,255,255,0.05)]">
              <div class="w-24 h-24 bg-apple-green/8 rounded-full flex items-center justify-center mx-auto text-apple-secondary-text opacity-40">
                <BookOpen :size="48" />
              </div>
              <p class="text-lg text-apple-secondary-text font-medium italic">尚未关联文献证据。请前往文献匹配工作站获取最新研究成果。</p>
            </div>
          </div>
        </div>

        <!-- 4. 预测入库资产说明 (替代文献版块) -->
        <div v-else class="apple-card p-16 shadow-[0_28px_72px_-52px_rgba(2,6,23,0.9)] border-none space-y-12 min-h-[600px] flex flex-col justify-center">
          <div class="flex items-center gap-6">
            <div class="w-14 h-14 rounded-apple bg-apple-green/10 text-apple-green flex items-center justify-center shadow-inner">
              <Sparkles :size="28" />
            </div>
            <div>
              <h3 class="text-2xl font-bold text-apple-text">预测入库资产说明</h3>
              <p class="text-sm text-apple-secondary-text opacity-80">本地确认入库的 AI 预测结构资产，独立于公共数据库管理</p>
            </div>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-12">
            <div class="p-12 rounded-apple-xl bg-black/[0.1] space-y-6 shadow-[inset_0_0_0_1px_rgba(255,255,255,0.04)]">
              <p class="text-[10px] font-bold uppercase tracking-widest text-apple-blue opacity-80">资产属性</p>
              <p class="text-xl font-bold text-apple-text">预测确认件 (Verified Prediction)</p>
              <p class="text-base leading-relaxed text-apple-secondary-text opacity-80">
                本仓库仅存储经过人工确认命名的预测结果。预测生成的 PDB 文件已完整同步至本地存储，支持持久化分析与注释。
              </p>
            </div>

            <div class="p-12 rounded-apple-xl bg-black/[0.1] space-y-10 shadow-[inset_0_0_0_1px_rgba(255,255,255,0.04)]">
              <p class="text-[10px] font-bold uppercase tracking-widest text-apple-blue opacity-80">工作流入口</p>
              <div class="grid grid-cols-2 gap-6">
                <button @click="router.push('/prediction/minifold')" class="apple-button-secondary !py-4 !text-[10px] font-bold uppercase tracking-widest hover:bg-apple-blue hover:text-white transition-colors">MiniFold</button>
                <button @click="router.push('/prediction/nvidia')" class="apple-button-secondary !py-4 !text-[10px] font-bold uppercase tracking-widest hover:bg-apple-blue hover:text-white transition-colors">NVIDIA ESMFold</button>
                <button @click="router.push('/prediction/trrosettarna')" class="apple-button-secondary !py-4 !text-[10px] font-bold uppercase tracking-widest hover:bg-apple-blue hover:text-white transition-colors">trRosettaRNA</button>
                <button @click="router.push('/library/imported')" class="apple-button-secondary !py-4 !text-[10px] font-bold uppercase tracking-widest hover:bg-apple-blue hover:text-white transition-colors">导入库</button>
              </div>
            </div>
          </div>
        </div>

        <!-- 5. 补充信息 (功能说明) -->
        <div v-if="selectedEnzyme.description" class="apple-card p-12 shadow-[0_28px_72px_-52px_rgba(2,6,23,0.9)] border-none bg-black/[0.1]">
          <div class="flex items-center gap-4 mb-8">
            <Info :size="20" class="text-apple-secondary-text opacity-40" />
            <p class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text opacity-60">补充说明</p>
          </div>
          <p class="text-lg leading-relaxed text-apple-text whitespace-pre-wrap opacity-90 italic">{{ selectedEnzyme.description }}</p>
        </div>
          </div>
        </template>
      </div>
    </transition>

    <!-- Fullscreen 3D Viewer Modal -->
    <transition name="fade">
      <div v-if="showFullscreenViewer" class="fixed inset-0 z-[100] bg-black/80 backdrop-blur-md flex flex-col">
        <div class="h-16 px-8 flex items-center justify-between border-b border-apple-border/60 bg-[rgba(5,9,18,0.38)]">
          <div class="flex items-center gap-4">
            <h3 class="text-white font-bold">{{ selectedEnzyme?.proteinName }}</h3>
            <span class="px-2 py-0.5 rounded-full bg-apple-blue/90 text-white text-[10px] font-bold uppercase tracking-widest">
              {{ selectedStructureId }}
            </span>
          </div>
          <button 
            @click="showFullscreenViewer = false"
            class="w-10 h-10 rounded-full bg-[rgba(9,14,25,0.54)] text-white flex items-center justify-center hover:bg-[rgba(12,18,30,0.82)] transition-all shadow-[inset_0_0_0_1px_rgba(255,255,255,0.05)]"
          >
            <X :size="20" />
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

    <!-- Import Literature Modal -->
    <transition name="fade">
      <div
        v-if="showImportLiteratureModal"
        class="fixed inset-0 z-[110] bg-black/50 backdrop-blur-sm flex items-center justify-center p-4"
        @click.self="closeImportLiteratureModal"
      >
        <div class="w-full max-w-xl apple-card p-8 space-y-6">
          <div class="flex items-start justify-between gap-4">
            <div class="space-y-1">
              <h3 class="text-xl font-bold text-apple-text">导入本地文献</h3>
              <p class="text-sm text-apple-secondary-text leading-relaxed">
                为当前酶条目添加本地文献附件。系统会把文件复制到平台存储目录，并在关联文献里显示。
              </p>
            </div>
            <button
              @click="closeImportLiteratureModal"
              :disabled="!!importingEnzymeId"
              class="w-10 h-10 rounded-full hover:bg-black/5 dark:hover:bg-white/5 text-apple-secondary-text flex items-center justify-center disabled:opacity-50"
            >
              <X :size="18" />
            </button>
          </div>

          <div class="space-y-4">
            <div class="p-8 rounded-apple bg-black/[0.08] flex flex-col items-center justify-center space-y-4 shadow-[inset_0_0_0_1px_rgba(255,255,255,0.05)]">
              <Upload :size="32" class="text-apple-secondary-text opacity-40" />
              <input
                type="file"
                class="hidden"
                id="file-upload"
                @change="handleImportFileChange"
                :disabled="!!importingEnzymeId"
              />
              <label for="file-upload" class="apple-button-secondary !py-2 !px-4 text-xs cursor-pointer">选择文件</label>
              <p v-if="importLiteratureFile" class="text-sm font-bold text-apple-blue">
                已选择：{{ importLiteratureFile.name }}
              </p>
              <p v-else class="text-xs text-apple-secondary-text italic">未选择任何文件</p>
            </div>
            <p v-if="importLiteratureError" class="text-xs text-red-500 font-bold text-center">
              {{ importLiteratureError }}
            </p>
          </div>

          <div class="flex justify-end gap-3">
            <button
              @click="closeImportLiteratureModal"
              :disabled="!!importingEnzymeId"
              class="apple-button-secondary !py-2.5 !px-6 text-xs disabled:opacity-50"
            >
              取消
            </button>
            <button
              @click="handleImportLiterature"
              :disabled="!!importingEnzymeId || !importLiteratureFile"
              class="apple-button !py-2.5 !px-6 text-xs flex items-center gap-2 disabled:opacity-50 shadow-apple-md"
            >
              <Loader2 v-if="!!importingEnzymeId" :size="14" class="animate-spin" />
              <Check v-else :size="14" />
              确认导入
            </button>
          </div>
        </div>
      </div>
    </transition>

    <!-- Annotation Modal -->
    <transition name="fade">
      <div
        v-if="showAnnotationModal"
        class="fixed inset-0 z-[115] bg-black/50 backdrop-blur-sm flex items-center justify-center p-4"
        @click.self="closeAnnotationModal"
      >
        <div class="w-full max-w-2xl apple-card p-8 space-y-6 shadow-[0_28px_72px_-52px_rgba(2,6,23,0.9)]">
          <div class="flex items-start justify-between gap-4">
            <div class="space-y-1">
              <h3 class="text-xl font-bold text-apple-text">{{ editingAnnotationId ? '编辑结构注释' : '新增结构注释' }}</h3>
              <p class="text-sm text-apple-secondary-text leading-relaxed">
                为当前酶条目记录结构域、活性位点或突变位点。保存后可在 3D 结构中直接聚焦查看。
              </p>
            </div>
            <button
              @click="closeAnnotationModal"
              :disabled="annotationSaving"
              class="w-10 h-10 rounded-full hover:bg-black/5 dark:hover:bg-white/5 text-apple-secondary-text flex items-center justify-center disabled:opacity-50"
            >
              <X :size="18" />
            </button>
          </div>

          <div v-if="structurePickMode" class="p-4 rounded-apple bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 flex items-center gap-3">
            <MousePointerClick :size="18" class="text-amber-600" />
            <p class="text-xs text-amber-700 dark:text-amber-300 font-bold uppercase tracking-wider">
              3D 选点模式激活：请点击 3D 结构中的残基，位点信息将自动回填
            </p>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div class="space-y-2">
              <span class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text ml-1">注释类型</span>
              <div class="flex items-center gap-2">
                <select v-model="annotationForm.annotationType" class="apple-input w-full text-sm">
                  <option v-for="option in annotationTypeOptions" :key="option.value" :value="option.value">
                    {{ option.label }}
                  </option>
                </select>
                <button
                  v-if="canPickAnnotationFromStructure"
                  type="button"
                  class="apple-button-secondary !py-2.5 !px-3 text-[10px] flex items-center gap-1 shrink-0"
                  @click="handlePickAnnotationFromStructure(annotationForm.annotationType)"
                >
                  <MousePointerClick :size="12" />
                  3D 选点
                </button>
              </div>
            </div>

            <div class="space-y-2">
              <span class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text ml-1">注释标题</span>
              <input v-model="annotationForm.title" type="text" class="apple-input w-full text-sm" placeholder="例如：催化核心区域 / Ser128" />
            </div>

            <div class="space-y-2">
              <span class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text ml-1">起始残基</span>
              <input v-model.number="annotationForm.startResidue" type="number" min="1" class="apple-input w-full text-sm" placeholder="例如 128" />
            </div>

            <div class="space-y-2">
              <span class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text ml-1">
                {{ annotationForm.annotationType === 'MUTATION' ? '结束残基 (锁定)' : '结束残基' }}
              </span>
              <input
                v-model.number="annotationForm.endResidue"
                type="number"
                min="1"
                class="apple-input w-full text-sm"
                :disabled="annotationForm.annotationType === 'MUTATION'"
                :placeholder="annotationForm.annotationType === 'MUTATION' ? '单残基位点' : '例如 196'"
              />
            </div>

            <div class="space-y-2">
              <span class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text ml-1">链标识</span>
              <input v-model="annotationForm.chainLabel" type="text" class="apple-input w-full text-sm" placeholder="可选，例如 A" />
            </div>

            <div class="space-y-2">
              <span class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text ml-1">标记颜色</span>
              <div class="flex items-center gap-3">
                <input v-model="annotationForm.colorHex" type="color" class="h-10 w-14 rounded bg-transparent cursor-pointer shadow-[inset_0_0_0_1px_rgba(255,255,255,0.08)]" />
                <input v-model="annotationForm.colorHex" type="text" class="apple-input flex-1 text-sm font-mono" placeholder="#3B82F6" />
              </div>
            </div>

            <div v-if="annotationForm.annotationType === 'MUTATION'" class="md:col-span-2 space-y-2">
              <span class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text ml-1">突变说明</span>
              <input v-model="annotationForm.mutationLabel" type="text" class="apple-input w-full text-sm" placeholder="例如：S128A / G45D" />
            </div>

            <div class="md:col-span-2 space-y-2">
              <span class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text ml-1">备注说明</span>
              <textarea
                v-model="annotationForm.description"
                rows="4"
                class="apple-input w-full resize-none text-sm leading-relaxed"
                placeholder="填写功能解释、实验依据、保守性分析结论等"
              ></textarea>
            </div>
          </div>

          <div class="space-y-2">
            <p v-if="annotationNotice" class="text-xs text-apple-blue font-bold text-center animate-pulse">{{ annotationNotice }}</p>
            <p v-if="annotationError" class="text-xs text-red-500 font-bold text-center">{{ annotationError }}</p>
          </div>

          <div class="flex justify-end gap-3 pt-2">
            <button
              @click="closeAnnotationModal"
              :disabled="annotationSaving"
              class="apple-button-secondary !py-3 !px-8 text-xs disabled:opacity-50"
            >
              取消
            </button>
            <button
              @click="handleSaveAnnotation"
              :disabled="annotationSaving"
              class="apple-button !py-3 !px-8 text-xs flex items-center gap-2 disabled:opacity-50 shadow-apple-md"
            >
              <Loader2 v-if="annotationSaving" :size="14" class="animate-spin" />
              <Check v-else :size="14" />
              保存注释
            </button>
          </div>
        </div>
      </div>
    </transition>

    <ConfirmDialog
      :open="showDeleteEnzymeDialog"
      title="删除酶条目"
      :message="`确定要移除“${selectedEnzyme?.proteinName || selectedEnzyme?.accession || '该条目'}”吗？删除后将无法恢复。`"
      confirm-text="确认删除"
      :loading="isDeleting"
      danger
      @cancel="closeDeleteEnzymeDialog"
      @confirm="confirmDeleteEnzyme"
    />

    <ConfirmDialog
      :open="showDeleteAnnotationDialog"
      title="删除注释"
      :message="`确定删除注释“${pendingDeleteAnnotation?.title || '未命名注释'}”吗？该操作会立即从当前条目中移除。`"
      confirm-text="确认删除"
      :loading="deletingAnnotationId === pendingDeleteAnnotation?.id"
      danger
      @cancel="closeDeleteAnnotationDialog"
      @confirm="confirmDeleteAnnotation"
    />

    <ConfirmDialog
      :open="showDeleteLiteratureDialog"
      title="删除关联文献"
      :message="`确定从当前酶条目中移除“${pendingDeleteLiterature?.title || '该文献'}”吗？如果这是仅当前条目使用的本地上传附件，系统也会一并清理存储文件。`"
      confirm-text="确认删除"
      :loading="Boolean(pendingDeleteLiterature?.relationId && deletingRelationIds.includes(pendingDeleteLiterature.relationId))"
      danger
      @cancel="closeDeleteLiteratureDialog"
      @confirm="confirmDeleteLiterature"
    />
  </div>
</template>

<style scoped>
.card-switch-enter-active,
.card-switch-leave-active {
  transition:
    opacity 0.52s cubic-bezier(0.22, 1, 0.36, 1),
    transform 0.52s cubic-bezier(0.22, 1, 0.36, 1),
    filter 0.52s cubic-bezier(0.22, 1, 0.36, 1);
}

.card-switch-enter-from {
  opacity: 0;
  transform: translateY(20px) scale(0.985);
  filter: blur(16px);
}

.card-switch-leave-to {
  opacity: 0;
  transform: translateY(-12px) scale(1.01);
  filter: blur(12px);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

.annotation-panel-enter-active,
.annotation-panel-leave-active {
  transition:
    opacity 0.24s ease,
    transform 0.24s ease;
}

.annotation-panel-enter-from,
.annotation-panel-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

.apple-input:focus {
  @apply ring-4 ring-apple-blue/10;
}

@media (prefers-reduced-motion: reduce) {
  .card-switch-enter-active,
  .card-switch-leave-active {
    transition-duration: 160ms;
  }

  .card-switch-enter-from,
  .card-switch-leave-to {
    transform: none;
    filter: none;
  }
}
</style>
