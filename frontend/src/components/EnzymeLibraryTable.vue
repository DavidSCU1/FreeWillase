<script setup lang="ts">
import type { EnzymeEntry } from '@/types'
import { ExternalLink, ChevronRight, FlaskConical, Trash2 } from 'lucide-vue-next'

defineProps<{
  enzymes: EnzymeEntry[]
}>()

const emit = defineEmits<{
  'delete': [id: number]
}>()

function shortHash(hash: string) {
  return hash ? `${hash.slice(0, 12)}...` : '-'
}

function confirmDelete(id: number) {
  if (confirm('确定要从本地酶库中移除该条目吗？该操作不可撤销。')) {
    emit('delete', id)
  }
}
</script>

<template>
  <div class="apple-card overflow-hidden">
    <div class="p-6 flex items-center justify-between border-b border-apple-border bg-white/50 dark:bg-black/20">
      <div class="flex items-center gap-3">
        <div class="w-8 h-8 rounded-apple bg-apple-blue/10 text-apple-blue flex items-center justify-center">
          <FlaskConical :size="16" />
        </div>
        <div>
          <h2 class="text-sm font-bold text-apple-text tracking-tight">酶库中心</h2>
          <p class="text-[10px] text-apple-secondary-text uppercase tracking-wider font-semibold">Enzyme Library</p>
        </div>
      </div>
      <div class="flex items-center gap-4">
        <span class="text-xs font-medium text-apple-secondary-text">{{ enzymes.length }} 条记录</span>
        <button class="text-xs font-bold text-apple-blue hover:opacity-80 transition-opacity">查看全部</button>
      </div>
    </div>

    <div v-if="enzymes.length" class="overflow-x-auto">
      <table class="w-full text-left border-collapse">
        <thead>
          <tr class="bg-black/5 dark:bg-white/5 text-[10px] uppercase tracking-widest font-bold text-apple-secondary-text">
            <th class="px-6 py-4">Accession</th>
            <th class="px-6 py-4">Protein Name</th>
            <th class="px-6 py-4">Organism</th>
            <th class="px-6 py-4">Length</th>
            <th class="px-6 py-4">Sequence Hash</th>
            <th class="px-6 py-4">Action</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-apple-border">
          <tr 
            v-for="enzyme in enzymes" 
            :key="enzyme.id"
            class="group hover:bg-apple-blue/[0.02] transition-colors cursor-pointer"
          >
            <td class="px-6 py-4">
              <span class="text-xs font-bold text-apple-blue">{{ enzyme.accession }}</span>
            </td>
            <td class="px-6 py-4">
              <p class="text-xs font-semibold text-apple-text truncate max-w-[200px]">{{ enzyme.proteinName }}</p>
            </td>
            <td class="px-6 py-4">
              <p class="text-xs text-apple-secondary-text italic">{{ enzyme.organismName }}</p>
            </td>
            <td class="px-6 py-4">
              <span class="text-xs font-medium text-apple-secondary-text bg-apple-light-gray dark:bg-white/5 px-2 py-0.5 rounded-full">
                {{ enzyme.sequenceLength }} aa
              </span>
            </td>
            <td class="px-6 py-4">
              <span class="text-[10px] font-mono text-apple-secondary-text/60">{{ shortHash(enzyme.sequenceHash) }}</span>
            </td>
            <td class="px-6 py-4">
              <div class="flex items-center gap-2">
                <button class="p-1.5 rounded-full hover:bg-red-500/10 text-apple-secondary-text hover:text-red-500 transition-all" title="删除条目" @click.stop="confirmDelete(enzyme.id)">
                  <Trash2 :size="14" />
                </button>
                <button class="p-1.5 rounded-full hover:bg-apple-blue/10 text-apple-secondary-text hover:text-apple-blue transition-all">
                  <ExternalLink :size="14" />
                </button>
                <button class="p-1.5 rounded-full hover:bg-apple-blue/10 text-apple-secondary-text hover:text-apple-blue transition-all">
                  <ChevronRight :size="14" />
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-else class="p-20 text-center">
      <div class="w-16 h-16 bg-apple-light-gray dark:bg-white/5 rounded-full flex items-center justify-center mx-auto mb-4 text-apple-secondary-text">
        <FlaskConical :size="32" />
      </div>
      <h3 class="text-sm font-bold text-apple-text mb-1">暂无酶条目</h3>
      <p class="text-xs text-apple-secondary-text max-w-xs mx-auto">执行 NCBI Accession 导入后，这里将实时显示本地库记录。</p>
    </div>
  </div>
</template>
