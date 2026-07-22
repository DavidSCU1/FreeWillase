<script setup lang="ts">
import { computed } from 'vue'
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
  LogOut
} from 'lucide-vue-next'
import { useAuth } from '@/utils/auth'

const route = useRoute()
const { logout } = useAuth()

const navItems = [
  { label: '工作台', to: '/dashboard', icon: LayoutDashboard },
  { label: '酶库中心', to: '/library', icon: FlaskConical },
  { label: 'NCBI 导入', to: '/importer', icon: Database },
  { label: '文献匹配', to: '/matcher', icon: BookOpenText },
  { label: '预测接口', to: '/prediction', icon: Microscope },
]

const currentItem = computed(() => navItems.find((item) => item.to === route.path) || navItems[0])
</script>

<template>
  <div class="min-h-screen bg-apple-background text-apple-text flex">
    <!-- Sidebar -->
    <aside class="w-64 border-r border-apple-border flex flex-col apple-glass fixed h-screen z-10">
      <div class="p-6 flex items-center gap-3">
        <div class="w-10 h-10 bg-apple-blue rounded-apple flex items-center justify-center text-white">
          <FlaskConical :size="24" />
        </div>
        <div>
          <h1 class="font-bold text-lg tracking-tight">FreeWillase</h1>
          <p class="text-[10px] text-apple-secondary-text uppercase tracking-widest font-medium">Enzyme Platform</p>
        </div>
      </div>

      <div class="px-4 py-2">
        <div class="relative group">
          <Search class="absolute left-3 top-1/2 -translate-y-1/2 text-apple-secondary-text group-focus-within:text-apple-blue transition-colors" :size="14" />
          <input 
            type="text" 
            placeholder="快速搜索..." 
            class="w-full bg-black/5 dark:bg-white/5 border-none rounded-apple py-2 pl-9 pr-4 text-xs focus:ring-1 focus:ring-apple-blue outline-none transition-all"
          />
        </div>
      </div>

      <nav class="flex-1 px-3 py-4 space-y-1">
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="flex items-center justify-between px-3 py-2.5 rounded-apple transition-all group"
          :class="route.path === item.to 
            ? 'bg-apple-blue text-white shadow-lg shadow-apple-blue/20' 
            : 'text-apple-secondary-text hover:bg-black/5 dark:hover:bg-white/5 hover:text-apple-text'"
        >
          <div class="flex items-center gap-3">
            <component :is="item.icon" :size="18" />
            <span class="text-sm font-medium">{{ item.label }}</span>
          </div>
          <ChevronRight v-if="route.path === item.to" :size="14" class="opacity-50" />
        </RouterLink>
      </nav>

      <div class="p-4 border-t border-apple-border">
        <button class="flex items-center gap-3 w-full px-3 py-2 text-apple-secondary-text hover:text-apple-text transition-colors text-sm font-medium">
          <Settings :size="18" />
          <span>系统设置</span>
        </button>
      </div>
    </aside>

    <!-- Main Content -->
    <main class="flex-1 ml-64 min-h-screen flex flex-col">
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
