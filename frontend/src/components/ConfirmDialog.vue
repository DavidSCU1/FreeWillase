<script setup lang="ts">
withDefaults(defineProps<{
  open: boolean
  title: string
  message: string
  confirmText?: string
  cancelText?: string
  loading?: boolean
  danger?: boolean
}>(), {
  confirmText: '确认',
  cancelText: '取消',
  loading: false,
  danger: false,
})

const emit = defineEmits<{
  confirm: []
  cancel: []
}>()
</script>

<template>
  <transition name="fade">
    <div
      v-if="open"
      class="fixed inset-0 z-[120] bg-black/50 backdrop-blur-sm flex items-center justify-center p-4"
      @click.self="!loading && emit('cancel')"
    >
      <div class="w-full max-w-md apple-card p-8 space-y-6 shadow-apple-2xl">
        <div class="space-y-2">
          <p class="text-[10px] font-bold uppercase tracking-widest" :class="danger ? 'text-red-500' : 'text-apple-blue'">
            {{ danger ? '危险操作确认' : '操作确认' }}
          </p>
          <h3 class="text-xl font-bold text-apple-text">{{ title }}</h3>
          <p class="text-sm text-apple-secondary-text leading-relaxed whitespace-pre-wrap">{{ message }}</p>
        </div>

        <div class="flex justify-end gap-3">
          <button
            type="button"
            class="apple-button-secondary !py-2.5 !px-6 text-xs disabled:opacity-50"
            :disabled="loading"
            @click="emit('cancel')"
          >
            {{ cancelText }}
          </button>
          <button
            type="button"
            class="!py-2.5 !px-6 text-xs text-white rounded-apple disabled:opacity-50"
            :class="danger ? 'bg-red-500 hover:bg-red-600 shadow-lg shadow-red-500/20' : 'apple-button'"
            :disabled="loading"
            @click="emit('confirm')"
          >
            {{ loading ? '处理中...' : confirmText }}
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
