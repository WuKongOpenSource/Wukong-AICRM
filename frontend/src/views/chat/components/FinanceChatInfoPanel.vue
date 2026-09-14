<template>
  <div class="flex h-full flex-col overflow-hidden bg-white">
    <header class="shrink-0 border-b border-[#ececec] px-4 py-3">
      <div class="flex items-start justify-between gap-3">
        <div class="min-w-0">
          <h2 class="truncate text-[15px] font-semibold text-[#0d0d0d]">财务概况</h2>
          <p class="mt-0.5 truncate text-xs text-[#8f8f8f]">现金流、应收风险和最近流水</p>
        </div>
        <button
          class="flex size-8 items-center justify-center rounded-lg text-[#8f8f8f] transition-colors hover:bg-[#f3f3f3] hover:text-[#0d0d0d]"
          type="button"
          title="刷新"
          @click="loadDashboard"
        >
          <span class="material-symbols-outlined text-[18px] leading-none">refresh</span>
        </button>
      </div>
    </header>

    <div class="min-h-0 flex-1 overflow-y-auto px-4 py-4">
      <div v-if="loading" class="flex h-32 items-center justify-center text-slate-300">
        <span class="material-symbols-outlined animate-spin text-[24px] leading-none">progress_activity</span>
      </div>
      <template v-else>
        <section class="rounded-xl border border-slate-200 bg-white px-4 py-4 shadow-sm">
          <div class="mb-4 flex items-center justify-between gap-2">
            <div class="flex min-w-0 items-center gap-2">
              <span :class="sectionIconBoxClass" :style="{ backgroundColor: '#1f1e1c' }">
                <span :class="sectionMaterialIconClass">auto_awesome</span>
              </span>
              <h3 class="min-w-0 text-sm font-bold leading-snug text-slate-900 break-words">AI财务分析</h3>
            </div>
            <button
              type="button"
              class="flex size-8 shrink-0 items-center justify-center rounded-full text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600 disabled:cursor-not-allowed disabled:opacity-50"
              :disabled="chatStore.currentSessionIsStreaming || analysisRunning"
              :title="analysisRunning ? '生成中' : (financeAnalysisReport ? '更新财务分析' : '生成财务分析')"
              :aria-label="analysisRunning ? '生成中' : (financeAnalysisReport ? '更新财务分析' : '生成财务分析')"
              @click="runFinanceAnalysis"
            >
              <span
                class="material-symbols-outlined text-[20px] leading-none"
                :class="{ 'animate-spin': analysisRunning }"
              >{{ analysisRunning ? 'progress_activity' : 'refresh' }}</span>
            </button>
          </div>

          <div v-if="analysisRunning" class="mb-4 rounded-2xl border border-sky-200 bg-sky-50 px-4 py-3">
            <div class="flex items-start gap-3">
              <div class="mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-xl bg-sky-100 text-sky-500">
                <span class="material-symbols-outlined text-[18px] leading-none animate-spin">progress_activity</span>
              </div>
              <div class="min-w-0">
                <p class="text-sm font-bold text-sky-700">正在生成财务分析</p>
                <p class="mt-1 text-xs leading-5 text-sky-600">AI 正在读取正式财务记录，完成后会在这里展示报告。</p>
              </div>
            </div>
          </div>

          <AiParseInsightSidebar
            :result="financeAiParseResult"
            :show-tip="false"
            compact-score
            unified
            empty-title="暂无 AI 财务分析"
            empty-description="点击下方按钮，让 AI 基于正式财务记录生成现金流、应收风险和下一步动作建议。"
            score-caption="基于现金流、逾期应收、未来应收和费用压力评估"
          >
            <template #empty-extra>
              <div class="mt-5 flex w-full justify-center px-1">
                <button
                  type="button"
                  class="inline-flex max-w-full items-center justify-center gap-1.5 rounded-full bg-primary px-4 py-2 text-xs font-bold text-white shadow-[0_8px_22px_-6px_rgba(37,99,235,0.55)] transition-[filter,transform] hover:brightness-105 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:brightness-100 disabled:active:scale-100"
                  :disabled="chatStore.currentSessionIsStreaming || analysisRunning"
                  :title="analysisRunning ? '生成中' : '生成财务分析'"
                  @click="runFinanceAnalysis"
                >
                  <span
                    v-if="chatStore.currentSessionIsStreaming || analysisRunning"
                    class="material-symbols-outlined shrink-0 text-[16px] leading-none animate-spin"
                  >progress_activity</span>
                  <span v-else class="material-symbols-outlined shrink-0 text-[16px] leading-none">auto_awesome</span>
                  <span class="truncate">生成财务分析</span>
                </button>
              </div>
            </template>
          </AiParseInsightSidebar>
        </section>

        <section class="mt-4 rounded-xl border border-slate-200 bg-white px-4 py-4 shadow-sm">
          <div class="mb-4 flex items-center justify-between gap-2">
            <div class="flex min-w-0 items-center gap-2">
              <span :class="sectionIconBoxClass" :style="{ backgroundColor: '#5f704a' }">
                <span :class="sectionMaterialIconClass">account_balance_wallet</span>
              </span>
              <div class="min-w-0">
                <h3 class="text-sm font-bold leading-snug text-slate-900">本月现金流</h3>
                <p class="mt-0.5 text-xs text-slate-400">收入、支出与净额</p>
              </div>
            </div>
            <div class="flex shrink-0 rounded-xl bg-slate-100 p-1 text-xs font-medium text-slate-500">
              <button class="finance-toggle-btn" :class="{ active: metricMode === 'summary' }" @click="metricMode = 'summary'">指标</button>
              <button class="finance-toggle-btn" :class="{ active: metricMode === 'trend' }" @click="metricMode = 'trend'">趋势</button>
            </div>
          </div>
          <div v-if="metricMode === 'summary'" class="space-y-3">
            <div class="rounded-2xl border border-slate-100 bg-slate-50/70 px-4 py-3">
              <div class="text-xs font-medium text-slate-400">本月净现金流</div>
              <div class="mt-1 break-words text-2xl font-bold leading-8" :class="cashFlowClass">{{ money(dashboard?.netCashFlow) }}</div>
            </div>
            <div class="grid grid-cols-2 gap-2">
              <div class="finance-metric-card">
                <div class="text-[11px] text-slate-400">收入</div>
                <div class="mt-1 break-words text-sm font-bold leading-5 text-slate-900">{{ money(dashboard?.monthIncome) }}</div>
              </div>
              <div class="finance-metric-card">
                <div class="text-[11px] text-slate-400">支出</div>
                <div class="mt-1 break-words text-sm font-bold leading-5 text-slate-900">{{ money(dashboard?.monthExpense) }}</div>
              </div>
            </div>
          </div>
          <div v-else class="space-y-3">
            <div v-for="item in dashboard?.cashFlow || []" :key="item.month">
              <div class="mb-1 flex items-center justify-between text-xs text-slate-500">
                <span>{{ item.month }}</span>
                <span>{{ money(item.net) }}</span>
              </div>
              <div class="h-2 overflow-hidden rounded-full bg-slate-100">
                <div class="h-full rounded-full" :class="Number(item.net || 0) < 0 ? 'bg-rose-400' : 'bg-emerald-500'" :style="{ width: trendWidth(item.net) }"></div>
              </div>
            </div>
          </div>
        </section>

        <section class="mt-4 rounded-xl border border-slate-200 bg-white px-4 py-4 shadow-sm">
          <div class="mb-4 flex items-center gap-2">
            <span :class="sectionIconBoxClass" :style="{ backgroundColor: '#8d4f34' }">
              <span :class="sectionMaterialIconClass">warning</span>
            </span>
            <div class="min-w-0">
              <h3 class="text-sm font-bold leading-snug text-slate-900">应收风险</h3>
              <p class="mt-0.5 text-xs text-slate-400">逾期与未来 90 天预计应收</p>
            </div>
          </div>
          <div class="grid grid-cols-2 gap-2">
            <div class="finance-metric-card">
              <div class="text-[11px] text-slate-400">逾期应收</div>
              <div class="mt-1 break-words text-sm font-bold leading-5 text-rose-600">{{ money(dashboard?.overdueReceivable) }}</div>
            </div>
            <div class="finance-metric-card">
              <div class="text-[11px] text-slate-400">未来 30 天</div>
              <div class="mt-1 break-words text-sm font-bold leading-5 text-slate-900">{{ money(dashboard?.receivable30) }}</div>
            </div>
            <div class="finance-metric-card">
              <div class="text-[11px] text-slate-400">31-60 天</div>
              <div class="mt-1 break-words text-sm font-bold leading-5 text-slate-900">{{ money(dashboard?.receivable60) }}</div>
            </div>
            <div class="finance-metric-card">
              <div class="text-[11px] text-slate-400">61-90 天</div>
              <div class="mt-1 break-words text-sm font-bold leading-5 text-slate-900">{{ money(dashboard?.receivable90) }}</div>
            </div>
          </div>
        </section>

        <section class="mt-4 rounded-xl border border-slate-200 bg-white px-4 py-4 shadow-sm">
          <div class="mb-4 flex items-center gap-2">
            <span :class="sectionIconBoxClass" :style="{ backgroundColor: '#cf744f' }">
              <span :class="sectionMaterialIconClass">receipt_long</span>
            </span>
            <h3 class="text-sm font-bold leading-snug text-slate-900">最近流水</h3>
          </div>
          <div v-if="!dashboard?.recentRecords?.length" class="rounded-2xl border-2 border-dashed border-slate-200 bg-slate-50/70 py-8 text-center">
            <span class="material-symbols-outlined text-3xl leading-none text-slate-300">receipt_long</span>
            <p class="mt-2 text-xs font-medium text-slate-400">暂无记录</p>
          </div>
          <div v-else class="space-y-2">
            <div v-for="record in dashboard.recentRecords.slice(0, 6)" :key="record.id" class="rounded-xl border border-slate-200 bg-white p-3 transition-all hover:border-primary/30 hover:shadow-md">
              <div class="truncate text-sm font-bold text-slate-900">{{ primaryText(record) }}</div>
              <div class="mt-1 flex items-center justify-between gap-2 text-xs text-slate-500">
                <span class="truncate">{{ record.customerName || record.projectName || '-' }}</span>
                <span class="shrink-0 font-semibold text-slate-700">{{ money(record.amount) }}</span>
              </div>
            </div>
          </div>
        </section>

        <section class="mt-4 rounded-xl border border-slate-200 bg-white px-4 py-4 shadow-sm">
          <div class="mb-3 flex items-center gap-2">
            <span :class="sectionIconBoxClass" :style="{ backgroundColor: '#1f1e1c' }">
              <span :class="sectionMaterialIconClass">tips_and_updates</span>
            </span>
            <h3 class="text-sm font-bold leading-snug text-slate-900">建议关注</h3>
          </div>
          <ul class="space-y-2 text-xs leading-5 text-slate-600">
            <li v-for="item in insights" :key="item" class="rounded-xl bg-slate-50/80 px-3 py-2">{{ item }}</li>
          </ul>
        </section>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { getFinanceDashboard } from '@/api/finance'
import { useChatStore } from '@/stores/chat'
import { useUserStore } from '@/stores/user'
import AiParseInsightSidebar from '@/components/crm/AiParseInsightSidebar.vue'
import type { CustomerAiParseVO } from '@/api/customer'
import type { FinanceDashboardVO, FinanceRecordVO } from '@/types/finance'

const dashboard = ref<FinanceDashboardVO | null>(null)
const loading = ref(false)
const chatStore = useChatStore()
const userStore = useUserStore()
const metricMode = ref<'summary' | 'trend'>('summary')
const analysisRunning = ref(false)
const financeAnalysisReport = ref('')
const financeAnalysisTime = ref('')
const FINANCE_ANALYSIS_STORAGE_KEY_PREFIX = 'wk_ai_crm:finance_analysis_report:v1'
const sectionIconBoxClass = 'inline-flex size-7 shrink-0 items-center justify-center rounded-lg text-white shadow-sm'
const sectionMaterialIconClass = 'material-symbols-outlined text-[17px] leading-none'

const cashFlowClass = computed(() => Number(dashboard.value?.netCashFlow || 0) < 0 ? 'text-rose-600' : 'text-emerald-700')
const financeAnalysisStorageKey = computed(() => {
  const userId = String(userStore.userId || 'anonymous')
  const sessionId = String(chatStore.currentSessionId || 'no-session')
  return `${FINANCE_ANALYSIS_STORAGE_KEY_PREFIX}:${userId}:${sessionId}`
})
const financeAiParseResult = computed<CustomerAiParseVO | null>(() => {
  const report = financeAnalysisReport.value.trim()
  if (!report) return null
  return {
    score: financeHealthScore.value,
    summary: financeAnalysisSummary.value,
    nextStep: financeAnalysisNextStep.value,
    keyPoints: financeAnalysisKeyPoints.value,
    tags: financeAnalysisTags.value
  }
})
const financeHealthScore = computed(() => {
  let score = 82
  if (Number(dashboard.value?.overdueReceivable || 0) > 0) score -= 18
  if (Number(dashboard.value?.monthExpense || 0) > Number(dashboard.value?.monthIncome || 0)) score -= 12
  if (Number(dashboard.value?.receivable30 || 0) > 0) score += 4
  return Math.max(35, Math.min(95, score))
})
const financeAnalysisSummary = computed(() => extractFinanceAnalysisSummary(financeAnalysisReport.value))
const financeAnalysisNextStep = computed(() => extractFinanceAnalysisNextStep(financeAnalysisReport.value))
const financeAnalysisKeyPoints = computed(() => {
  const summary = new Set(splitReportPoints(financeAnalysisSummary.value))
  const points = splitReportPoints(financeAnalysisReport.value).filter(point =>
    !summary.has(point) &&
    !/^总体判断|^现金流|^应收风险|^重点客户|^下一步/.test(point) &&
    !/下一步|行动|建议/.test(point)
  )
  return points.slice(0, 5)
})
const financeAnalysisTags = computed(() => {
  const tags: string[] = []
  if (Number(dashboard.value?.netCashFlow || 0) < 0) tags.push('现金流承压')
  else tags.push('现金流正常')
  if (Number(dashboard.value?.overdueReceivable || 0) > 0) tags.push('存在逾期')
  if (Number(dashboard.value?.receivable30 || 0) > 0) tags.push('近期应收')
  return tags
})
const insights = computed(() => {
  const items: string[] = []
  if (Number(dashboard.value?.overdueReceivable || 0) > 0) items.push('有逾期应收，建议优先追踪对应客户。')
  if (Number(dashboard.value?.receivable30 || 0) > 0) items.push('未来 30 天存在预计应收，可让 AI 按客户拆解。')
  if (Number(dashboard.value?.monthExpense || 0) > Number(dashboard.value?.monthIncome || 0)) items.push('本月支出高于收入，建议查看费用明细。')
  return items.length ? items : ['当前没有明显风险，可继续让 AI 按客户或项目生成报表。']
})

onMounted(() => {
  loadSavedFinanceAnalysis()
  void loadDashboard()
})

watch(financeAnalysisStorageKey, () => {
  loadSavedFinanceAnalysis()
})

async function loadDashboard() {
  loading.value = true
  try {
    dashboard.value = await getFinanceDashboard({})
  } finally {
    loading.value = false
  }
}

async function runFinanceAnalysis() {
  analysisRunning.value = true
  const beforeAssistantIds = new Set(chatStore.messages.filter(message => message.role === 'assistant').map(message => message.id))
  try {
    await chatStore.sendMessage(
      '请基于当前财务记录，分析本月现金流、逾期应收、未来90天应收、主要风险客户或项目，并给出下一步行动建议。请用简洁分段输出：总体判断、现金流、应收风险、重点客户或项目、下一步动作。',
      undefined,
      undefined,
      'finance'
    )
    const nextReport = [...chatStore.messages]
      .reverse()
      .find(message => message.role === 'assistant' && !beforeAssistantIds.has(message.id) && message.content?.trim())
      ?.content
      ?.trim()
    if (nextReport) {
      financeAnalysisReport.value = nextReport
      financeAnalysisTime.value = formatDateTime(new Date())
      saveFinanceAnalysis()
    }
    await loadDashboard()
  } finally {
    analysisRunning.value = false
  }
}

function primaryText(row: FinanceRecordVO) {
  return row.contractName || row.title || row.expenseType || row.invoiceNo || row.paymentMethod || row.remark || '财务记录'
}

function money(value: unknown) {
  const amount = Number(value || 0)
  return amount.toLocaleString('zh-CN', { style: 'currency', currency: 'CNY' })
}

function trendWidth(value: unknown) {
  const max = Math.max(...(dashboard.value?.cashFlow || []).map(item => Math.abs(Number(item.net || 0))), 1)
  return `${Math.max(8, Math.round(Math.abs(Number(value || 0)) / max * 100))}%`
}

function loadSavedFinanceAnalysis() {
  try {
    const raw = localStorage.getItem(financeAnalysisStorageKey.value)
    if (!raw) {
      financeAnalysisReport.value = ''
      financeAnalysisTime.value = ''
      return
    }
    const parsed = JSON.parse(raw) as { report?: unknown; time?: unknown }
    if (typeof parsed.report === 'string') financeAnalysisReport.value = parsed.report
    if (typeof parsed.time === 'string') financeAnalysisTime.value = parsed.time
  } catch {
    financeAnalysisReport.value = ''
    financeAnalysisTime.value = ''
  }
}

function saveFinanceAnalysis() {
  localStorage.setItem(financeAnalysisStorageKey.value, JSON.stringify({
    report: financeAnalysisReport.value,
    time: financeAnalysisTime.value
  }))
}

function formatDateTime(date: Date) {
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function splitReportPoints(value: string) {
  const normalized = String(value || '')
    .replace(/\r/g, '\n')
    .replace(/```[\s\S]*?```/g, '')
    .replace(/[ \t]*\n[ \t]*/g, '\n')
    .trim()

  if (!normalized) return []

  return normalized
    .split(/\n+/)
    .flatMap(line => splitInlineReportLine(line))
    .map(cleanReportPoint)
    .filter(isMeaningfulReportPoint)
    .filter((point, index, array) => array.indexOf(point) === index)
}

function extractFinanceAnalysisSummary(value: string) {
  const points = splitReportPoints(value)
  const summaryPoints = points.filter(point => !/下一步|行动|建议/.test(point)).slice(0, 3)
  return summaryPoints.length ? summaryPoints.join('\n') : value.trim()
}

function extractFinanceAnalysisNextStep(value: string) {
  const points = splitReportPoints(value)
  const nextStepPoints = points.filter(point => /下一步|行动|建议|跟进|优先/.test(point)).slice(0, 3)
  return nextStepPoints.join('\n')
}

function splitInlineReportLine(line: string) {
  const trimmed = line.trim()
  if (!trimmed) return []
  if (/^\s*(?:[-*•·]|\d+[.)、．]|[一二三四五六七八九十]+[.)、．])\s+/.test(trimmed)) return [trimmed]
  return trimmed.match(/[^。！？!?；;]+[。！？!?；;]?/g) || [trimmed]
}

function cleanReportPoint(value: string) {
  return value
    .replace(/^\s*(?:#{1,6}\s*)?/, '')
    .replace(/\*\*/g, '')
    .replace(/__+/g, '')
    .replace(/`+/g, '')
    .replace(/^\s*(?:[-*•·]|\d+[.)、．]|[一二三四五六七八九十]+[.)、．])\s*/, '')
    .replace(/^[：:，,、\s]+/, '')
    .replace(/\s+/g, ' ')
    .trim()
}

function isMeaningfulReportPoint(point: string) {
  if (!point) return false
  if (/^(总体判断|现金流|应收风险|重点客户或项目|重点客户|下一步动作|下一步|行动建议|建议)$/.test(point)) return false
  if (/^[\d.,，。+\-/%￥¥万千百亿\s]+$/.test(point)) return false
  if (/^[（(]?\s*[-+]?\d+(?:\.\d+)?\s*[）)]?$/.test(point)) return false
  if (point.length < 6 && !/[一-龥]/.test(point)) return false
  if (/^[（(][^）)]*$/.test(point)) return false
  return true
}
</script>

<style scoped>
.finance-metric-card {
  border-radius: 12px;
  border: 1px solid rgb(241 245 249);
  background: rgb(248 250 252 / 0.75);
  padding: 10px 12px;
}

.finance-toggle-btn {
  border-radius: 8px;
  padding: 4px 9px;
  transition: color 0.15s ease, background-color 0.15s ease, box-shadow 0.15s ease;
}

.finance-toggle-btn.active {
  background: #fff;
  color: #0f172a;
  box-shadow: 0 1px 2px rgb(15 23 42 / 0.08);
}
</style>
