<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Database, Upload, FileText, CheckCircle2, AlertCircle, Loader2, ArrowRight } from 'lucide-vue-next'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EnzymeLibraryTable from '@/components/EnzymeLibraryTable.vue'
import ImportTaskCard from '@/components/ImportTaskCard.vue'
import NcbiCredentialsForm from '@/components/NcbiCredentialsForm.vue'
import { useNcbiImport } from '@/composables/useNcbiImport'

const {
  taskName,
  accessionInput,
  moleculeType,
  ncbiEmail,
  ncbiApiKey,
  task,
  enzymes,
  loading,
  errorMessage,
  accessionCount,
  refreshAll,
  removeEnzyme,
  submitImport,
} = useNcbiImport()

const pendingDeleteId = ref<number | null>(null)
const deleteLoading = ref(false)
const pendingDeleteEntry = computed(() => enzymes.value.find((item) => item.id === pendingDeleteId.value) ?? null)

function openDeleteDialog(id: number) {
  pendingDeleteId.value = id
}

function closeDeleteDialog() {
  if (deleteLoading.value) return
  pendingDeleteId.value = null
}

async function confirmDelete() {
  if (pendingDeleteId.value == null) return
  deleteLoading.value = true
  try {
    await removeEnzyme(pendingDeleteId.value)
    pendingDeleteId.value = null
  } finally {
    deleteLoading.value = false
  }
}

onMounted(async () => {
  try {
    await refreshAll()
  } catch {
    // Silent
  }
})
</script>

<template>
  <div class="space-y-10 pb-20 motion-stagger">
    <!-- Header -->
    <div class="space-y-1">
      <h1 class="text-3xl font-bold tracking-tight text-apple-text">NCBI 导入中心</h1>
      <p class="text-apple-secondary-text text-sm">批量导入 NCBI accession，建立本地酶库并补齐基础元数据。</p>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-[1fr_400px] gap-8 items-start motion-stagger">
      <!-- Main Import Area -->
      <div class="apple-card p-8 space-y-8">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-apple bg-apple-blue/10 text-apple-blue flex items-center justify-center">
              <Upload :size="20" />
            </div>
            <div>
              <h2 class="text-lg font-bold text-apple-text">批量 Accession 录入</h2>
              <p class="text-xs text-apple-secondary-text">支持蛋白 accession 与核酸 accession 两种导入模式。</p>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <button class="text-xs font-bold text-apple-blue hover:opacity-80 transition-opacity flex items-center gap-1">
              <FileText :size="14" />
              下载 Excel 模板
            </button>
          </div>
        </div>

        <div class="space-y-6">
          <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div class="space-y-2">
              <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest ml-1">任务名称</label>
              <input 
                v-model="taskName"
                type="text" 
                placeholder="例如: 批次导入_20260720" 
                class="apple-input"
              />
            </div>
            <div class="space-y-2">
              <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest ml-1">导入类型</label>
              <div class="flex gap-2">
                <button
                  type="button"
                  class="flex-1 py-3 rounded-apple text-xs font-bold transition-all"
                  :class="moleculeType === 'protein'
                    ? 'bg-apple-blue text-white shadow-lg shadow-apple-blue/20'
                    : 'apple-soft-panel text-apple-secondary-text hover:text-apple-text'"
                  @click="moleculeType = 'protein'"
                >
                  蛋白质酶
                </button>
                <button
                  type="button"
                  class="flex-1 py-3 rounded-apple text-xs font-bold transition-all"
                  :class="moleculeType === 'RNA'
                    ? 'bg-emerald-500 text-white shadow-lg shadow-emerald-500/20'
                    : 'apple-soft-panel text-apple-secondary-text hover:text-apple-text'"
                  @click="moleculeType = 'RNA'"
                >
                  核酶 / RNA 酶
                </button>
              </div>
              <p class="text-[10px] text-apple-secondary-text ml-1">
                {{ moleculeType === 'protein'
                  ? '当前将检索 NCBI Protein 库，并继续尝试 UniProt / PDB 自动补全。'
                  : '当前将检索 NCBI Nucleotide 库，先导入 RNA 序列与基础信息。' }}
              </p>
            </div>
          </div>

          <!-- NCBI Credentials (Optional) -->
          <NcbiCredentialsForm 
            v-model:email="ncbiEmail"
            v-model:api-key="ncbiApiKey"
          />

          <div class="space-y-2">
            <div class="flex justify-between items-center ml-1">
              <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest">Accession 列表</label>
              <span class="text-[10px] font-bold text-apple-blue bg-apple-blue/10 px-2 py-0.5 rounded-full">
                {{ accessionCount }} 条识别
              </span>
            </div>
            <textarea 
              v-model="accessionInput"
              rows="12"
              :placeholder="moleculeType === 'protein'
                ? '每行输入一个 Protein Accession，例如 WP_010248927.1 或 NP_001092.1'
                : '每行输入一个 Nucleotide Accession，例如 NR_001566.1 或 XM_123456.2'"
              class="apple-input font-mono text-sm leading-relaxed resize-none"
            ></textarea>
          </div>

          <div class="flex flex-col md:flex-row items-center justify-between gap-4 pt-4 border-t border-black/20">
            <div class="flex items-center gap-3 text-xs text-apple-secondary-text">
              <div class="w-2 h-2 rounded-full bg-apple-green animate-pulse"></div>
              <span>NCBI E-utilities 已连接 (API_KEY 模式)</span>
            </div>
            <div class="flex items-center gap-4">
              <p v-if="errorMessage" class="text-xs text-red-500 font-medium flex items-center gap-1">
                <AlertCircle :size="14" />
                {{ errorMessage }}
              </p>
              <button 
                @click="submitImport"
                :disabled="loading || accessionCount === 0"
                class="apple-button-primary flex items-center gap-2 min-w-[160px] justify-center disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <template v-if="loading">
                  <Loader2 :size="16" class="animate-spin" />
                  处理中...
                </template>
                <template v-else>
                  提交异步导入
                  <ArrowRight :size="16" />
                </template>
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Right Sidebar Info -->
      <div class="space-y-8">
        <div class="apple-card p-6 bg-gradient-to-br from-apple-blue/5 to-transparent">
          <h3 class="text-sm font-bold text-apple-text mb-4">导入说明</h3>
          <ul class="space-y-4">
            <li class="flex gap-3">
              <div class="mt-1">
                <div class="w-1.5 h-1.5 rounded-full bg-apple-blue"></div>
              </div>
              <div>
                <p class="text-xs font-bold text-apple-text">异步处理</p>
                <p class="text-[10px] text-apple-secondary-text mt-1 leading-relaxed">系统会在后台执行 accession 抓取与解析，你可以继续在工作台查看实时进度。</p>
              </div>
            </li>
            <li class="flex gap-3">
              <div class="mt-1">
                <div class="w-1.5 h-1.5 rounded-full bg-apple-blue"></div>
              </div>
              <div>
                <p class="text-xs font-bold text-apple-text">自动补全</p>
                <p class="text-[10px] text-apple-secondary-text mt-1 leading-relaxed">系统会自动补全名称、序列、物种信息及可用的结构关联数据。</p>
              </div>
            </li>
            <li class="flex gap-3">
              <div class="mt-1">
                <div class="w-1.5 h-1.5 rounded-full bg-apple-blue"></div>
              </div>
              <div>
                <p class="text-xs font-bold text-apple-text">重复校验</p>
                <p class="text-[10px] text-apple-secondary-text mt-1 leading-relaxed">系统会自动跳过已存在于本地库中的 Accession 版本，避免冗余。</p>
              </div>
            </li>
          </ul>
        </div>

        <ImportTaskCard :task="task" />
      </div>
    </div>

    <EnzymeLibraryTable :enzymes="enzymes" @delete="openDeleteDialog" />

    <ConfirmDialog
      :open="pendingDeleteId !== null"
      title="删除酶条目"
      :message="`确定要从本地酶库中移除“${pendingDeleteEntry?.proteinName || pendingDeleteEntry?.accession || '该条目'}”吗？删除后将无法恢复。`"
      confirm-text="确认删除"
      :loading="deleteLoading"
      danger
      @cancel="closeDeleteDialog"
      @confirm="confirmDelete"
    />
  </div>
</template>
