import { computed, ref, onUnmounted } from 'vue'
import { getLatestImportTask, getImportTask, importAccessions, listEnzymes, deleteEnzyme } from '@/utils/api'
import type { EnzymeEntry, ImportTask } from '@/types'

export function useNcbiImport() {
  const taskName = ref('ncbi_batch_bootstrap_001')
  const accessionInput = ref('WP_010248927.1\nNP_001092.1')
  const ncbiEmail = ref(localStorage.getItem('ncbi_email') || '')
  const ncbiApiKey = ref(localStorage.getItem('ncbi_api_key') || '')
  const task = ref<ImportTask | null>(null)
  const enzymes = ref<EnzymeEntry[]>([])
  const loading = ref(false)
  const errorMessage = ref('')
  let pollTimer: number | null = null

  onUnmounted(() => {
    stopPolling()
  })

  function startPolling() {
    if (pollTimer) return
    pollTimer = window.setInterval(async () => {
      if (task.value && (task.value.status === 'RUNNING' || task.value.status === 'PENDING')) {
        try {
          const updatedTask = await getImportTask(task.value.id)
          task.value = updatedTask
          if (updatedTask.status !== 'RUNNING' && updatedTask.status !== 'PENDING') {
            stopPolling()
            await refreshEnzymeLibrary()
          }
        } catch (error) {
          console.error('Polling error:', error)
          stopPolling()
        }
      } else {
        stopPolling()
      }
    }, 2000)
  }

  function stopPolling() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  const accessionCount = computed(() =>
    accessionInput.value
      .split(/\r?\n|,|\s+/)
      .map((item) => item.trim())
      .filter(Boolean).length,
  )

  function parseAccessions() {
    return accessionInput.value
      .split(/\r?\n|,|\s+/)
      .map((item) => item.trim())
      .filter(Boolean)
  }

  async function refreshEnzymeLibrary() {
    enzymes.value = await listEnzymes()
  }

  async function refreshLatestTask() {
    task.value = await getLatestImportTask()
    if (task.value && (task.value.status === 'RUNNING' || task.value.status === 'PENDING')) {
      startPolling()
    }
  }

  async function refreshAll() {
    await Promise.all([refreshEnzymeLibrary(), refreshLatestTask()])
  }

  async function removeEnzyme(id: number) {
    try {
      await deleteEnzyme(id)
      await refreshEnzymeLibrary()
      return true
    } catch (error) {
      console.error('删除失败', error)
      return false
    }
  }

  async function submitImport() {
    loading.value = true
    errorMessage.value = ''
    
    // Save credentials to localStorage
    localStorage.setItem('ncbi_email', ncbiEmail.value)
    localStorage.setItem('ncbi_api_key', ncbiApiKey.value)

    try {
      task.value = await importAccessions({
        taskName: taskName.value.trim(),
        accessions: parseAccessions(),
        ncbiEmail: ncbiEmail.value.trim() || undefined,
        ncbiApiKey: ncbiApiKey.value.trim() || undefined,
      })
      startPolling()
      await refreshEnzymeLibrary()
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '导入失败'
    } finally {
      loading.value = false
    }
  }

  return {
    taskName,
    accessionInput,
    ncbiEmail,
    ncbiApiKey,
    task,
    enzymes,
    loading,
    errorMessage,
    accessionCount,
    parseAccessions,
    refreshEnzymeLibrary,
    refreshLatestTask,
    refreshAll,
    removeEnzyme,
    submitImport,
  }
}
