<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import BrandMark from '@/components/BrandMark.vue'
import { BRAND_PUZZLE_BOARD_UNITS, brandPuzzleTemplates, type BrandPuzzlePieceTemplate } from '@/utils/brandPuzzle'

type PieceState = BrandPuzzlePieceTemplate & {
  x: number
  y: number
  rotation: number
  locked: boolean
  order: number
  settleDelay: number
}

const emit = defineEmits<{
  solved: []
}>()

const stageRef = ref<HTMLElement | null>(null)
const stageWidth = ref(960)
const stageHeight = ref(640)
const pieces = ref<PieceState[]>([])
const activePieceId = ref<string | null>(null)
const zCounter = ref(20)
const justSolved = ref(false)
const autoSolving = ref(false)
const logoTapCount = ref(0)

let resizeObserver: ResizeObserver | null = null
let solvedTimer: ReturnType<typeof setTimeout> | null = null
let finishTimer: ReturnType<typeof setTimeout> | null = null
let logoTapTimer: ReturnType<typeof setTimeout> | null = null
let dragState:
  | {
      id: string
      pointerId: number
      originX: number
      originY: number
      startClientX: number
      startClientY: number
    }
  | null = null

const boardSize = computed(() => Math.min(stageWidth.value * 0.32, stageHeight.value * 0.52, 340))
const boardLeft = computed(() => (stageWidth.value - boardSize.value) / 2)
const boardTop = computed(() => (stageHeight.value - boardSize.value) / 2)
const unitScale = computed(() => boardSize.value / BRAND_PUZZLE_BOARD_UNITS)
const snapDistance = computed(() => Math.max(18, boardSize.value * 0.07))
const solved = computed(() => pieces.value.length > 0 && pieces.value.every((piece) => piece.locked))
const sortedPieces = computed(() => [...pieces.value].sort((a, b) => a.order - b.order))

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max)
}

function getPieceWidth(piece: BrandPuzzlePieceTemplate) {
  return piece.width * unitScale.value
}

function getPieceHeight(piece: BrandPuzzlePieceTemplate) {
  return piece.height * unitScale.value
}

function getTargetX(piece: BrandPuzzlePieceTemplate) {
  return boardLeft.value + piece.targetX * unitScale.value
}

function getTargetY(piece: BrandPuzzlePieceTemplate) {
  return boardTop.value + piece.targetY * unitScale.value
}

function getScatterPoint(index: number, piece: BrandPuzzlePieceTemplate) {
  const pieceWidth = getPieceWidth(piece)
  const pieceHeight = getPieceHeight(piece)
  const edgePadding = 24
  const horizontalInset = Math.max(32, stageWidth.value * 0.08)
  const verticalInset = Math.max(40, stageHeight.value * 0.1)
  const positions = [
    { x: horizontalInset, y: verticalInset, rotation: -16 },
    { x: boardLeft.value + boardSize.value * 0.12, y: verticalInset - 6, rotation: -8 },
    { x: stageWidth.value - pieceWidth - horizontalInset, y: verticalInset + 12, rotation: 15 },
    { x: horizontalInset + 10, y: stageHeight.value - pieceHeight - verticalInset, rotation: 12 },
    { x: boardLeft.value - pieceWidth - 28, y: boardTop.value + boardSize.value * 0.18, rotation: -18 },
    { x: stageWidth.value - pieceWidth - horizontalInset - 8, y: boardTop.value + boardSize.value * 0.22, rotation: 14 },
    { x: stageWidth.value - pieceWidth - horizontalInset, y: stageHeight.value - pieceHeight - verticalInset - 6, rotation: -12 },
    { x: Math.max(edgePadding, boardLeft.value - pieceWidth - boardSize.value * 0.28), y: boardTop.value + boardSize.value * 0.58, rotation: -10 },
    { x: Math.min(stageWidth.value - pieceWidth - edgePadding, boardLeft.value + boardSize.value + boardSize.value * 0.12), y: Math.max(edgePadding, boardTop.value - pieceHeight * 0.52), rotation: 9 },
    { x: boardLeft.value + boardSize.value * 0.42, y: stageHeight.value - pieceHeight - verticalInset + 10, rotation: 6 },
  ]
  const preset = positions[index % positions.length]

  return {
    x: clamp(preset.x, edgePadding, stageWidth.value - pieceWidth - edgePadding),
    y: clamp(preset.y, edgePadding, stageHeight.value - pieceHeight - edgePadding),
    rotation: preset.rotation,
  }
}

function resetPuzzle() {
  if (solvedTimer) {
    clearTimeout(solvedTimer)
    solvedTimer = null
  }
  if (finishTimer) {
    clearTimeout(finishTimer)
    finishTimer = null
  }
  justSolved.value = false
  pieces.value = brandPuzzleTemplates.map((piece, index) => {
    const scatter = getScatterPoint(index, piece)
    return {
      ...piece,
      x: scatter.x,
      y: scatter.y,
      rotation: scatter.rotation,
      locked: false,
      order: index + 1,
      settleDelay: 0,
    }
  })
  activePieceId.value = null
  dragState = null
  autoSolving.value = false
  logoTapCount.value = 0
  if (logoTapTimer) {
    clearTimeout(logoTapTimer)
    logoTapTimer = null
  }
}

function updateStageBounds() {
  if (!stageRef.value) return
  const rect = stageRef.value.getBoundingClientRect()
  if (!rect.width || !rect.height) return
  stageWidth.value = rect.width
  stageHeight.value = rect.height
  resetPuzzle()
}

function getPieceStyle(piece: PieceState) {
  return {
    width: `${getPieceWidth(piece)}px`,
    height: `${getPieceHeight(piece)}px`,
    left: `${piece.x}px`,
    top: `${piece.y}px`,
    zIndex: piece.order,
    transform: `rotate(${piece.rotation}deg) scale(${activePieceId.value === piece.id ? 1.02 : 1})`,
    transition: activePieceId.value === piece.id
      ? 'none'
      : autoSolving.value
        ? 'transform 420ms cubic-bezier(0.22, 1, 0.36, 1), left 520ms cubic-bezier(0.22, 1, 0.36, 1), top 520ms cubic-bezier(0.22, 1, 0.36, 1), box-shadow 220ms ease'
        : 'transform 180ms ease, left 180ms ease, top 180ms ease, box-shadow 180ms ease',
    transitionDelay: autoSolving.value ? `${piece.settleDelay}ms` : '0ms',
  }
}

function getPieceBrandStyle(piece: BrandPuzzlePieceTemplate) {
  return {
    width: `${boardSize.value}px`,
    height: `${boardSize.value}px`,
    left: `${-piece.targetX * unitScale.value}px`,
    top: `${-piece.targetY * unitScale.value}px`,
  }
}

function getPieceOverlayStyle(piece: BrandPuzzlePieceTemplate) {
  return {
    background: 'linear-gradient(180deg, rgba(255,255,255,0.015), transparent 34%, rgba(2,8,20,0.02))',
    boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.03)',
  }
}

function snapPiece(piece: PieceState) {
  piece.x = getTargetX(piece)
  piece.y = getTargetY(piece)
  piece.rotation = 0
  piece.locked = true
  piece.order = ++zCounter.value
}

function trySnap(piece: PieceState) {
  const deltaX = piece.x - getTargetX(piece)
  const deltaY = piece.y - getTargetY(piece)
  const distance = Math.hypot(deltaX, deltaY)
  if (distance <= snapDistance.value) {
    snapPiece(piece)
  }
}

function handlePointerDown(piece: PieceState, event: PointerEvent) {
  if (piece.locked) return

  activePieceId.value = piece.id
  piece.order = ++zCounter.value
  dragState = {
    id: piece.id,
    pointerId: event.pointerId,
    originX: piece.x,
    originY: piece.y,
    startClientX: event.clientX,
    startClientY: event.clientY,
  }

  const element = event.currentTarget as HTMLElement | null
  element?.setPointerCapture(event.pointerId)
}

function handlePointerMove(piece: PieceState, event: PointerEvent) {
  if (!dragState || dragState.id !== piece.id || dragState.pointerId !== event.pointerId) return

  const nextX = dragState.originX + (event.clientX - dragState.startClientX)
  const nextY = dragState.originY + (event.clientY - dragState.startClientY)
  piece.x = clamp(nextX, 8, stageWidth.value - getPieceWidth(piece) - 8)
  piece.y = clamp(nextY, 8, stageHeight.value - getPieceHeight(piece) - 8)
}

function finishDrag(piece: PieceState, event: PointerEvent) {
  if (!dragState || dragState.id !== piece.id || dragState.pointerId !== event.pointerId) return

  trySnap(piece)
  activePieceId.value = null
  dragState = null

  const element = event.currentTarget as HTMLElement | null
  if (element?.hasPointerCapture(event.pointerId)) {
    element.releasePointerCapture(event.pointerId)
  }
}

function triggerEasterEggAutoSolve() {
  if (solved.value || autoSolving.value) return

  autoSolving.value = true
  activePieceId.value = null
  dragState = null
  pieces.value = pieces.value.map((piece, index) => ({
    ...piece,
    x: getTargetX(piece),
    y: getTargetY(piece),
    rotation: 0,
    locked: true,
    order: 200 + index,
    settleDelay: index * 42,
  }))
}

function handleLogoTap() {
  if (solved.value || autoSolving.value) return

  logoTapCount.value += 1
  if (logoTapTimer) {
    clearTimeout(logoTapTimer)
  }
  logoTapTimer = setTimeout(() => {
    logoTapCount.value = 0
    logoTapTimer = null
  }, 2000)

  if (logoTapCount.value >= 5) {
    logoTapCount.value = 0
    if (logoTapTimer) {
      clearTimeout(logoTapTimer)
      logoTapTimer = null
    }
    triggerEasterEggAutoSolve()
  }
}

watch(solved, (value, previousValue) => {
  if (!value || previousValue) return
  justSolved.value = true
  if (solvedTimer) {
    clearTimeout(solvedTimer)
  }
  if (finishTimer) {
    clearTimeout(finishTimer)
  }
  solvedTimer = setTimeout(() => {
    justSolved.value = false
    solvedTimer = null
    autoSolving.value = false
  }, 1200)
  finishTimer = setTimeout(() => {
    emit('solved')
    finishTimer = null
  }, 900)
})

onMounted(() => {
  updateStageBounds()
  resizeObserver = new ResizeObserver(() => {
    updateStageBounds()
  })
  if (stageRef.value) {
    resizeObserver.observe(stageRef.value)
  }
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
  if (solvedTimer) {
    clearTimeout(solvedTimer)
    solvedTimer = null
  }
  if (finishTimer) {
    clearTimeout(finishTimer)
    finishTimer = null
  }
})
</script>

<template>
  <div class="brand-puzzle-overlay">
    <div class="brand-puzzle-overlay__mesh"></div>
    <div class="brand-puzzle-overlay__halo brand-puzzle-overlay__halo--left"></div>
    <div class="brand-puzzle-overlay__halo brand-puzzle-overlay__halo--right"></div>

    <div class="brand-puzzle-overlay__content">
      <div class="brand-puzzle-overlay__header">
        <div class="brand-puzzle-overlay__brand">
          <button
            type="button"
            class="brand-puzzle-overlay__logo-button"
            aria-label="FreeWillase"
            @click="handleLogoTap"
          >
            <BrandMark class="brand-puzzle-overlay__logo" />
          </button>
          <div>
            <p class="brand-puzzle-overlay__eyebrow">Puzzle Unlock</p>
            <h2>先把 FreeWillase 拼回来</h2>
          </div>
        </div>
      </div>

      <div ref="stageRef" class="brand-puzzle-overlay__stage">
        <div class="brand-puzzle-overlay__stage-grid"></div>

        <div
          class="brand-puzzle-overlay__target"
          :class="{ 'brand-puzzle-overlay__target--solved': solved }"
          :style="{
            left: `${boardLeft}px`,
            top: `${boardTop}px`,
            width: `${boardSize}px`,
            height: `${boardSize}px`,
          }"
        >
          <div class="brand-puzzle-overlay__target-mark">
            <BrandMark class="h-full w-full" />
          </div>
          <div
            v-if="justSolved"
            class="brand-puzzle-overlay__bloom"
          ></div>
        </div>

        <div v-if="justSolved" class="pointer-events-none absolute inset-0">
          <span
            v-for="particle in 10"
            :key="particle"
            class="brand-puzzle-overlay__particle"
            :style="{
              '--tx': `${((particle % 5) - 2) * 66}px`,
              '--ty': `${particle <= 5 ? -86 - particle * 6 : 48 + (particle - 5) * 10}px`,
              '--delay': `${particle * 26}ms`,
            }"
          ></span>
        </div>

        <div
          v-for="piece in sortedPieces"
          :key="piece.id"
          class="brand-puzzle-piece absolute cursor-grab select-none active:cursor-grabbing"
          :class="{
            'pointer-events-none cursor-default': piece.locked,
            'shadow-[0_14px_36px_-18px_rgba(92,199,245,0.42)]': activePieceId === piece.id,
            'shadow-[0_12px_32px_-20px_rgba(15,23,42,0.95)]': activePieceId !== piece.id,
          }"
          :style="getPieceStyle(piece)"
          @pointerdown="handlePointerDown(piece, $event)"
          @pointermove="handlePointerMove(piece, $event)"
          @pointerup="finishDrag(piece, $event)"
          @pointercancel="finishDrag(piece, $event)"
        >
          <div class="relative h-full w-full overflow-hidden" :style="{ clipPath: piece.clipPath }">
            <div class="absolute inset-0" :style="getPieceOverlayStyle(piece)"></div>
            <div class="absolute" :style="getPieceBrandStyle(piece)">
              <BrandMark class="h-full w-full" />
            </div>
            <div class="absolute inset-0 border border-white/[0.018]"></div>
            <div class="absolute -inset-[8%] opacity-[0.03] blur-md" :style="{ background: `radial-gradient(circle at 50% 50%, ${piece.glow}, transparent 70%)` }"></div>
          </div>
        </div>

        <div class="brand-puzzle-overlay__hint">
          <span>拖动碎片，靠近轮廓时会自动吸附</span>
          <span v-if="solved" class="text-apple-blue">已拼合，正在进入工作区</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.brand-puzzle-overlay {
  position: fixed;
  inset: 0;
  z-index: 110;
  overflow: hidden;
  background:
    radial-gradient(circle at 18% 16%, rgba(92, 199, 245, 0.14), transparent 24%),
    radial-gradient(circle at 82% 24%, rgba(103, 218, 205, 0.1), transparent 22%),
    linear-gradient(180deg, rgba(2, 6, 23, 0.98), rgba(3, 7, 18, 0.98));
  backdrop-filter: blur(14px);
}

.brand-puzzle-overlay__mesh,
.brand-puzzle-overlay__halo {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.brand-puzzle-overlay__mesh {
  background-image:
    linear-gradient(rgba(148, 163, 184, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.04) 1px, transparent 1px);
  background-size: 128px 128px;
  mask-image: radial-gradient(circle at center, black 24%, transparent 78%);
  opacity: 0.34;
}

.brand-puzzle-overlay__halo {
  inset: auto;
  width: 30rem;
  height: 30rem;
  border-radius: 9999px;
  filter: blur(96px);
  opacity: 0.5;
}

.brand-puzzle-overlay__halo--left {
  left: -8rem;
  top: 8%;
  background: rgba(92, 199, 245, 0.14);
}

.brand-puzzle-overlay__halo--right {
  right: -10rem;
  bottom: 4%;
  background: rgba(103, 218, 205, 0.1);
}

.brand-puzzle-overlay__content {
  position: relative;
  z-index: 1;
  display: flex;
  min-height: 100vh;
  flex-direction: column;
  padding: 2.25rem;
}

.brand-puzzle-overlay__header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1.5rem;
  padding: 0.5rem 0 1.5rem;
}

.brand-puzzle-overlay__brand {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.brand-puzzle-overlay__logo-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  padding: 0;
  background: transparent;
  cursor: pointer;
}

.brand-puzzle-overlay__logo-button:focus-visible {
  outline: none;
}

.brand-puzzle-overlay__logo {
  width: 3.5rem;
  height: 3.5rem;
  filter: drop-shadow(0 0 20px rgba(92, 199, 245, 0.18));
  transition: transform 220ms ease, filter 220ms ease;
}

.brand-puzzle-overlay__logo-button:hover .brand-puzzle-overlay__logo,
.brand-puzzle-overlay__logo-button:focus-visible .brand-puzzle-overlay__logo {
  transform: scale(1.03);
  filter: drop-shadow(0 0 26px rgba(92, 199, 245, 0.24));
}

.brand-puzzle-overlay__eyebrow {
  margin: 0;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.34em;
  text-transform: uppercase;
  color: rgba(148, 163, 184, 0.82);
}

.brand-puzzle-overlay__brand h2 {
  margin: 0.35rem 0 0;
  font-size: clamp(1.9rem, 4.8vw, 3.5rem);
  line-height: 0.96;
  letter-spacing: -0.04em;
  color: rgba(248, 250, 252, 0.98);
}

.brand-puzzle-overlay__stage {
  position: relative;
  flex: 1;
  overflow: hidden;
  border-radius: 2rem;
  border: 1px solid rgba(255, 255, 255, 0.05);
  background:
    radial-gradient(circle at top, rgba(92, 199, 245, 0.08), transparent 42%),
    linear-gradient(180deg, rgba(6, 12, 24, 0.96), rgba(2, 7, 18, 0.94));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.03),
    0 36px 100px -60px rgba(8, 47, 73, 0.56);
}

.brand-puzzle-overlay__stage-grid {
  position: absolute;
  inset: 0;
  opacity: 0.64;
  background:
    linear-gradient(90deg, transparent 0, rgba(255, 255, 255, 0.018) 50%, transparent 100%),
    linear-gradient(180deg, transparent 0, rgba(255, 255, 255, 0.012) 50%, transparent 100%);
}

.brand-puzzle-overlay__target {
  position: absolute;
  border-radius: 1.75rem;
  border: 1px dashed rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.02);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.03);
}

.brand-puzzle-overlay__target--solved {
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.05),
    0 0 0 1px rgba(92, 199, 245, 0.1),
    0 0 42px rgba(92, 199, 245, 0.08);
}

.brand-puzzle-overlay__target-mark {
  position: absolute;
  inset: 0;
  opacity: 0.16;
}

.brand-puzzle-overlay__bloom {
  position: absolute;
  inset: -10%;
  border-radius: 2rem;
  background: radial-gradient(circle, rgba(103, 218, 205, 0.18), rgba(92, 199, 245, 0.1) 38%, transparent 72%);
  animation: brand-puzzle-bloom 1.2s ease-out forwards;
}

.brand-puzzle-piece {
  touch-action: none;
}

.brand-puzzle-overlay__hint {
  pointer-events: none;
  position: absolute;
  inset-inline: 1.25rem;
  bottom: 1rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  font-size: 0.72rem;
  color: rgba(148, 163, 184, 0.9);
}

.brand-puzzle-overlay__particle {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 0.55rem;
  height: 0.55rem;
  border-radius: 9999px;
  background: radial-gradient(circle, rgba(103, 218, 205, 0.88), rgba(92, 199, 245, 0.28) 58%, transparent 72%);
  box-shadow: 0 0 14px rgba(92, 199, 245, 0.32);
  animation: brand-puzzle-particle 900ms ease-out forwards;
  animation-delay: var(--delay);
}

@keyframes brand-puzzle-bloom {
  0% {
    opacity: 0;
    transform: scale(0.82);
  }

  20% {
    opacity: 1;
    transform: scale(1);
  }

  100% {
    opacity: 0;
    transform: scale(1.16);
  }
}

@keyframes brand-puzzle-particle {
  0% {
    opacity: 0;
    transform: translate(-50%, -50%) scale(0.3);
  }

  20% {
    opacity: 1;
  }

  100% {
    opacity: 0;
    transform: translate(calc(-50% + var(--tx)), calc(-50% + var(--ty))) scale(1.15);
  }
}

@media (max-width: 900px) {
  .brand-puzzle-overlay__content {
    padding: 1.25rem;
  }

  .brand-puzzle-overlay__header {
    flex-direction: column;
    align-items: flex-start;
  }

  .brand-puzzle-overlay__stage {
    min-height: 72vh;
  }

  .brand-puzzle-overlay__hint {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
