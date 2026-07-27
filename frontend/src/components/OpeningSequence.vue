<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import BrandMark from '@/components/BrandMark.vue'

const emit = defineEmits<{
  finished: []
}>()

const visible = ref(true)
const isLeaving = ref(false)
let hideTimer: number | null = null

function dismissSequence() {
  isLeaving.value = true
  hideTimer = window.setTimeout(() => {
    visible.value = false
  }, 720)
}

onMounted(() => {
  const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  hideTimer = window.setTimeout(dismissSequence, prefersReducedMotion ? 180 : 1900)
})

onBeforeUnmount(() => {
  if (hideTimer) {
    window.clearTimeout(hideTimer)
  }
})
</script>

<template>
  <transition name="opening-sequence" @after-leave="emit('finished')">
    <div v-if="visible" class="opening-sequence" :class="{ 'opening-sequence--leaving': isLeaving }" aria-hidden="true">
      <div class="opening-sequence__curtain opening-sequence__curtain--top"></div>
      <div class="opening-sequence__curtain opening-sequence__curtain--bottom"></div>
      <div class="opening-sequence__mesh"></div>
      <div class="opening-sequence__noise"></div>
      <div class="opening-sequence__halo opening-sequence__halo--left"></div>
      <div class="opening-sequence__halo opening-sequence__halo--right"></div>

      <div class="opening-sequence__content">
        <div class="opening-sequence__brand">
          <BrandMark class="opening-sequence__logo" />
          <div class="opening-sequence__wordmark">
            <p class="opening-sequence__eyebrow">Research Interface</p>
            <h1>FreeWillase</h1>
          </div>
        </div>

        <div class="opening-sequence__signal">
          <span></span>
          <span></span>
          <span></span>
        </div>
      </div>
    </div>
  </transition>
</template>

<style scoped>
.opening-sequence {
  position: fixed;
  inset: 0;
  z-index: 120;
  overflow: hidden;
  background:
    radial-gradient(circle at 22% 18%, rgba(56, 189, 248, 0.22), transparent 24%),
    radial-gradient(circle at 78% 24%, rgba(94, 234, 212, 0.12), transparent 22%),
    linear-gradient(180deg, rgba(2, 6, 23, 0.98), rgba(3, 7, 18, 0.98));
}

.opening-sequence__curtain,
.opening-sequence__mesh,
.opening-sequence__noise,
.opening-sequence__halo {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.opening-sequence__curtain {
  background:
    linear-gradient(180deg, rgba(2, 6, 23, 0.98), rgba(6, 11, 23, 0.9)),
    linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.04), transparent);
  transform-origin: center;
  transition: transform 820ms cubic-bezier(0.22, 1, 0.36, 1), opacity 820ms ease;
}

.opening-sequence__curtain--top {
  clip-path: polygon(0 0, 100% 0, 100% 52%, 0 68%);
}

.opening-sequence__curtain--bottom {
  clip-path: polygon(0 38%, 100% 22%, 100% 100%, 0 100%);
}

.opening-sequence__mesh {
  background-image:
    linear-gradient(rgba(148, 163, 184, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.05) 1px, transparent 1px);
  background-size: 140px 140px;
  mask-image: radial-gradient(circle at center, black 26%, transparent 74%);
  opacity: 0.45;
  animation: openingMeshDrift 8s linear infinite;
}

.opening-sequence__noise {
  background:
    repeating-linear-gradient(
      180deg,
      rgba(255, 255, 255, 0.02) 0,
      rgba(255, 255, 255, 0.02) 1px,
      transparent 1px,
      transparent 5px
    );
  mix-blend-mode: screen;
  opacity: 0.18;
}

.opening-sequence__halo {
  inset: auto;
  width: 28rem;
  height: 28rem;
  border-radius: 9999px;
  filter: blur(88px);
  opacity: 0.58;
  animation: openingHaloPulse 2.4s ease-in-out infinite alternate;
}

.opening-sequence__halo--left {
  left: -8rem;
  top: 18%;
  background: rgba(56, 189, 248, 0.28);
}

.opening-sequence__halo--right {
  right: -10rem;
  bottom: 8%;
  background: rgba(94, 234, 212, 0.18);
  animation-delay: 0.3s;
}

.opening-sequence__content {
  position: relative;
  z-index: 1;
  display: grid;
  place-items: center;
  gap: 1.75rem;
  min-height: 100vh;
  padding: 2rem;
}

.opening-sequence__brand {
  display: flex;
  align-items: center;
  gap: 1.5rem;
  opacity: 0;
  transform: translateY(18px) scale(0.98);
  animation: openingContentReveal 0.9s cubic-bezier(0.22, 1, 0.36, 1) 0.14s forwards;
}

.opening-sequence__logo {
  width: 5rem;
  height: 5rem;
  filter: drop-shadow(0 0 30px rgba(56, 189, 248, 0.24));
}

.opening-sequence__wordmark h1 {
  margin: 0.35rem 0 0;
  font-size: clamp(2rem, 5vw, 4.5rem);
  line-height: 0.95;
  letter-spacing: -0.05em;
  color: rgba(248, 250, 252, 0.98);
}

.opening-sequence__eyebrow {
  margin: 0;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.46em;
  text-transform: uppercase;
  color: rgba(148, 163, 184, 0.84);
}

.opening-sequence__signal {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  opacity: 0;
  animation: openingContentReveal 0.9s cubic-bezier(0.22, 1, 0.36, 1) 0.3s forwards;
}

.opening-sequence__signal span {
  display: block;
  width: 3.25rem;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(103, 232, 249, 0.86), transparent);
  animation: openingSignalSweep 1.8s ease-in-out infinite;
}

.opening-sequence__signal span:nth-child(2) {
  animation-delay: 0.15s;
}

.opening-sequence__signal span:nth-child(3) {
  animation-delay: 0.3s;
}

.opening-sequence--leaving .opening-sequence__curtain--top {
  transform: translateY(-110%);
  opacity: 0;
}

.opening-sequence--leaving .opening-sequence__curtain--bottom {
  transform: translateY(110%);
  opacity: 0;
}

.opening-sequence--leaving .opening-sequence__brand,
.opening-sequence--leaving .opening-sequence__signal {
  transition: opacity 480ms ease, transform 640ms cubic-bezier(0.22, 1, 0.36, 1);
  opacity: 0;
  transform: translateY(-16px) scale(1.02);
}

.opening-sequence-enter-active,
.opening-sequence-leave-active {
  transition: opacity 0.5s ease;
}

.opening-sequence-enter-from,
.opening-sequence-leave-to {
  opacity: 0;
}

@keyframes openingContentReveal {
  from {
    opacity: 0;
    transform: translateY(18px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes openingSignalSweep {
  0%,
  100% {
    opacity: 0.34;
    transform: scaleX(0.82);
  }
  50% {
    opacity: 1;
    transform: scaleX(1.08);
  }
}

@keyframes openingHaloPulse {
  from {
    transform: scale(0.94);
  }
  to {
    transform: scale(1.06);
  }
}

@keyframes openingMeshDrift {
  from {
    transform: translate3d(0, 0, 0);
  }
  to {
    transform: translate3d(0, 26px, 0);
  }
}

@media (max-width: 768px) {
  .opening-sequence__brand {
    flex-direction: column;
    text-align: center;
  }

  .opening-sequence__eyebrow {
    letter-spacing: 0.32em;
  }

  .opening-sequence__signal span {
    width: 2rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .opening-sequence__brand,
  .opening-sequence__signal,
  .opening-sequence__mesh,
  .opening-sequence__halo,
  .opening-sequence__signal span {
    animation: none;
  }

  .opening-sequence__brand,
  .opening-sequence__signal {
    opacity: 1;
    transform: none;
  }

  .opening-sequence__curtain,
  .opening-sequence--leaving .opening-sequence__brand,
  .opening-sequence--leaving .opening-sequence__signal {
    transition-duration: 180ms;
  }
}
</style>
