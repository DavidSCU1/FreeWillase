<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, type CSSProperties, type Component } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  Database,
  FlaskConical,
  Orbit,
  ShieldCheck,
  Sparkles,
} from 'lucide-vue-next'

type LibraryKey = 'imported' | 'predicted'

interface LibraryOption {
  key: LibraryKey
  title: string
  subtitle: string
  description: string
  summary: string
  to: string
  icon: Component
  accentTextClass: string
  accentSurfaceClass: string
  accentGlow: string
  accentBorder: string
  tags: string[]
  bullets: string[]
}

const libraryOptions: LibraryOption[] = [
  {
    key: 'imported',
    title: '导入酶库',
    subtitle: '从 Accession 建库后，继续补结构、注释与文献证据。',
    description: '适合整理正式 accession 条目，逐步完善结构浏览、手动注释、文献关联与后续分析。',
    summary: '适合作为正式主酶库持续维护',
    to: '/library/imported',
    icon: FlaskConical,
    accentTextClass: 'text-apple-blue',
    accentSurfaceClass: 'bg-apple-blue/12',
    accentGlow: 'rgba(56, 189, 248, 0.18)',
    accentBorder: 'rgba(56, 189, 248, 0.22)',
    tags: ['Accession 导入', '结构浏览', '文献关联'],
    bullets: ['查看 accession 导入条目', '继续做结构注释与证据整理', '适合作为主酶库使用'],
  },
  {
    key: 'predicted',
    title: '预测成果库',
    subtitle: '收纳已确认入库的预测结构，与导入条目独立分仓。',
    description: '适合管理 MiniFold、云预测或 RNA 预测得到的结构成果，避免与正式 accession 条目混杂。',
    summary: '适合沉淀模型输出与版本结果',
    to: '/library/predicted',
    icon: Sparkles,
    accentTextClass: 'text-purple-400',
    accentSurfaceClass: 'bg-purple-400/12',
    accentGlow: 'rgba(192, 132, 252, 0.16)',
    accentBorder: 'rgba(192, 132, 252, 0.2)',
    tags: ['预测结构', '结果归档', '独立分仓'],
    bullets: ['查看已确认的预测成果', '保持和正式酶库分离管理', '适合复盘模型输出与版本结果'],
  },
]

const importedLibrary = libraryOptions[0]
const predictedLibrary = libraryOptions[1]

const router = useRouter()
const stageRef = ref<HTMLElement | null>(null)
const pointerRatio = ref(0.5)
const prefersReducedMotion = ref(false)
const isCoarsePointer = ref(false)
const viewportWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1440)

let reduceMotionQuery: MediaQueryList | null = null
let coarsePointerQuery: MediaQueryList | null = null

const interactiveEnabled = computed(
  () => viewportWidth.value >= 768 && !prefersReducedMotion.value && !isCoarsePointer.value,
)

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value))
}

function syncViewport() {
  viewportWidth.value = window.innerWidth
}

function syncMediaPreferences() {
  reduceMotionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  coarsePointerQuery = window.matchMedia('(pointer: coarse)')
  prefersReducedMotion.value = reduceMotionQuery.matches
  isCoarsePointer.value = coarsePointerQuery.matches
}

function handlePointerMove(event: PointerEvent) {
  if (!interactiveEnabled.value) return
  const stage = stageRef.value
  if (!stage) return
  const rect = stage.getBoundingClientRect()
  pointerRatio.value = clamp((event.clientX - rect.left) / rect.width, 0, 1)
}

function handlePointerLeave() {
  pointerRatio.value = 0.5
}

function focusLibrary(key: LibraryKey) {
  if (!interactiveEnabled.value) return
  pointerRatio.value = key === 'imported' ? 0.18 : 0.82
}

const importedEmphasis = computed(() => (
  interactiveEnabled.value ? clamp((0.5 - pointerRatio.value) / 0.5, 0, 1) : 0
))

const predictedEmphasis = computed(() => (
  interactiveEnabled.value ? clamp((pointerRatio.value - 0.5) / 0.5, 0, 1) : 0
))

function getCardStyle(option: LibraryOption): CSSProperties {
  const emphasis = option.key === 'imported' ? importedEmphasis.value : predictedEmphasis.value
  const opposite = option.key === 'imported' ? predictedEmphasis.value : importedEmphasis.value
  const blur = opposite > 0.02 ? opposite * 8 : 0
  const scale = emphasis > 0 ? 1 + emphasis * 0.024 : 1
  const translateX = option.key === 'imported'
    ? emphasis * -10 + opposite * -4
    : emphasis * 10 + opposite * 4
  const brightness = emphasis > 0 ? 1 + emphasis * 0.04 : 1 - opposite * 0.03
  const saturation = emphasis > 0 ? 1 + emphasis * 0.03 : 1 - opposite * 0.08
  const opacity = 1 - opposite * 0.08
  const filterParts = []

  if (blur > 0.01) filterParts.push(`blur(${blur}px)`)
  if (Math.abs(brightness - 1) > 0.001) filterParts.push(`brightness(${brightness})`)
  if (Math.abs(saturation - 1) > 0.001) filterParts.push(`saturate(${saturation})`)

  return {
    '--library-accent-glow': option.accentGlow,
    '--library-accent-border': option.accentBorder,
    transform: `translateX(${translateX}px) scale(${scale})`,
    filter: filterParts.length ? filterParts.join(' ') : 'none',
    opacity,
    zIndex: emphasis >= opposite ? 2 : 1,
  } as CSSProperties
}

const stageStyle = computed<CSSProperties>(() => {
  const importedGlow = importedEmphasis.value
  const predictedGlow = predictedEmphasis.value

  return {
    background: `
      radial-gradient(circle at 18% 22%, rgba(56, 189, 248, ${0.1 + importedGlow * 0.16}), transparent 28%),
      radial-gradient(circle at 82% 20%, rgba(192, 132, 252, ${0.08 + predictedGlow * 0.14}), transparent 28%),
      linear-gradient(180deg, rgba(9, 15, 28, 0.92), rgba(8, 13, 24, 0.88))
    `,
  }
})

const dividerStyle = computed<CSSProperties>(() => ({
  transform: interactiveEnabled.value
    ? `translateX(${(pointerRatio.value - 0.5) * -42}px) scaleY(${1 + Math.abs(pointerRatio.value - 0.5) * 0.26})`
    : 'none',
  opacity: interactiveEnabled.value ? 0.9 : 0.7,
  boxShadow: `
    0 18px 40px -28px rgba(2, 6, 23, 0.8),
    0 0 28px rgba(56, 189, 248, ${0.06 + importedEmphasis.value * 0.12}),
    0 0 28px rgba(192, 132, 252, ${0.04 + predictedEmphasis.value * 0.1})
  `,
}))

const importedAuraStyle = computed<CSSProperties>(() => ({
  opacity: `${0.2 + importedEmphasis.value * 0.26}`,
}))

const predictedAuraStyle = computed<CSSProperties>(() => ({
  opacity: `${0.18 + predictedEmphasis.value * 0.24}`,
}))

function enterLibrary(key: LibraryKey) {
  const destination = libraryOptions.find((item) => item.key === key)?.to
  if (destination) {
    router.push(destination)
  }
}

onMounted(() => {
  syncViewport()
  syncMediaPreferences()
  window.addEventListener('resize', syncViewport)
  reduceMotionQuery?.addEventListener?.('change', syncMediaPreferences)
  coarsePointerQuery?.addEventListener?.('change', syncMediaPreferences)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewport)
  reduceMotionQuery?.removeEventListener?.('change', syncMediaPreferences)
  coarsePointerQuery?.removeEventListener?.('change', syncMediaPreferences)
})
</script>

<template>
  <div class="space-y-8 motion-stagger">
    <div class="max-w-4xl space-y-4">
      <div class="inline-flex items-center gap-2 rounded-full bg-white/[0.03] px-4 py-2 text-[11px] font-bold uppercase tracking-[0.28em] text-apple-text/78 shadow-[inset_0_1px_0_rgba(255,255,255,0.025)]">
        <Database :size="13" class="text-apple-blue" />
        酶库分仓入口
      </div>
      <h1 class="text-4xl md:text-5xl font-bold tracking-tight text-apple-text">酶库中心</h1>
      <p class="max-w-3xl text-base md:text-lg leading-8 text-apple-text/78">
        先决定你要维护的是正式 accession 条目，还是已经确认的预测结构成果。两个入口分别服务于不同的数据生命周期，但都保持同一套深色工作台体验。
      </p>
    </div>

    <div
      ref="stageRef"
      class="library-stage relative overflow-hidden rounded-[34px] p-4 md:p-5"
      :style="stageStyle"
      @pointermove="handlePointerMove"
      @pointerleave="handlePointerLeave"
    >
      <div class="library-stage-aura left-[-8%] top-[6%] h-[280px] w-[280px] bg-apple-blue/18" :style="importedAuraStyle"></div>
      <div class="library-stage-aura right-[-10%] top-[8%] h-[280px] w-[280px] bg-purple-400/16" :style="predictedAuraStyle"></div>

      <div class="relative z-10 grid gap-4 md:grid-cols-[minmax(0,1fr)_56px_minmax(0,1fr)] md:items-stretch">
        <button
          type="button"
          class="library-card group relative overflow-hidden rounded-[28px] px-7 py-7 text-left md:px-8 md:py-8"
          :style="getCardStyle(importedLibrary)"
          @focus="focusLibrary(importedLibrary.key)"
          @click="enterLibrary(importedLibrary.key)"
        >
          <div class="flex items-center gap-3">
            <div class="inline-flex items-center gap-2 rounded-full bg-white/[0.04] px-4 py-2 text-[11px] font-bold uppercase tracking-[0.22em] text-apple-text/88 shadow-[inset_0_1px_0_rgba(255,255,255,0.025)]">
              <Database :size="13" />
              {{ importedLibrary.title }}
            </div>
          </div>

          <div class="mt-8 flex items-start gap-5">
            <div
              class="flex h-16 w-16 shrink-0 items-center justify-center rounded-[22px] shadow-[0_18px_32px_-24px_rgba(2,6,23,0.9)]"
              :class="[importedLibrary.accentSurfaceClass, importedLibrary.accentTextClass]"
            >
              <component :is="importedLibrary.icon" :size="26" />
            </div>

            <div class="flex-1 space-y-4">
              <h2 class="text-4xl font-bold tracking-tight text-white">{{ importedLibrary.title }}</h2>
              <p class="text-lg leading-8 text-apple-text/92">{{ importedLibrary.subtitle }}</p>
              <p class="text-sm leading-8 text-apple-text/76">{{ importedLibrary.description }}</p>
            </div>
          </div>

          <div class="mt-7 flex flex-wrap gap-2">
            <span
              v-for="tag in importedLibrary.tags"
              :key="tag"
              class="rounded-full bg-white/[0.04] px-3 py-1.5 text-[11px] font-semibold text-apple-text/88 shadow-[inset_0_1px_0_rgba(255,255,255,0.02)]"
            >
              {{ tag }}
            </span>
          </div>

          <div class="mt-7 space-y-3">
            <div
              v-for="bullet in importedLibrary.bullets"
              :key="bullet"
              class="flex items-center gap-3 text-sm text-apple-text/90"
            >
              <div
                class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full"
                :class="[importedLibrary.accentSurfaceClass, importedLibrary.accentTextClass]"
              >
                <ShieldCheck :size="12" />
              </div>
              <span>{{ bullet }}</span>
            </div>
          </div>

          <div class="mt-8 flex items-center justify-between gap-5 border-t border-white/[0.04] pt-5">
            <div class="space-y-1">
              <p class="text-[10px] font-bold uppercase tracking-[0.24em] text-apple-text/52">推荐用途</p>
              <p class="text-sm font-medium text-apple-text/90">{{ importedLibrary.summary }}</p>
            </div>

            <div class="inline-flex items-center gap-2 text-sm font-semibold text-white">
              进入库
              <ArrowRight :size="16" />
            </div>
          </div>
        </button>

        <div class="hidden md:flex items-center justify-center">
          <div class="library-divider h-[calc(100%-36px)] w-[56px] rounded-full" :style="dividerStyle"></div>
        </div>

        <button
          type="button"
          class="library-card group relative overflow-hidden rounded-[28px] px-7 py-7 text-left md:px-8 md:py-8 md:text-right"
          :style="getCardStyle(predictedLibrary)"
          @focus="focusLibrary(predictedLibrary.key)"
          @click="enterLibrary(predictedLibrary.key)"
        >
          <div class="flex items-center gap-3 md:justify-end">
            <div class="inline-flex items-center gap-2 rounded-full bg-white/[0.04] px-4 py-2 text-[11px] font-bold uppercase tracking-[0.22em] text-apple-text/88 shadow-[inset_0_1px_0_rgba(255,255,255,0.025)]">
              <Orbit :size="13" />
              {{ predictedLibrary.title }}
            </div>
          </div>

          <div class="mt-8 flex items-start gap-5 md:flex-row-reverse">
            <div
              class="flex h-16 w-16 shrink-0 items-center justify-center rounded-[22px] shadow-[0_18px_32px_-24px_rgba(2,6,23,0.9)]"
              :class="[predictedLibrary.accentSurfaceClass, predictedLibrary.accentTextClass]"
            >
              <component :is="predictedLibrary.icon" :size="26" />
            </div>

            <div class="flex-1 space-y-4">
              <h2 class="text-4xl font-bold tracking-tight text-white">{{ predictedLibrary.title }}</h2>
              <p class="text-lg leading-8 text-apple-text/92">{{ predictedLibrary.subtitle }}</p>
              <p class="text-sm leading-8 text-apple-text/76">{{ predictedLibrary.description }}</p>
            </div>
          </div>

          <div class="mt-7 flex flex-wrap gap-2 md:justify-end">
            <span
              v-for="tag in predictedLibrary.tags"
              :key="tag"
              class="rounded-full bg-white/[0.04] px-3 py-1.5 text-[11px] font-semibold text-apple-text/88 shadow-[inset_0_1px_0_rgba(255,255,255,0.02)]"
            >
              {{ tag }}
            </span>
          </div>

          <div class="mt-7 space-y-3">
            <div
              v-for="bullet in predictedLibrary.bullets"
              :key="bullet"
              class="flex items-center gap-3 text-sm text-apple-text/90 md:flex-row-reverse md:justify-end"
            >
              <div
                class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full"
                :class="[predictedLibrary.accentSurfaceClass, predictedLibrary.accentTextClass]"
              >
                <ShieldCheck :size="12" />
              </div>
              <span>{{ bullet }}</span>
            </div>
          </div>

              <div class="mt-8 flex items-center justify-between gap-5 border-t border-white/[0.04] pt-5 md:flex-row-reverse">
            <div class="space-y-1">
              <p class="text-[10px] font-bold uppercase tracking-[0.24em] text-apple-text/52">推荐用途</p>
              <p class="text-sm font-medium text-apple-text/90">{{ predictedLibrary.summary }}</p>
            </div>

            <div class="inline-flex items-center gap-2 text-sm font-semibold text-white">
              进入库
              <ArrowRight :size="16" />
            </div>
          </div>
            </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.library-stage {
  box-shadow:
    inset 0 1px 0 rgb(255 255 255 / 0.025),
    0 28px 72px -48px rgba(2, 6, 23, 0.72);
}

.library-stage-aura {
  position: absolute;
  border-radius: 9999px;
  filter: blur(78px);
  pointer-events: none;
  transition: opacity 260ms ease;
}

.library-card {
  position: relative;
  border: 1px solid rgba(255, 255, 255, 0.04);
  background:
    linear-gradient(180deg, rgba(13, 20, 35, 0.62), rgba(11, 18, 31, 0.38)),
    radial-gradient(circle at top left, var(--library-accent-glow), transparent 40%);
  box-shadow:
    inset 0 1px 0 rgb(255 255 255 / 0.025),
    0 24px 56px -42px rgba(2, 6, 23, 0.78);
  transition:
    transform 260ms cubic-bezier(0.22, 1, 0.36, 1),
    filter 260ms cubic-bezier(0.22, 1, 0.36, 1),
    opacity 260ms ease,
    border-color 260ms ease,
    box-shadow 260ms ease;
}

.library-card::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background:
    radial-gradient(circle at top left, var(--library-accent-glow), transparent 42%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.025), transparent 18%, transparent 82%, rgba(255, 255, 255, 0.012));
  pointer-events: none;
}

.library-card > * {
  position: relative;
  z-index: 1;
}

.library-divider {
  border: 1px solid rgba(255, 255, 255, 0.035);
  background:
    linear-gradient(180deg, rgba(20, 28, 44, 0.9), rgba(16, 24, 39, 0.74)),
    linear-gradient(180deg, rgba(56, 189, 248, 0.06), rgba(192, 132, 252, 0.06));
  backdrop-filter: blur(12px);
  transition:
    transform 260ms cubic-bezier(0.22, 1, 0.36, 1),
    opacity 260ms ease,
    box-shadow 260ms ease;
}

.library-card:hover {
  border-color: rgb(255 255 255 / 0.12);
  box-shadow:
    inset 0 1px 0 rgb(255 255 255 / 0.05),
    0 34px 72px -42px rgba(2, 6, 23, 0.88);
}
</style>
