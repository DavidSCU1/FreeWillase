<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import {
  LayoutDashboard,
  Database,
  FlaskConical,
  BookOpenText,
  Microscope,
  ChevronRight,
  PanelLeftClose,
  PanelLeftOpen,
  LogOut
} from 'lucide-vue-next'
import { useAuth } from '@/utils/auth'
import BrandMark from '@/components/BrandMark.vue'
import OpeningSequence from '@/components/OpeningSequence.vue'
import BrandPuzzleOverlay from '@/components/BrandPuzzleOverlay.vue'
import { useBrandPuzzleGate } from '@/composables/useBrandPuzzleGate'

const route = useRoute()
const { logout } = useAuth()
const SIDEBAR_STORAGE_KEY = 'appSidebarCollapsed'
const isSidebarCollapsed = ref(false)
const { isPuzzleGateVisible, markOpeningFinished, unlockBrandPuzzle } = useBrandPuzzleGate()

const navItems = [
  { label: '工作台', to: '/dashboard', icon: LayoutDashboard },
  { label: '酶库中心', to: '/library', icon: FlaskConical },
  { label: 'NCBI 导入', to: '/importer', icon: Database },
  { label: '文献匹配', to: '/matcher', icon: BookOpenText },
  { label: '预测接口', to: '/prediction', icon: Microscope },
]

const isRouteActive = (path: string) => route.path === path || route.path.startsWith(`${path}/`)

const currentItem = computed(() => navItems.find((item) => isRouteActive(item.to)) || navItems[0])
const showPuzzleGate = computed(() => route.path !== '/login' && isPuzzleGateVisible.value)

function toggleSidebar() {
  isSidebarCollapsed.value = !isSidebarCollapsed.value
  localStorage.setItem(SIDEBAR_STORAGE_KEY, isSidebarCollapsed.value ? '1' : '0')
}

onMounted(() => {
  isSidebarCollapsed.value = localStorage.getItem(SIDEBAR_STORAGE_KEY) === '1'
})
</script>

<template>
  <div class="relative min-h-screen bg-apple-background text-apple-text flex overflow-hidden">
    <OpeningSequence @finished="markOpeningFinished" />
    <transition name="brand-puzzle-gate" appear>
      <BrandPuzzleOverlay v-if="showPuzzleGate" @solved="unlockBrandPuzzle" />
    </transition>
    <div class="pointer-events-none absolute inset-0 resn-shell-grid"></div>
    <div class="pointer-events-none absolute inset-x-0 top-0 h-96 bg-[radial-gradient(circle_at_top,rgba(92,199,245,0.1),transparent_48%)]"></div>
    <div class="pointer-events-none absolute left-[-10rem] top-20 h-[22rem] w-[22rem] rounded-full bg-apple-blue/[0.08] blur-3xl"></div>
    <div class="pointer-events-none absolute right-[-12rem] top-24 h-96 w-96 rounded-full bg-apple-green/[0.09] blur-3xl"></div>
    <!-- Sidebar -->
    <aside
      class="border-r border-apple-border/50 flex flex-col apple-glass fixed h-screen z-10 transition-all duration-300 bg-[rgba(7,11,23,0.42)]"
      :class="[isSidebarCollapsed ? 'w-24' : 'w-64', showPuzzleGate ? 'shell-under-gate' : 'shell-ready']"
    >
      <div class="p-6 flex items-center" :class="isSidebarCollapsed ? 'justify-center' : 'gap-3'">
        <BrandMark class="w-10 h-10 shrink-0" />
        <div v-if="!isSidebarCollapsed">
          <h1 class="font-bold text-lg tracking-tight">FreeWillase</h1>
          <p class="text-[10px] text-apple-secondary-text uppercase tracking-[0.28em] font-medium">Enzyme Platform</p>
        </div>
      </div>

      <div class="px-4 py-2 space-y-3">
        <button
          type="button"
          class="apple-soft-strip flex w-full items-center rounded-apple px-3 py-2 text-xs font-bold text-apple-secondary-text transition-colors hover:text-apple-text"
          :class="isSidebarCollapsed ? 'justify-center' : 'justify-between'"
          :title="isSidebarCollapsed ? '展开侧栏' : '收起侧栏'"
          @click="toggleSidebar"
        >
          <span v-if="!isSidebarCollapsed">收起侧栏</span>
          <PanelLeftOpen v-if="isSidebarCollapsed" :size="16" />
          <PanelLeftClose v-else :size="16" />
        </button>
      </div>

      <nav class="flex-1 px-3 py-4 space-y-1">
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="flex items-center px-3 py-2.5 rounded-apple transition-all group"
          :class="[
            isSidebarCollapsed ? 'justify-center' : 'justify-between',
            isRouteActive(item.to)
              ? 'bg-[linear-gradient(135deg,rgba(8,12,23,0.96),rgba(18,25,40,0.92)_62%,rgba(92,199,245,0.68))] text-white shadow-[0_16px_36px_-24px_rgba(92,199,245,0.18)]'
              : 'text-apple-secondary-text hover:bg-apple-blue/[0.04] hover:text-apple-text'
          ]"
          :title="isSidebarCollapsed ? item.label : undefined"
        >
          <div class="flex items-center" :class="isSidebarCollapsed ? 'justify-center' : 'gap-3'">
            <component :is="item.icon" :size="18" />
            <span v-if="!isSidebarCollapsed" class="text-sm font-medium">{{ item.label }}</span>
          </div>
          <ChevronRight v-if="!isSidebarCollapsed && isRouteActive(item.to)" :size="14" class="opacity-50" />
        </RouterLink>
      </nav>
    </aside>

    <!-- Main Content -->
    <main
      class="relative z-10 flex-1 min-h-screen flex flex-col transition-all duration-300"
      :class="[isSidebarCollapsed ? 'ml-24' : 'ml-64', showPuzzleGate ? 'shell-under-gate' : 'shell-ready']"
    >
      <!-- Header -->
      <header class="h-16 border-b border-apple-border/50 apple-glass sticky top-0 z-20 px-8 flex items-center justify-between bg-[rgba(6,10,20,0.34)]">
        <div class="flex items-center gap-3 text-sm">
          <span class="apple-soft-strip rounded-full px-2.5 py-1 text-[10px] font-bold uppercase tracking-[0.24em] text-apple-secondary-text">
            Workspace
          </span>
          <span class="text-apple-secondary-text">FreeWillase</span>
          <span class="text-apple-border">/</span>
          <span class="font-medium">{{ currentItem.label }}</span>
        </div>
        
        <div class="flex items-center gap-4">
          <div class="flex items-center gap-2">
            <div class="w-8 h-8 rounded-full border border-apple-border/70 bg-[linear-gradient(135deg,rgba(9,13,24,0.96),rgba(92,199,245,0.72))] flex items-center justify-center text-[10px] font-bold text-white shadow-[0_12px_24px_-18px_rgba(92,199,245,0.18)]">
              AD
            </div>
            <span class="text-xs font-bold text-apple-text">Admin</span>
          </div>
          <div class="h-4 w-px bg-apple-border"></div>
          <button 
            @click="logout"
            class="p-2 rounded-full hover:bg-red-500/10 text-apple-secondary-text hover:text-red-500 transition-all"
            title="退出登录"
          >
            <LogOut :size="16" />
          </button>
        </div>
      </header>

      <!-- Content Area -->
      <div class="p-8 max-w-[1440px] mx-auto w-full flex-1">
        <RouterView v-slot="{ Component, route: currentRoute }">
          <transition name="route-reveal" mode="out-in">
            <div :key="currentRoute.fullPath" class="route-stage">
              <component :is="Component" />
            </div>
          </transition>
        </RouterView>
      </div>
    </main>
  </div>
</template>

<style scoped>
.resn-shell-grid {
  z-index: 0;
  background-image:
    linear-gradient(rgba(148, 163, 184, 0.032) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.032) 1px, transparent 1px);
  background-size: 96px 96px;
  mask-image: radial-gradient(circle at center, black 18%, transparent 88%);
  opacity: 0.16;
}

.route-stage {
  position: relative;
  min-height: calc(100vh - 8rem);
}

.route-stage::before {
  content: '';
  position: absolute;
  inset: -1.5rem -1rem auto;
  height: 14rem;
  pointer-events: none;
  background: linear-gradient(180deg, rgba(92, 199, 245, 0.04), transparent 72%);
  opacity: 0.32;
}

.shell-ready,
.shell-under-gate {
  transition:
    filter 0.72s cubic-bezier(0.22, 1, 0.36, 1),
    transform 0.72s cubic-bezier(0.22, 1, 0.36, 1),
    opacity 0.72s cubic-bezier(0.22, 1, 0.36, 1);
}

.shell-under-gate {
  filter: blur(10px) saturate(0.84);
  transform: scale(0.992);
  opacity: 0.55;
}

.brand-puzzle-gate-enter-active,
.brand-puzzle-gate-leave-active {
  transition:
    opacity 0.68s cubic-bezier(0.22, 1, 0.36, 1),
    transform 0.68s cubic-bezier(0.22, 1, 0.36, 1),
    filter 0.68s cubic-bezier(0.22, 1, 0.36, 1);
}

.brand-puzzle-gate-enter-from,
.brand-puzzle-gate-leave-to {
  opacity: 0;
  transform: scale(1.02);
  filter: blur(14px);
}

.brand-puzzle-gate-enter-active :deep(.brand-puzzle-overlay__content),
.brand-puzzle-gate-leave-active :deep(.brand-puzzle-overlay__content) {
  transition:
    opacity 0.72s cubic-bezier(0.22, 1, 0.36, 1),
    transform 0.72s cubic-bezier(0.22, 1, 0.36, 1),
    filter 0.72s cubic-bezier(0.22, 1, 0.36, 1);
}

.brand-puzzle-gate-enter-from :deep(.brand-puzzle-overlay__content) {
  opacity: 0;
  transform: translateY(26px) scale(0.985);
  filter: blur(14px);
}

.brand-puzzle-gate-leave-to :deep(.brand-puzzle-overlay__content) {
  opacity: 0;
  transform: translateY(-16px) scale(1.01);
  filter: blur(10px);
}

.route-reveal-enter-active,
.route-reveal-leave-active {
  transition:
    opacity 0.62s cubic-bezier(0.22, 1, 0.36, 1),
    transform 0.62s cubic-bezier(0.22, 1, 0.36, 1),
    filter 0.62s cubic-bezier(0.22, 1, 0.36, 1);
}

.route-reveal-enter-from {
  opacity: 0;
  transform: translateY(24px) scale(0.985);
  filter: blur(16px);
}

.route-reveal-leave-to {
  opacity: 0;
  transform: translateY(-14px) scale(1.01);
  filter: blur(12px);
}

@media (prefers-reduced-motion: reduce) {
  .shell-ready,
  .shell-under-gate,
  .brand-puzzle-gate-enter-active,
  .brand-puzzle-gate-leave-active,
  .brand-puzzle-gate-enter-active :deep(.brand-puzzle-overlay__content),
  .brand-puzzle-gate-leave-active :deep(.brand-puzzle-overlay__content),
  .route-reveal-enter-active,
  .route-reveal-leave-active {
    transition-duration: 180ms;
  }

  .shell-under-gate,
  .brand-puzzle-gate-enter-from,
  .brand-puzzle-gate-leave-to,
  .brand-puzzle-gate-enter-from :deep(.brand-puzzle-overlay__content),
  .brand-puzzle-gate-leave-to :deep(.brand-puzzle-overlay__content),
  .route-reveal-enter-from,
  .route-reveal-leave-to {
    transform: none;
    filter: none;
  }
}
</style>
