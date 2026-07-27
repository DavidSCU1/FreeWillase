<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { KeyRound, Loader2, Sparkles } from 'lucide-vue-next'
import { useRoute } from 'vue-router'
import { useAiConfigStore } from '@/stores/aiConfig'

const route = useRoute()
const aiConfigStore = useAiConfigStore()

const apiKey = ref('')
const baseUrl = ref('')

const providerMeta = computed(() => {
  if (aiConfigStore.activeProvider === 'nvidia') {
    return {
      eyebrow: 'NVIDIA 凭证配置',
      title: '这页需要你的 NVIDIA API Key',
      description: '我们会为当前登录用户在项目空间里创建一份私有 env 配置文件，之后这个账号再进入 NVIDIA 工作台时就会自动复用。',
      apiLabel: 'NVIDIA API Key',
      apiPlaceholder: '请输入你的 NVIDIA API Key',
      showBaseUrl: true,
      baseUrlLabel: 'NVIDIA API URL（可选）',
      baseUrlPlaceholder: '默认：https://health.api.nvidia.com',
    }
  }

  return {
    eyebrow: 'MiniFold 凭证配置',
    title: '这页需要你的 Ark API Key',
    description: 'MiniFold 的 AI 优化链路会读取当前账号的私有 env 配置文件。这里我们只保存你的 Key，Ark URL 固定走项目内置地址。',
    apiLabel: 'ARK API Key',
    apiPlaceholder: '请输入你的 ARK API Key',
    showBaseUrl: false,
    baseUrlLabel: 'ARK API URL（可选）',
    baseUrlPlaceholder: '默认使用运行时内置地址',
  }
})

const configuredHint = computed(() => {
  const status = aiConfigStore.providerStatus
  if (!status) return ''
  if (status.userScopedFilePresent) {
    return '当前账号已经有一份私有 env 文件，重新保存会直接更新它。'
  }
  return '当前账号还没有私有 env 文件，保存后会自动创建。'
})

const canSave = computed(() => apiKey.value.trim().length > 0)

watch(() => aiConfigStore.isPromptOpen, open => {
  if (!open) {
    apiKey.value = ''
    baseUrl.value = ''
  }
})

async function handleSave() {
  if (!aiConfigStore.activeProvider) return
  try {
    await aiConfigStore.saveConfig(aiConfigStore.activeProvider, {
      apiKey: apiKey.value,
      baseUrl: baseUrl.value || undefined,
    })
  } catch {
    // Error state is already surfaced by the store.
  }
}

function handleSkip() {
  aiConfigStore.skipPrompt(route.fullPath)
}
</script>

<template>
  <transition name="fade">
    <div
      v-if="aiConfigStore.isPromptOpen && aiConfigStore.activeProvider"
      class="fixed inset-0 z-[140] bg-black/60 backdrop-blur-sm flex items-center justify-center p-4"
      @click.self="!aiConfigStore.isSaving && handleSkip()"
    >
      <div class="w-full max-w-xl apple-card p-8 space-y-6 shadow-apple-2xl">
        <div class="space-y-3">
          <div class="inline-flex items-center gap-2 rounded-full bg-apple-blue/10 px-3 py-1 text-[10px] font-bold uppercase tracking-widest text-apple-blue">
            <Sparkles :size="12" />
            {{ providerMeta.eyebrow }}
          </div>
          <div class="space-y-2">
            <h3 class="text-xl font-bold text-apple-text">{{ providerMeta.title }}</h3>
            <p class="text-sm leading-relaxed text-apple-secondary-text">{{ providerMeta.description }}</p>
            <p class="text-xs leading-relaxed text-apple-secondary-text/90">{{ configuredHint }}</p>
          </div>
        </div>

        <div class="grid gap-4">
          <div class="space-y-2">
            <label class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">{{ providerMeta.apiLabel }}</label>
            <div class="relative">
              <KeyRound class="absolute left-3 top-1/2 -translate-y-1/2 text-apple-secondary-text" :size="14" />
              <input
                v-model="apiKey"
                type="password"
                class="apple-input pl-10 text-sm"
                :placeholder="providerMeta.apiPlaceholder"
              >
            </div>
          </div>

          <div v-if="providerMeta.showBaseUrl" class="space-y-2">
            <label class="text-[10px] font-bold uppercase tracking-widest text-apple-secondary-text">{{ providerMeta.baseUrlLabel }}</label>
            <input
              v-model="baseUrl"
              type="text"
              class="apple-input text-sm"
              :placeholder="providerMeta.baseUrlPlaceholder"
            >
          </div>

          <div v-if="aiConfigStore.error" class="rounded-apple bg-red-500/[0.06] px-4 py-3 text-sm text-red-400 shadow-[inset_0_0_0_1px_rgba(239,68,68,0.12)]">
            {{ aiConfigStore.error }}
          </div>
        </div>

        <div class="flex justify-end gap-3">
          <button
            type="button"
            class="apple-button-secondary !py-2.5 !px-6 text-xs disabled:opacity-50"
            :disabled="aiConfigStore.isSaving"
            @click="handleSkip"
          >
            先跳过
          </button>
          <button
            type="button"
            class="apple-button !py-2.5 !px-6 text-xs text-white disabled:opacity-50 inline-flex items-center gap-2"
            :disabled="aiConfigStore.isSaving || !canSave"
            @click="handleSave"
          >
            <Loader2 v-if="aiConfigStore.isSaving" class="animate-spin" :size="14" />
            <span>{{ aiConfigStore.isSaving ? '保存中...' : '保存并继续' }}</span>
          </button>
        </div>
      </div>
    </div>
  </transition>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
</style>
