<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import BrandMark from '@/components/BrandMark.vue'
import { useBrandPuzzleGate } from '@/composables/useBrandPuzzleGate'
import { BRAND_PUZZLE_BOARD_UNITS, brandPuzzleTemplates } from '@/utils/brandPuzzle'

const { shatterBrandPuzzle } = useBrandPuzzleGate()
const isShattering = ref(false)
let shatterTimer: ReturnType<typeof setTimeout> | null = null

const pieceStyles = computed(() =>
  brandPuzzleTemplates.map((piece, index) => ({
    id: piece.id,
    shellStyle: {
      left: `${(piece.targetX / BRAND_PUZZLE_BOARD_UNITS) * 100}%`,
      top: `${(piece.targetY / BRAND_PUZZLE_BOARD_UNITS) * 100}%`,
      width: `${(piece.width / BRAND_PUZZLE_BOARD_UNITS) * 100}%`,
      height: `${(piece.height / BRAND_PUZZLE_BOARD_UNITS) * 100}%`,
      clipPath: piece.clipPath,
      '--piece-tint': piece.tint,
      '--piece-glow': piece.glow,
      '--shatter-x': `${[-42, 0, 46, -56, 18, 58, -34, 8, 48][index]}px`,
      '--shatter-y': `${[-54, -62, -40, 0, 18, -2, 52, 60, 46][index]}px`,
      '--shatter-rotate': `${[-16, -8, 18, -20, 10, 16, -12, 8, 22][index]}deg`,
      '--shatter-delay': `${index * 14}ms`,
    },
    markStyle: {
      width: `${BRAND_PUZZLE_BOARD_UNITS / piece.width * 100}%`,
      height: `${BRAND_PUZZLE_BOARD_UNITS / piece.height * 100}%`,
      left: `${-(piece.targetX / piece.width) * 100}%`,
      top: `${-(piece.targetY / piece.height) * 100}%`,
    },
  })),
)

function handleShatter() {
  if (isShattering.value) return

  isShattering.value = true
  shatterTimer = setTimeout(() => {
    shatterBrandPuzzle()
    isShattering.value = false
    shatterTimer = null
  }, 340)
}

onBeforeUnmount(() => {
  if (shatterTimer) {
    clearTimeout(shatterTimer)
    shatterTimer = null
  }
})
</script>

<template>
  <button
    type="button"
    class="brand-puzzle-panel group"
    :class="{ 'brand-puzzle-panel--shattering': isShattering }"
    @click="handleShatter"
  >
    <div class="brand-puzzle-panel__frame">
      <div class="brand-puzzle-panel__assembled">
        <div
          v-for="piece in pieceStyles"
          :key="piece.id"
          class="brand-puzzle-panel__piece"
          :style="piece.shellStyle"
        >
          <div class="brand-puzzle-panel__piece-shell">
            <div class="brand-puzzle-panel__piece-tint"></div>
            <div class="brand-puzzle-panel__piece-mark" :style="piece.markStyle">
              <BrandMark class="h-full w-full" />
            </div>
            <div class="brand-puzzle-panel__piece-glow"></div>
            <div class="brand-puzzle-panel__piece-edge"></div>
          </div>
        </div>
      </div>
      <div class="brand-puzzle-panel__overlay"></div>
    </div>
  </button>
</template>

<style scoped>
.brand-puzzle-panel {
  display: block;
  width: min(100%, 34rem);
  border: 0;
  padding: 0;
  background: transparent;
  text-align: left;
}

.brand-puzzle-panel__frame {
  position: relative;
  overflow: hidden;
  border-radius: 1.9rem;
  border: 1px solid rgba(255, 255, 255, 0.05);
  background:
    radial-gradient(circle at top, rgba(56, 189, 248, 0.12), transparent 42%),
    linear-gradient(180deg, rgba(6, 12, 24, 0.96), rgba(2, 7, 18, 0.94));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.03),
    0 26px 80px -52px rgba(8, 47, 73, 0.75);
  transition:
    transform 220ms ease,
    box-shadow 220ms ease,
    border-color 220ms ease,
    filter 320ms ease,
    opacity 320ms ease;
}

.brand-puzzle-panel:hover .brand-puzzle-panel__frame,
.brand-puzzle-panel:focus-visible .brand-puzzle-panel__frame {
  transform: translateY(-2px);
  border-color: rgba(56, 189, 248, 0.18);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.04),
    0 30px 80px -48px rgba(8, 47, 73, 0.9),
    0 0 0 1px rgba(56, 189, 248, 0.12);
}

.brand-puzzle-panel:focus-visible {
  outline: none;
}

.brand-puzzle-panel__assembled {
  position: relative;
  width: min(100%, 20rem);
  aspect-ratio: 1;
  margin: 1.4rem auto;
  filter: drop-shadow(0 18px 30px rgba(2, 6, 23, 0.18));
}

.brand-puzzle-panel__piece {
  position: absolute;
  transition:
    transform 360ms cubic-bezier(0.22, 1, 0.36, 1),
    opacity 320ms ease,
    filter 360ms ease;
  transition-delay: var(--shatter-delay);
}

.brand-puzzle-panel__piece-shell {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  clip-path: inherit;
  border-radius: 0;
  background: transparent;
}

.brand-puzzle-panel__piece-tint {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.015), transparent 34%, rgba(2, 8, 20, 0.02));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.03);
}

.brand-puzzle-panel__piece-mark {
  position: absolute;
  filter: none;
}

.brand-puzzle-panel__piece-glow {
  position: absolute;
  inset: -8%;
  border-radius: 1rem;
  opacity: 0.03;
  filter: blur(8px);
  background: radial-gradient(circle at 50% 50%, var(--piece-glow), transparent 70%);
}

.brand-puzzle-panel__piece-edge {
  position: absolute;
  inset: 0;
  border: 1px solid rgba(255, 255, 255, 0.018);
}

.brand-puzzle-panel__overlay {
  pointer-events: none;
  position: absolute;
  inset: 0;
  opacity: 0;
  background:
    radial-gradient(circle at center, rgba(94, 234, 212, 0.18), transparent 44%),
    linear-gradient(90deg, transparent 0, rgba(255, 255, 255, 0.02) 50%, transparent 100%),
    linear-gradient(180deg, transparent 0, rgba(255, 255, 255, 0.015) 50%, transparent 100%);
  transition: opacity 260ms ease;
}

.brand-puzzle-panel--shattering {
  pointer-events: none;
}

.brand-puzzle-panel--shattering .brand-puzzle-panel__frame {
  transform: scale(1.018);
  filter: saturate(1.08);
  opacity: 0.88;
}

.brand-puzzle-panel--shattering .brand-puzzle-panel__piece {
  opacity: 0;
  filter: blur(7px);
  transform: translate(var(--shatter-x), var(--shatter-y)) rotate(var(--shatter-rotate)) scale(0.92);
}

.brand-puzzle-panel--shattering .brand-puzzle-panel__overlay {
  opacity: 1;
}
</style>
