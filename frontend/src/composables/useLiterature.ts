import { computed, ref } from 'vue'
import { deleteEnzymeLiteratureRelation, downloadLiteratureRelation, getEnzymeLiteratures, getLiteratureScanStatus, importEnzymeLiteratureFile, listAllLiteratures, scanLiteratures, uploadEnzymeLiteratureFile } from '@/utils/api'
import type { LiteratureRecord, LiteratureScanStatus } from '@/types'

function createIdleScanStatus(): LiteratureScanStatus {
  return {
    status: 'IDLE',
    message: '等待启动文献扫描',
    scope: 'ALL',
    apiKeyEnabled: false,
    totalEnzymes: 0,
    processedEnzymes: 0,
    discoveredCandidates: 0,
    failedEnzymes: 0,
  }
}

export function useLiterature() {
  const literatures = ref<LiteratureRecord[]>([])
  const scanStatus = ref<LiteratureScanStatus | null>(createIdleScanStatus())
  const enzymeLiteratures = ref<LiteratureRecord[]>([])
  const listLoading = ref(false)
  const enzymeLoading = ref(false)
  const scanLoading = ref(false)
  const scanStatusLoading = ref(false)
  const downloadingRelationIds = ref<number[]>([])
  const deletingRelationIds = ref<number[]>([])
  const importingEnzymeId = ref<number | null>(null)
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

  const fetchScanStatus = async () => {
    scanStatusLoading.value = true
    try {
      scanStatus.value = await getLiteratureScanStatus() ?? createIdleScanStatus()
    } catch (err) {
      console.error('获取文献扫描状态失败', err)
      scanStatus.value = createIdleScanStatus()
    } finally {
      scanStatusLoading.value = false
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
      const updatedRecord = await downloadLiteratureRelation(relationId)
      literatures.value = literatures.value.map((item) =>
        item.relationId === relationId ? { ...item, ...updatedRecord, savedToLibrary: true } : item,
      )
      enzymeLiteratures.value = enzymeLiteratures.value.map((item) =>
        item.relationId === relationId ? { ...item, ...updatedRecord, savedToLibrary: true } : item,
      )
    } catch (err) {
      console.error('文献下载失败', err)
      throw err
    } finally {
      downloadingRelationIds.value = downloadingRelationIds.value.filter((id) => id !== relationId)
    }
  }

  const importLocalLiterature = async (enzymeId: number, file: File | string) => {
    try {
      importingEnzymeId.value = enzymeId
      const importedRecord = typeof file === 'string'
        ? await importEnzymeLiteratureFile(enzymeId, file)
        : await uploadEnzymeLiteratureFile(enzymeId, file)
      const existingIndex = enzymeLiteratures.value.findIndex((item) => item.id === importedRecord.id)
      if (existingIndex >= 0) {
        enzymeLiteratures.value = enzymeLiteratures.value.map((item) =>
          item.id === importedRecord.id ? { ...item, ...importedRecord } : item,
        )
      } else {
        enzymeLiteratures.value = [importedRecord, ...enzymeLiteratures.value]
      }

      const allIndex = literatures.value.findIndex(
        (item) => item.id === importedRecord.id && item.enzymeId === importedRecord.enzymeId,
      )
      if (allIndex >= 0) {
        literatures.value = literatures.value.map((item, index) =>
          index === allIndex ? { ...item, ...importedRecord } : item,
        )
      } else {
        literatures.value = [importedRecord, ...literatures.value]
      }
      return importedRecord
    } catch (err) {
      console.error('本地文献导入失败', err)
      throw err
    } finally {
      importingEnzymeId.value = null
    }
  }

  const removeEnzymeLiterature = async (enzymeId: number, relationId: number) => {
    try {
      deletingRelationIds.value = [...deletingRelationIds.value, relationId]
      await deleteEnzymeLiteratureRelation(enzymeId, relationId)
      enzymeLiteratures.value = enzymeLiteratures.value.filter((item) => item.relationId !== relationId)
      literatures.value = literatures.value.filter((item) => !(item.enzymeId === enzymeId && item.relationId === relationId))
    } catch (err) {
      console.error('删除关联文献失败', err)
      throw err
    } finally {
      deletingRelationIds.value = deletingRelationIds.value.filter((id) => id !== relationId)
    }
  }

  return {
    literatures,
    scanStatus,
    enzymeLiteratures,
    loading,
    listLoading,
    enzymeLoading,
    scanLoading,
    scanStatusLoading,
    downloadingRelationIds,
    deletingRelationIds,
    importingEnzymeId,
    error,
    ncbiEmail,
    ncbiApiKey,
    fetchAllLiteratures,
    fetchScanStatus,
    fetchEnzymeLiteratures,
    scan,
    downloadLiterature,
    importLocalLiterature,
    removeEnzymeLiterature,
  }
}
