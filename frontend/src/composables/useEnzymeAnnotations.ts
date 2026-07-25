import { computed, ref } from 'vue'
import {
  createEnzymeAnnotation,
  deleteEnzymeAnnotation,
  importUniProtAnnotations,
  listEnzymeAnnotations,
  updateEnzymeAnnotation,
} from '@/utils/api'
import type { EnzymeAnnotation, EnzymeAnnotationType } from '@/types'

export interface EnzymeAnnotationForm {
  annotationType: EnzymeAnnotationType
  title: string
  startResidue: number | null
  endResidue: number | null
  chainLabel: string
  mutationLabel: string
  colorHex: string
  description: string
}

export function createEmptyAnnotationForm(): EnzymeAnnotationForm {
  return {
    annotationType: 'DOMAIN',
    title: '',
    startResidue: null,
    endResidue: null,
    chainLabel: '',
    mutationLabel: '',
    colorHex: '#3B82F6',
    description: '',
  }
}

export function toAnnotationForm(annotation: EnzymeAnnotation): EnzymeAnnotationForm {
  return {
    annotationType: annotation.annotationType,
    title: annotation.title,
    startResidue: annotation.startResidue,
    endResidue: annotation.endResidue,
    chainLabel: annotation.chainLabel || '',
    mutationLabel: annotation.mutationLabel || '',
    colorHex: annotation.colorHex || '#3B82F6',
    description: annotation.description || '',
  }
}

export function useEnzymeAnnotations() {
  const annotations = ref<EnzymeAnnotation[]>([])
  const listLoading = ref(false)
  const saving = ref(false)
  const deletingId = ref<number | null>(null)
  const importing = ref(false)

  const hasAnnotations = computed(() => annotations.value.length > 0)

  const fetchAnnotations = async (enzymeId: number) => {
    listLoading.value = true
    try {
      annotations.value = await listEnzymeAnnotations(enzymeId)
    } finally {
      listLoading.value = false
    }
  }

  const saveAnnotation = async (enzymeId: number, form: EnzymeAnnotationForm, annotationId?: number | null) => {
    saving.value = true
    try {
      const payload = {
        annotationType: form.annotationType,
        title: form.title || undefined,
        startResidue: form.startResidue || 0,
        endResidue: form.annotationType === 'MUTATION'
          ? form.startResidue || 0
          : (form.endResidue || form.startResidue || 0),
        chainLabel: form.chainLabel || undefined,
        mutationLabel: form.mutationLabel || undefined,
        colorHex: form.colorHex || undefined,
        description: form.description || undefined,
      }
      const saved = annotationId
        ? await updateEnzymeAnnotation(enzymeId, annotationId, payload)
        : await createEnzymeAnnotation(enzymeId, payload)
      const existingIndex = annotations.value.findIndex((item) => item.id === saved.id)
      if (existingIndex >= 0) {
        annotations.value = annotations.value.map((item) => (item.id === saved.id ? saved : item))
      } else {
        annotations.value = [...annotations.value, saved].sort((a, b) => a.startResidue - b.startResidue || a.endResidue - b.endResidue)
      }
      return saved
    } finally {
      saving.value = false
    }
  }

  const removeAnnotation = async (enzymeId: number, annotationId: number) => {
    deletingId.value = annotationId
    try {
      await deleteEnzymeAnnotation(enzymeId, annotationId)
      annotations.value = annotations.value.filter((item) => item.id !== annotationId)
    } finally {
      deletingId.value = null
    }
  }

  const importFromUniProt = async (enzymeId: number) => {
    importing.value = true
    try {
      const imported = await importUniProtAnnotations(enzymeId)
      if (!imported.length) {
        return imported
      }
      const knownIds = new Set(annotations.value.map((item) => item.id))
      annotations.value = [...annotations.value, ...imported.filter((item) => !knownIds.has(item.id))]
        .sort((a, b) => a.startResidue - b.startResidue || a.endResidue - b.endResidue)
      return imported
    } finally {
      importing.value = false
    }
  }

  return {
    annotations,
    hasAnnotations,
    listLoading,
    saving,
    deletingId,
    importing,
    fetchAnnotations,
    saveAnnotation,
    removeAnnotation,
    importFromUniProt,
  }
}
