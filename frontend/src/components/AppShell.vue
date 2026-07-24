<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import {
  LayoutDashboard,
  Database,
  FlaskConical,
  BookOpenText,
  Microscope,
  Settings,
  Search,
  ChevronRight,
  PanelLeftClose,
  PanelLeftOpen,
  LogOut
} from 'lucide-vue-next'
import { useAuth } from '@/utils/auth'

const route = useRoute()
const { logout } = useAuth()
const SIDEBAR_STORAGE_KEY = 'appSidebarCollapsed'
const isSidebarCollapsed = ref(false)

const navItems = [
  { label: '工作台', to: '/dashboard', icon: LayoutDashboard },
  { label: '酶库中心', to: '/library', icon: FlaskConical },
  { label: 'NCBI 导入', to: '/importer', icon: Database },
  { label: '文献匹配', to: '/matcher', icon: BookOpenText },
  { label: '预测接口', to: '/prediction', icon: Microscope },
]

const isRouteActive = (path: string) => route.path === path || route.path.startsWith(`${path}/`)

const currentItem = computed(() => navItems.find((item) => isRouteActive(item.to)) || navItems[0])

function toggleSidebar() {
  isSidebarCollapsed.value = !isSidebarCollapsed.value
  localStorage.setItem(SIDEBAR_STORAGE_KEY, isSidebarCollapsed.value ? '1' : '0')
}

onMounted(() => {
  isSidebarCollapsed.value = localStorage.getItem(SIDEBAR_STORAGE_KEY) === '1'
})
</script>

<template>
  <div class="min-h-screen bg-apple-background text-apple-text flex">
    <!-- Sidebar -->
    <aside
      class="border-r border-apple-border flex flex-col apple-glass fixed h-screen z-10 transition-all duration-300"
      :class="isSidebarCollapsed ? 'w-24' : 'w-64'"
    >
      <div class="p-6 flex items-center" :class="isSidebarCollapsed ? 'justify-center' : 'gap-3'">
        <div class="w-10 h-10 bg-apple-blue rounded-apple flex items-center justify-center text-white">
          <FlaskConical :size="24" />
        </div>
        <div v-if="!isSidebarCollapsed">
          <h1 class="font-bold text-lg tracking-tight">FreeWillase</h1>
          <p class="text-[10px] text-apple-secondary-text uppercase tracking-widest font-medium">Enzyme Platform</p>
        </div>
      </div>

      <div class="px-4 py-2">
        <button
          type="button"
          class="mb-3 flex w-full items-center rounded-apple border border-apple-border bg-white/40 px-3 py-2 text-xs font-bold text-apple-secondary-text transition-colors hover:bg-black/5 hover:text-apple-text dark:bg-white/5 dark:hover:bg-white/10"
          :class="isSidebarCollapsed ? 'justify-center' : 'justify-between'"
          :title="isSidebarCollapsed ? '展开侧栏' : '收起侧栏'"
          @click="toggleSidebar"
        >
          <span v-if="!isSidebarCollapsed">收起侧栏</span>
          <PanelLeftOpen v-if="isSidebarCollapsed" :size="16" />
          <PanelLeftClose v-else :size="16" />
        </button>

        <div class="relative group">
          <Search
            class="absolute top-1/2 -translate-y-1/2 text-apple-secondary-text group-focus-within:text-apple-blue transition-colors"
            :class="isSidebarCollapsed ? 'left-1/2 -translate-x-1/2' : 'left-3'"
            :size="14"
          />
          <input 
            type="text" 
            placeholder="快速搜索..." 
            class="w-full bg-black/5 dark:bg-white/5 border-none rounded-apple py-2 text-xs focus:ring-1 focus:ring-apple-blue outline-none transition-all"
            :class="isSidebarCollapsed ? 'px-0 text-transparent placeholder:text-transparent cursor-pointer' : 'pl-9 pr-4'"
            :title="isSidebarCollapsed ? '快速搜索' : undefined"
          />
        </div>
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
              ? 'bg-apple-blue text-white shadow-lg shadow-apple-blue/20'
              : 'text-apple-secondary-text hover:bg-black/5 dark:hover:bg-white/5 hover:text-apple-text'
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

      <div class="p-4 border-t border-apple-border">
        <button
          class="flex w-full px-3 py-2 text-apple-secondary-text hover:text-apple-text transition-colors text-sm font-medium"
          :class="isSidebarCollapsed ? 'justify-center' : 'items-center gap-3'"
          :title="isSidebarCollapsed ? '系统设置' : undefined"
        >
          <Settings :size="18" />
          <span v-if="!isSidebarCollapsed">系统设置</span>
        </button>
      </div>
    </aside>

    <!-- Main Content -->
    <main
      class="flex-1 min-h-screen flex flex-col transition-all duration-300"
      :class="isSidebarCollapsed ? 'ml-24' : 'ml-64'"
    >
      <!-- Header -->
      <header class="h-16 border-b border-apple-border apple-glass sticky top-0 z-20 px-8 flex items-center justify-between">
        <div class="flex items-center gap-2 text-sm">
          <span class="text-apple-secondary-text">FreeWillase</span>
          <span class="text-apple-border">/</span>
          <span class="font-medium">{{ currentItem.label }}</span>
        </div>
        
        <div class="flex items-center gap-4">
          <div class="flex items-center gap-2">
            <div class="w-8 h-8 rounded-full border-2 border-white bg-apple-blue flex items-center justify-center text-[10px] font-bold text-white shadow-sm">
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
        <RouterView v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </RouterView>
      </div>
    </main>
  </div>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: all 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(4px);
}
</style>
