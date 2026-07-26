<script setup lang="ts">
import { FlaskConical, Sparkles, ArrowRight } from 'lucide-vue-next'
import { RouterLink } from 'vue-router'
</script>

<template>
  <div class="space-y-8">
    <div class="flex items-start justify-between gap-6">
      <div class="space-y-2">
        <h1 class="text-3xl font-bold tracking-tight text-apple-text">酶库中心</h1>
        <p class="text-sm text-apple-secondary-text">选择数据仓后进入条目列表，查看结构、注释与文献证据。</p>
      </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-2 gap-8">
      <RouterLink
        to="/library/imported"
        class="rounded-apple-xl bg-white/50 dark:bg-white/5 p-8 hover:bg-white/70 dark:hover:bg-white/10 transition-colors shadow-apple"
      >
        <div class="flex items-center justify-between gap-4">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-apple bg-apple-blue/10 text-apple-blue flex items-center justify-center">
              <FlaskConical :size="18" />
            </div>
            <div>
              <h2 class="text-sm font-bold text-apple-text">导入酶库</h2>
              <p class="mt-1 text-xs text-apple-secondary-text leading-6">来源于 Accession 导入，适合做结构补全与文献关联。</p>
            </div>
          </div>
          <ArrowRight :size="16" class="text-apple-secondary-text" />
        </div>
      </RouterLink>

      <RouterLink
        to="/library/predicted"
        class="rounded-apple-xl bg-white/50 dark:bg-white/5 p-8 hover:bg-white/70 dark:hover:bg-white/10 transition-colors shadow-apple"
      >
        <div class="flex items-center justify-between gap-4">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-apple bg-purple-500/10 text-purple-500 flex items-center justify-center">
              <Sparkles :size="18" />
            </div>
            <div>
              <h2 class="text-sm font-bold text-apple-text">预测成果库</h2>
              <p class="mt-1 text-xs text-apple-secondary-text leading-6">已确认入库的预测结构，独立于导入条目分仓管理。</p>
            </div>
          </div>
          <ArrowRight :size="16" class="text-apple-secondary-text" />
        </div>
      </RouterLink>
    </div>
  </div>
</template>
