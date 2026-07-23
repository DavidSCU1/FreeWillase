<script setup lang="ts">
import { computed } from 'vue'
import { 
  Settings, 
  Play, 
  Activity, 
  Dna, 
  Cpu, 
  Layers, 
  ChevronRight,
  Sparkles,
  Loader2,
  Microscope,
  Download
} from 'lucide-vue-next'
import { predictionModules } from '@/data/mock'
import StructureViewer from '@/components/StructureViewer.vue'
import { usePredictionStore } from '@/stores/prediction'

const store = usePredictionStore()

const engines = [
  { id: 'minifold', label: 'MiniFold-v1' },
  { id: 'biohub', label: 'Biohub' },
  { id: 'nvidia', label: 'NVIDIA ESMFold' },
  { id: 'chai1', label: 'Chai-1' },
] as const

const pendingCount = computed(() => store.tasks.filter(t => t.status === 'running').length)

const inputLabel = computed(() => store.moleculeType === 'ligand' ? '输入 SMILES' : '输入序列')

const inputPlaceholder = computed(() => {
  if (store.moleculeType === 'ligand') return '请输入配体 SMILES...'
  if (store.submitMode === 'batch') return '请输入多条 FASTA 记录，每条以 >名称 开头...'
  if (store.submitMode === 'complex') return '请输入多条 FASTA 记录（每条作为一条链），每条以 >名称 开头...'
  return '请输入单条序列，支持 Plain 或单条 FASTA（会自动忽略 > 标题行）...'
})

const defaultBaseUrlHint = computed(() => {
  if (store.provider === 'biohub') return '默认：https://www.biohub.ai'
  if (store.provider === 'nvidia') return '默认：https://health.api.nvidia.com'
  if (store.provider === 'chai1') return '默认：https://api.biolm.ai'
  return ''
})

const canSubmit = computed(() => !store.isSubmitting)

const inputFormatTitle = computed(() => {
  if (store.moleculeType === 'ligand') return '输入格式'
  if (store.submitMode === 'batch') return '多条提交格式'
  if (store.submitMode === 'complex') return '多链复合体格式'
  return '单条提交格式'
})

const inputFormatExample = computed(() => {
  if (store.moleculeType === 'ligand') {
    return 'CC(=O)OC1=CC=CC=C1C(=O)O'
  }

  if (store.submitMode === 'batch') {
    return `>seq1
MKTFFVLLLCTFTVQAAPDAGVTKTYLQDVGGKSTLQKQLAELNQGQKELAAKLEQKQK
>seq2
GSHMSTNPKPQRITFVKDAGQKALDNLVQKQGQKLEAELQKQKVGDKTLEEALNQK`
  }

  if (store.submitMode === 'complex') {
    return `>chainA
MKTFFVLLLCTFTVQAAPDAGVTKTYLQDVGGKSTLQKQLAELNQGQKELAAKLEQKQK
>chainB
GSHMSTNPKPQRITFVKDAGQKALDNLVQKQGQKLEAELQKQKVGDKTLEEALNQK`
  }

  return `>sample_1
MKTFFVLLLCTFTVQAAPDAGVTKTYLQDVGGKSTLQKQLAELNQGQKELAAKLEQKQK`
})

function downloadStructure() {
  const text = store.lastStructureText
  if (!text) return
  const ext = store.viewerFormat === 'mmcif' ? 'cif' : 'pdb'
  const blob = new Blob([text], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${store.name || 'prediction'}.${ext}`
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}
</script>

<template>
  <div class="space-y-8 pb-20">
    <!-- Header -->
    <div class="flex flex-col md:flex-row md:items-center justify-between gap-4">
      <div class="space-y-1">
        <h1 class="text-3xl font-bold tracking-tight text-apple-text">预测接口中心</h1>
        <p class="text-apple-secondary-text text-sm">MiniFold / ESM 结构预测引擎与参数配置</p>
      </div>
      <div class="flex items-center gap-3">
        <div class="flex items-center gap-2 px-3 py-1 rounded-full bg-apple-blue/10 text-apple-blue text-[10px] font-bold uppercase tracking-widest">
          <Activity :size="12" />
          引擎状态: 运行中
        </div>
      </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-8 items-start">
      <!-- Prediction Setup -->
      <div class="lg:col-span-2 space-y-8">
        <div class="apple-card p-8">
          <div class="flex items-center gap-3 mb-8">
            <div class="w-10 h-10 rounded-apple bg-apple-blue/10 text-apple-blue flex items-center justify-center">
              <Settings :size="20" />
            </div>
            <div>
              <h2 class="text-lg font-bold text-apple-text">预测参数配置</h2>
              <p class="text-xs text-apple-secondary-text">配置计算引擎与推理参数</p>
            </div>
          </div>

          <div class="space-y-8">
            <div class="grid grid-cols-1 md:grid-cols-2 gap-8">
              <div class="space-y-3">
                <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest ml-1">选择预测引擎</label>
                <div class="grid grid-cols-1 gap-2">
                  <button 
                    v-for="engine in engines" 
                    :key="engine.id"
                    @click="store.provider = engine.id"
                    :class="store.provider === engine.id ? 'border-apple-blue bg-apple-blue/5 text-apple-blue' : 'border-apple-border text-apple-secondary-text'"
                    class="w-full px-4 py-3 rounded-apple border text-xs font-bold flex items-center justify-between group transition-all"
                  >
                    {{ engine.label }}
                    <div :class="store.provider === engine.id ? 'bg-apple-blue' : 'bg-apple-secondary-text/20'" class="w-1.5 h-1.5 rounded-full"></div>
                  </button>
                </div>
              </div>
              <div class="space-y-3">
                <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest ml-1">API 与参数</label>
                <div class="space-y-4 p-4 rounded-apple bg-apple-background dark:bg-white/5 border border-apple-border">
                  <div class="space-y-2">
                    <span class="text-[10px] text-apple-secondary-text font-bold">API Key</span>
                    <input
                      v-model="store.apiKey"
                      type="password"
                      placeholder="用户自行填写（不会写入代码）"
                      class="apple-input text-xs"
                    />
                    <div class="flex items-center justify-between">
                      <label class="flex items-center gap-2 text-[10px] font-bold text-apple-secondary-text">
                        <input v-model="store.rememberApiKey" type="checkbox" class="accent-apple-blue" />
                        记住 API Key (本地)
                      </label>
                      <button
                        type="button"
                        class="text-[10px] font-bold text-apple-secondary-text hover:text-apple-text transition-colors"
                        @click="store.clearApiKey"
                      >
                        清除
                      </button>
                    </div>
                  </div>

                  <div class="space-y-2">
                    <span class="text-[10px] text-apple-secondary-text font-bold">Base URL</span>
                    <input
                      v-model="store.baseUrl"
                      type="text"
                      :placeholder="`留空使用默认（${defaultBaseUrlHint}）`"
                      class="apple-input text-xs"
                    />
                    <div v-if="defaultBaseUrlHint" class="text-[10px] text-apple-secondary-text">{{ defaultBaseUrlHint }}</div>
                  </div>

                  <div v-if="store.provider !== 'minifold'" class="grid grid-cols-1 md:grid-cols-2 gap-3">
                    <div class="space-y-2">
                      <span class="text-[10px] text-apple-secondary-text font-bold">Model</span>
                      <select v-model="store.model" class="apple-input text-xs">
                        <option v-for="m in store.supportedModels" :key="m" :value="m">{{ m }}</option>
                      </select>
                    </div>
                    <div class="space-y-2">
                      <span class="text-[10px] text-apple-secondary-text font-bold">Molecule Type</span>
                      <select v-model="store.moleculeType" class="apple-input text-xs">
                        <option v-for="t in store.supportedTypes" :key="t" :value="t">{{ t }}</option>
                      </select>
                    </div>
                  </div>

                  <div v-if="store.provider !== 'minifold'" class="space-y-2">
                    <span class="text-[10px] text-apple-secondary-text font-bold">提交模式</span>
                    <div class="grid grid-cols-3 gap-2">
                      <button
                        type="button"
                        @click="store.submitMode = 'single'"
                        :class="store.submitMode === 'single' ? 'border-apple-blue bg-apple-blue/5 text-apple-blue' : 'border-apple-border text-apple-secondary-text'"
                        class="px-4 py-3 rounded-apple border text-xs font-bold transition-all"
                      >
                        单条提交
                      </button>
                      <button
                        type="button"
                        @click="store.submitMode = 'batch'"
                        :disabled="store.moleculeType === 'ligand'"
                        :class="store.submitMode === 'batch' ? 'border-apple-blue bg-apple-blue/5 text-apple-blue' : 'border-apple-border text-apple-secondary-text'"
                        class="px-4 py-3 rounded-apple border text-xs font-bold transition-all disabled:opacity-50"
                      >
                        多条提交
                      </button>
                      <button
                        type="button"
                        @click="store.submitMode = 'complex'"
                        :disabled="store.moleculeType === 'ligand' || store.provider !== 'chai1'"
                        :class="store.submitMode === 'complex' ? 'border-apple-blue bg-apple-blue/5 text-apple-blue' : 'border-apple-border text-apple-secondary-text'"
                        class="px-4 py-3 rounded-apple border text-xs font-bold transition-all disabled:opacity-50"
                      >
                        多链复合体
                      </button>
                    </div>
                    <div class="text-[10px] text-apple-secondary-text">
                      单条提交支持 Plain 或单条 FASTA；多条提交会将每条 FASTA 拆成独立任务；多链复合体会将多条 FASTA 作为同一次预测的不同链（仅 Chai-1）。
                    </div>
                  </div>

                  <div class="space-y-2">
                    <span class="text-[10px] text-apple-secondary-text font-bold">Name</span>
                    <input v-model="store.name" type="text" placeholder="样本名/ID" class="apple-input text-xs" />
                  </div>

                  <div v-if="store.provider === 'minifold'" class="space-y-3 p-3 rounded-apple bg-orange-500/5 border border-orange-500/10">
                    <div class="flex items-center gap-2 text-orange-600">
                      <Sparkles :size="14" />
                      <span class="text-[10px] font-bold uppercase tracking-tight">MiniFold-v1 Ark Hybrid</span>
                    </div>
                    <div class="text-[10px] text-orange-600/80 leading-relaxed">
                      基于火山引擎 Ark 大模型优化与物理折叠引擎。请在下方填写你的火山 API Key 以激活 AI 投票与精修功能。
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div class="space-y-3">
              <label class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest ml-1">{{ inputLabel }}</label>
              <textarea
                v-if="store.moleculeType !== 'ligand'"
                v-model="store.sequence"
                rows="6"
                :placeholder="inputPlaceholder"
                class="apple-input font-mono text-xs leading-relaxed resize-none"
              ></textarea>
              <textarea
                v-else
                v-model="store.smiles"
                rows="6"
                :placeholder="inputPlaceholder"
                class="apple-input font-mono text-xs leading-relaxed resize-none"
              ></textarea>
              <div v-if="store.moleculeType !== 'ligand'" class="text-[10px] text-apple-secondary-text">
                {{
                  store.submitMode === 'batch'
                    ? '多条提交时请使用多 FASTA 格式，每条记录都以 >名称 开头。'
                    : store.submitMode === 'complex'
                      ? '多链复合体时请使用多 FASTA 格式，每条记录会作为一条链（Chain）。'
                      : '单条提交支持 Plain 或单条 FASTA，系统会自动去掉标题行与换行。'
                }}
              </div>
              <div class="rounded-apple border border-apple-border bg-apple-background dark:bg-white/5 p-4">
                <div class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest mb-2">{{ inputFormatTitle }}</div>
                <pre class="text-xs font-mono text-apple-text whitespace-pre-wrap break-all">{{ inputFormatExample }}</pre>
              </div>
            </div>

            <div class="pt-6 border-t border-apple-border flex items-center justify-between">
              <div class="flex items-center gap-4 text-[10px] text-apple-secondary-text font-bold">
                <span class="flex items-center gap-1"><Cpu :size="12" /> Provider: {{ store.provider }}</span>
                <span class="flex items-center gap-1"><Layers :size="12" /> Model: {{ store.model || '—' }}</span>
              </div>
              <button 
                @click="store.submit"
                :disabled="!canSubmit"
                class="apple-button-primary flex items-center gap-2 min-w-[160px] justify-center disabled:opacity-50"
              >
                <template v-if="store.isSubmitting">
                  <Loader2 :size="16" class="animate-spin" />
                  提交任务中...
                </template>
                <template v-else>
                  <Play :size="16" />
                  开始结构预测
                </template>
              </button>
            </div>
            <div v-if="store.error" class="text-xs font-bold text-red-500">{{ store.error }}</div>
          </div>
        </div>

        <div class="apple-card p-8">
          <div class="flex items-center gap-3 mb-8">
            <div class="w-10 h-10 rounded-apple bg-emerald-500/10 text-emerald-500 flex items-center justify-center">
              <Microscope :size="20" />
            </div>
            <div>
              <h2 class="text-lg font-bold text-apple-text">结构结果预览</h2>
              <p class="text-xs text-apple-secondary-text">支持 PDB / mmCIF</p>
            </div>
          </div>

          <div class="space-y-4">
            <div class="flex items-center justify-between gap-3 flex-wrap">
              <div class="flex items-center gap-4 text-[10px] text-apple-secondary-text font-bold">
                <span v-if="store.activeTask?.result" class="flex items-center gap-1">
                  Provider: {{ store.activeTask.result.providerName }}
                </span>
                <span v-if="store.activeTask?.result" class="flex items-center gap-1">
                  Model: {{ store.activeTask.result.modelName }}
                </span>
                <span v-if="store.activeTask?.result?.plddt != null" class="flex items-center gap-1">
                  pLDDT: {{ store.activeTask.result.plddt.toFixed(2) }}
                </span>
                <span v-if="store.activeTask?.result?.ptm != null" class="flex items-center gap-1">
                  pTM: {{ store.activeTask.result.ptm.toFixed(3) }}
                </span>
              </div>

              <button
                v-if="store.lastStructureText"
                type="button"
                class="apple-button-secondary flex items-center gap-2"
                @click="downloadStructure"
              >
                <Download :size="16" />
                下载结构文件
              </button>
            </div>

            <StructureViewer
              :url="store.viewerUrl || undefined"
              :format="store.viewerFormat"
            />
          </div>
        </div>

        <!-- Prediction Modules Mapping -->
        <div class="apple-card p-8">
          <div class="flex items-center gap-3 mb-8">
            <div class="w-10 h-10 rounded-apple bg-purple-500/10 text-purple-500 flex items-center justify-center">
              <Dna :size="20" />
            </div>
            <div>
              <h2 class="text-lg font-bold text-apple-text">MiniFold 模块映射</h2>
              <p class="text-xs text-apple-secondary-text">内部算法模块集成状态</p>
            </div>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div 
              v-for="module in predictionModules" 
              :key="module.name"
              class="p-4 rounded-apple border border-apple-border bg-apple-background dark:bg-white/5 hover:border-apple-blue/20 transition-all group"
            >
              <div class="flex items-center justify-between mb-2">
                <span class="text-[10px] font-mono font-bold text-apple-blue uppercase tracking-tighter">{{ module.name }}</span>
                <div class="px-2 py-0.5 rounded-full bg-apple-blue/10 text-apple-blue text-[9px] font-bold uppercase tracking-widest">
                  Ready
                </div>
              </div>
              <p class="text-xs font-bold text-apple-text mb-1">{{ module.purpose }}</p>
              <p class="text-[10px] text-apple-secondary-text leading-relaxed">{{ module.status }}</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Right Sidebar -->
      <div class="space-y-8">
        <!-- Queue Card -->
        <div class="apple-card p-6">
          <div class="flex items-center justify-between mb-6">
            <h3 class="text-sm font-bold text-apple-text">任务队列</h3>
            <span class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-widest">{{ pendingCount }} 运行中</span>
          </div>
          <div class="space-y-4">
            <button
              v-for="task in store.tasks.slice(0, 8)"
              :key="task.id"
              type="button"
              class="p-3 rounded-apple bg-apple-background dark:bg-white/5 border border-apple-border flex items-center justify-between w-full text-left hover:border-apple-blue/20 transition-all"
              :class="store.activeTaskId === task.id ? 'border-apple-blue/40' : ''"
              @click="store.selectTask(task.id)"
            >
              <div class="flex items-center gap-3">
                <div
                  class="w-2 h-2 rounded-full"
                  :class="task.status === 'running' ? 'bg-apple-blue animate-pulse' : task.status === 'success' ? 'bg-apple-green' : 'bg-red-500'"
                ></div>
                <div>
                  <p class="text-xs font-bold text-apple-text">{{ task.name }}</p>
                  <p class="text-[10px] text-apple-secondary-text">
                    {{ task.sequenceLength != null ? `${task.sequenceLength} aa` : task.moleculeType }} • {{ task.provider }}
                  </p>
                </div>
              </div>
              <span class="text-[10px] font-bold text-apple-secondary-text uppercase tracking-tighter">
                {{ task.status === 'running' ? 'Running' : task.status === 'success' ? 'Done' : 'Error' }}
              </span>
            </button>

            <div v-if="store.tasks.length === 0" class="text-[10px] text-apple-secondary-text font-bold">
              暂无任务，请先提交一次预测
            </div>
          </div>
        </div>

        <!-- Help Card -->
        <div class="apple-card p-6 bg-gradient-to-br from-purple-500/5 to-transparent border-purple-500/10">
          <div class="flex items-center gap-3 mb-4">
            <div class="w-8 h-8 rounded-apple bg-purple-500/10 text-purple-500 flex items-center justify-center">
              <Sparkles :size="16" />
            </div>
            <h3 class="text-sm font-bold text-apple-text">预测说明</h3>
          </div>
          <p class="text-xs text-apple-secondary-text leading-relaxed">
            本板块集成 why666-big/ENA_API_test 的第三方结构预测调用方式（Biohub / NVIDIA / Chai-1）。API Key 需用户自行填写，可选择仅本次会话使用或本地记住；若第三方接口存在 CORS 限制，请改用后端代理转发。
          </p>
          <div class="mt-4 flex flex-col gap-2">
            <button class="w-full text-left p-3 rounded-apple bg-white/50 dark:bg-black/20 text-[10px] font-bold text-apple-text flex items-center justify-between hover:bg-white/80 transition-all">
              查看预测协议说明
              <ChevronRight :size="12" />
            </button>
            <button class="w-full text-left p-3 rounded-apple bg-white/50 dark:bg-black/20 text-[10px] font-bold text-apple-text flex items-center justify-between hover:bg-white/80 transition-all">
              计算资源分配
              <ChevronRight :size="12" />
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
