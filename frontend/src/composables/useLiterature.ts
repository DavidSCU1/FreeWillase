import { ref } from 'vue'
import { listAllLiteratures, matchLiterature, matchAllLiteratures, getEnzymeLiteratures } from '@/utils/api'

export interface LiteratureRecord {
  id: number
  title: string
  authors: string
  journal: string
  publishYear: number
  doi: string
  pmid: string
  abstractText: string
  sourceDb: string
  createdAt: string
  confidenceScore?: number
  confidenceLevel?: string
  matchedEnzymeName?: string
  matchedEnzymeAccession?: string
  matchedFields?: string
}

export function useLiterature() {
  const literatures = ref<LiteratureRecord[]>([])
  const enzymeLiteratures = ref<LiteratureRecord[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  const ncbiEmail = ref(localStorage.getItem('ncbi_email') || '')
  const ncbiApiKey = ref(localStorage.getItem('ncbi_api_key') || '')

  const fetchAllLiteratures = async () => {
    loading.value = true
    error.value = null
    try {
      literatures.value = await listAllLiteratures()
    } catch (err) {
      error.value = '无法获取文献记录'
      console.error(err)
    } finally {
      loading.value = false
    }
  }

  const fetchEnzymeLiteratures = async (enzymeId: number) => {
    loading.value = true
    try {
      enzymeLiteratures.value = await getEnzymeLiteratures(enzymeId)
    } catch (err) {
      console.error('获取酶关联文献失败', err)
      enzymeLiteratures.value = []
    } finally {
      loading.value = false
    }
  }

  const matchForEnzyme = async (enzymeId: number) => {
    // Save credentials to localStorage
    localStorage.setItem('ncbi_email', ncbiEmail.value)
    localStorage.setItem('ncbi_api_key', ncbiApiKey.value)

    try {
      await matchLiterature(enzymeId, {
        ncbiEmail: ncbiEmail.value.trim() || undefined,
        ncbiApiKey: ncbiApiKey.value.trim() || undefined,
      })
      await fetchAllLiteratures()
    } catch (err) {
      console.error('匹配失败', err)
    }
  }

  const matchAll = async () => {
    loading.value = true
    localStorage.setItem('ncbi_email', ncbiEmail.value)
    localStorage.setItem('ncbi_api_key', ncbiApiKey.value)

    try {
      await matchAllLiteratures({
        ncbiEmail: ncbiEmail.value.trim() || undefined,
        ncbiApiKey: ncbiApiKey.value.trim() || undefined,
      })
      // Since it's async, we might want to poll or just refresh after a while
      // For now, let's just refresh once
      setTimeout(fetchAllLiteratures, 3000)
    } catch (err) {
      console.error('全库匹配启动失败', err)
    } finally {
      loading.value = false
    }
  }

  return {
    literatures,
    enzymeLiteratures,
    loading,
    error,
    ncbiEmail,
    ncbiApiKey,
    fetchAllLiteratures,
    fetchEnzymeLiteratures,
    matchForEnzyme,
    matchAll
  }
}
