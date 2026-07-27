import { computed, readonly, ref } from 'vue'

const openingFinished = ref(false)
const puzzleUnlocked = ref(false)

const isPuzzleGateVisible = computed(() => openingFinished.value && !puzzleUnlocked.value)

function markOpeningFinished() {
  openingFinished.value = true
}

function unlockBrandPuzzle() {
  puzzleUnlocked.value = true
}

function shatterBrandPuzzle() {
  if (!openingFinished.value) return
  puzzleUnlocked.value = false
}

export function useBrandPuzzleGate() {
  return {
    openingFinished: readonly(openingFinished),
    puzzleUnlocked: readonly(puzzleUnlocked),
    isPuzzleGateVisible: readonly(isPuzzleGateVisible),
    markOpeningFinished,
    unlockBrandPuzzle,
    shatterBrandPuzzle,
  }
}
