import { computed, ref } from 'vue'
import { downloadLiteratureRelation, getEnzymeLiteratures, listAllLiteratures, scanLiteratures } from '@/utils/api'
import type { LiteratureRecord } from '@/types'

export function useLiterature() {
  const literatures = ref<LiteratureRecord[]>([])
  const enzymeLiteratures = ref<LiteratureRecord[]>([])
  const listLoading = ref(false)
  const enzymeLoading = ref(false)
  const scanLoading = ref(false)
  const downloadingRelationIds = ref<number[]>([])
  const error = ref<string | null>(null)

  const ncbiEmail = ref(localStorage.getItem('ncbi_email') || '')
  const ncbiApiKey = ref(localStorage.getItem('ncbi_api_key') || '')
  const loading = computed(() => listLoading.value || scanLoading.value)

  const fetchAllLiteratures = async () => {
    listLoading.value = true
    error.value = null
    try {
      literatures.value = await listAllLiteratures()
    } catch (err) {
      error.value = '无法获取文献记录'
      console.error(err)
    } finally {
      listLoading.value = false
    }
  }

  const fetchEnzymeLiteratures = async (enzymeId: number) => {
    enzymeLoading.value = true
    try {
      enzymeLiteratures.value = await getEnzymeLiteratures(enzymeId)
    } catch (err) {
      console.error('获取酶关联文献失败', err)
      enzymeLiteratures.value = []
    } finally {
      enzymeLoading.value = false
    }
  }

  const scan = async (enzymeIds?: number[]) => {
    scanLoading.value = true
    localStorage.setItem('ncbi_email', ncbiEmail.value)
    localStorage.setItem('ncbi_api_key', ncbiApiKey.value)

    try {
      await scanLiteratures({
        ncbiEmail: ncbiEmail.value.trim() || undefined,
        ncbiApiKey: ncbiApiKey.value.trim() || undefined,
        enzymeIds: enzymeIds?.length ? enzymeIds : undefined,
      })
    } catch (err) {
      console.error('匹配失败', err)
      throw err
    } finally {
      scanLoading.value = false
    }
  }

  const downloadLiterature = async (relationId: number) => {
    try {
      downloadingRelationIds.value = [...downloadingRelationIds.value, relationId]
      await downloadLiteratureRelation(relationId)
      literatures.value = literatures.value.map((item) =>
        item.relationId === relationId ? { ...item, savedToLibrary: true } : item,
      )
    } catch (err) {
      console.error('文献下载失败', err)
      throw err
    } finally {
      downloadingRelationIds.value = downloadingRelationIds.value.filter((id) => id !== relationId)
    }
  }

  return {
    literatures,
    enzymeLiteratures,
    loading,
    listLoading,
    enzymeLoading,
    scanLoading,
    downloadingRelationIds,
    error,
    ncbiEmail,
    ncbiApiKey,
    fetchAllLiteratures,
    fetchEnzymeLiteratures,
    scan,
    downloadLiterature,
  }
}
