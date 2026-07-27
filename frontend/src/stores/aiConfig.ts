import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { AiConfigProvider, UserAiConfigStatusResponse } from '@/types'
import { getUserAiConfigStatus, saveUserAiConfig } from '@/utils/api'

export const useAiConfigStore = defineStore('aiConfig', () => {
  const status = ref<UserAiConfigStatusResponse | null>(null)
  const isLoading = ref(false)
  const isSaving = ref(false)
  const isPromptOpen = ref(false)
  const activeProvider = ref<AiConfigProvider | null>(null)
  const error = ref('')
  const dismissedRouteKey = ref('')

  const providerStatus = computed(() => {
    if (!status.value || !activeProvider.value) return null
    return status.value[activeProvider.value]
  })

  async function refreshStatus(force = false) {
    if (isLoading.value && !force) return status.value
    try {
      isLoading.value = true
      status.value = await getUserAiConfigStatus()
      return status.value
    } catch (e: any) {
      error.value = e?.message || '读取 AI 配置状态失败'
      return status.value
    } finally {
      isLoading.value = false
    }
  }

  async function ensurePromptForRoute(provider: AiConfigProvider | undefined, routeKey: string) {
    if (!provider) {
      isPromptOpen.value = false
      activeProvider.value = null
      error.value = ''
      dismissedRouteKey.value = ''
      return
    }

    if (dismissedRouteKey.value && dismissedRouteKey.value !== routeKey) {
      dismissedRouteKey.value = ''
    }

    const currentStatus = await refreshStatus()
    const providerState = currentStatus?.[provider]
    activeProvider.value = provider

    if (!providerState?.configured && dismissedRouteKey.value !== routeKey) {
      isPromptOpen.value = true
      error.value = ''
      return
    }

    isPromptOpen.value = false
  }

  function skipPrompt(routeKey: string) {
    dismissedRouteKey.value = routeKey
    isPromptOpen.value = false
    error.value = ''
  }

  async function saveConfig(provider: AiConfigProvider, payload: { apiKey: string; baseUrl?: string }) {
    try {
      isSaving.value = true
      error.value = ''
      status.value = await saveUserAiConfig({
        provider,
        apiKey: payload.apiKey,
        baseUrl: payload.baseUrl,
      })
      isPromptOpen.value = false
    } catch (e: any) {
      error.value = e?.message || '保存 AI 配置失败'
      throw e
    } finally {
      isSaving.value = false
    }
  }

  return {
    status,
    isLoading,
    isSaving,
    isPromptOpen,
    activeProvider,
    providerStatus,
    error,
    refreshStatus,
    ensurePromptForRoute,
    skipPrompt,
    saveConfig,
  }
})
