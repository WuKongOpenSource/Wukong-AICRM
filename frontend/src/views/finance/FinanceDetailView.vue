<template>
  <div class="h-full flex flex-col wk-object-detail-embedded">
    <div v-if="loading" class="flex-1 flex items-center justify-center">
      <span class="material-symbols-outlined text-slate-300 text-4xl animate-spin">progress_activity</span>
    </div>

    <template v-else-if="record">
      <div class="min-h-0 flex-1 overflow-auto">
        <div class="static z-20 bg-background-light/90 backdrop-blur-md px-4 md:px-8 pt-4 pb-4 border-b border-slate-200/50 shrink-0 md:sticky md:top-0">
          <div class="flex items-center gap-2 text-sm text-slate-500 mb-3">
            <button @click="goBack" class="hover:text-primary flex items-center gap-1 transition-colors">
              财务列表
            </button>
            <span class="material-symbols-outlined text-xs">chevron_right</span>
            <span class="text-slate-900 font-medium">{{ activeMeta.label }}详情</span>
          </div>

          <div class="bg-white border border-slate-200 rounded-xl p-4 shadow-sm">
            <div class="flex flex-col gap-3 md:flex-row md:justify-between md:gap-0 justify-between">
              <div class="flex min-w-0 gap-4">
                <div class="size-14 bg-slate-100 rounded-lg flex items-center justify-center border border-slate-200 overflow-hidden shrink-0">
                  <span class="text-2xl font-bold text-slate-400">{{ activeMeta.label.charAt(0) }}</span>
                </div>
                <div class="min-w-0 space-y-2">
                  <div class="flex flex-col items-start gap-1 md:flex-row md:items-center md:gap-3 md:flex-wrap">
                    <h2 class="text-lg md:text-xl font-bold text-slate-900 truncate min-w-0 w-full md:w-auto">
                      {{ title }}
                    </h2>
                    <span class="px-2 py-0.5 text-xs font-bold rounded" :class="statusBadgeClass">
                      {{ statusLabel(record.status) }}
                    </span>
                  </div>
                  <div class="hidden md:flex w-full min-w-0 flex-wrap items-center gap-x-3 gap-y-2 text-sm">
                    <div class="flex min-w-0 flex-wrap items-center gap-x-4 gap-y-1">
                      <div class="flex items-center gap-1 shrink-0">
                        <span class="text-slate-400">客户:</span>
                        <span class="text-slate-600 font-medium">{{ record.customerName || '-' }}</span>
                      </div>
                      <div class="flex items-center gap-1 shrink-0">
                        <span class="text-slate-400">项目:</span>
                        <span class="text-slate-600 font-medium">{{ record.projectName || '-' }}</span>
                      </div>
                      <div class="flex items-center gap-1 shrink-0">
                        <span class="text-slate-400">金额:</span>
                        <span class="text-primary font-bold">{{ money(record.amount) }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <div class="mt-0 flex w-full min-w-0 flex-wrap items-center gap-x-3 gap-y-2 text-sm md:hidden">
                <div class="flex min-w-0 flex-wrap items-center gap-x-4 gap-y-1">
                  <div class="flex items-center gap-1 shrink-0">
                    <span class="text-slate-400">客户:</span>
                    <span class="text-slate-600 font-medium">{{ record.customerName || '-' }}</span>
                  </div>
                  <div class="flex items-center gap-1 shrink-0">
                    <span class="text-slate-400">金额:</span>
                    <span class="text-primary font-bold">{{ money(record.amount) }}</span>
                  </div>
                </div>
              </div>

              <div class="flex w-full flex-nowrap justify-start gap-2 md:overflow-visible md:w-auto md:flex-nowrap md:justify-start shrink-0">
                <button class="h-8 px-4 inline-flex items-center border border-slate-200 rounded-lg text-sm font-medium hover:bg-slate-50 transition-colors whitespace-nowrap" @click="showBasicInfoDrawer = true">
                  编辑
                </button>
                <button class="h-8 px-4 bg-primary/10 text-primary border border-primary/20 rounded-lg text-sm font-bold flex items-center gap-1.5 hover:bg-primary/20 transition-colors whitespace-nowrap" @click="handleAiInput">
                  <span class="material-symbols-outlined text-base leading-none">keyboard_voice</span>
                  语音识别
                </button>
                <button
                  type="button"
                  class="h-8 shrink-0 inline-flex items-center gap-1.5 rounded-lg bg-primary px-4 text-sm font-semibold text-white shadow-md shadow-primary/25 transition-colors hover:bg-primary/90 whitespace-nowrap"
                  @click="showBasicInfoDrawer = true"
                >
                  <span class="material-symbols-outlined text-base leading-none">description</span>
                  <span>基本信息</span>
                </button>
                <el-dropdown trigger="click">
                  <button
                    type="button"
                    class="h-8 w-8 shrink-0 inline-flex items-center justify-center rounded-lg border border-solid border-slate-200 text-slate-400 hover:bg-slate-50 hover:text-slate-600 transition-colors"
                    title="更多操作"
                  >
                    <span class="material-symbols-outlined text-lg">more_horiz</span>
                  </button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item @click="showBasicInfoDrawer = true">
                        <span class="flex items-center gap-2">
                          <span class="material-symbols-outlined text-sm">edit</span>
                          编辑详情
                        </span>
                      </el-dropdown-item>
                      <el-dropdown-item @click="handleOpenFinanceList">
                        <span class="flex items-center gap-2">
                          <span class="material-symbols-outlined text-sm">list_alt</span>
                          返回列表
                        </span>
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </div>

            <div class="mt-5 pt-4 border-t border-slate-100">
              <div class="wk-customer-stage-scroll relative overflow-visible overflow-x-auto overflow-y-visible md:overflow-visible">
                <div class="relative flex flex-nowrap md:flex-wrap items-stretch gap-x-2 md:gap-x-0 gap-y-2 min-w-max">
                  <div
                    v-for="(stage, idx) in financeStageFlow"
                    :key="stage.value"
                    class="relative h-8 flex-none w-[180px] group cursor-default"
                    :title="stage.label"
                    :style="{ zIndex: financeStageFlow.length - idx }"
                  >
                    <div
                      class="absolute inset-0 transition-all duration-300"
                      :class="getStepperSegmentBgClass(idx)"
                      :style="{ clipPath: getStepperClipPath(idx) }"
                    ></div>
                    <div class="relative z-10 flex h-full items-center justify-center overflow-hidden px-4">
                      <div class="flex min-w-0 max-w-full items-center justify-center gap-2">
                        <span
                          class="block min-w-0 truncate text-[14px] font-bold tracking-wider transition-colors"
                          :class="getStepperLabelClass(idx)"
                        >{{ stage.label }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="wk-mobile-px-15 md:px-8 pb-8 pt-3">
          <div class="lg:hidden mb-4">
            <div class="flex items-center gap-2 p-1 rounded-xl bg-slate-100">
              <button
                type="button"
                class="flex-1 h-9 px-3 rounded-lg text-sm font-bold transition-colors"
                :class="detailTab === 'ai' ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500'"
                @click="detailTab = 'ai'"
              >
                AI分析
              </button>
              <button
                type="button"
                class="flex-1 h-9 px-3 rounded-lg text-sm font-bold transition-colors"
                :class="detailTab === 'activity' ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500'"
                @click="detailTab = 'activity'"
              >
                最近活动
              </button>
              <button
                type="button"
                class="flex-1 h-9 px-3 rounded-lg text-sm font-bold transition-colors"
                :class="detailTab === 'related' ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500'"
                @click="detailTab = 'related'"
              >
                关联模块
              </button>
            </div>
          </div>

          <div class="grid grid-cols-1 lg:grid-cols-12 gap-4">
            <div :class="[detailTab === 'ai' ? 'block' : 'hidden', 'lg:block lg:col-span-3 space-y-4']">
              <section class="group/ai-section bg-white rounded-xl border border-slate-200 px-4 shadow-sm py-4">
                <div class="mb-4 space-y-1">
                  <div class="flex items-center justify-between gap-2">
                    <div class="flex min-w-0 flex-1 items-center gap-2">
                      <span :class="sectionIconBoxClass" :style="getSectionIconStyle('ai')">
                        <span :class="sectionMaterialIconClass">auto_awesome</span>
                      </span>
                      <h3 class="min-w-0 text-sm font-bold leading-snug text-slate-900 break-words">AI分析</h3>
                    </div>
                    <div class="flex shrink-0 items-center justify-end gap-2">
                      <p class="text-right text-xs leading-relaxed text-slate-400">{{ record.updateTime || record.createTime || '-' }}更新</p>
                      <button
                        type="button"
                        class="flex size-8 shrink-0 items-center justify-center rounded-full text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600"
                        title="更新 AI 分析"
                        aria-label="更新 AI 分析"
                        @click="handleRefreshAiAnalysis"
                      >
                        <span class="material-symbols-outlined text-[20px] leading-none">refresh</span>
                      </button>
                    </div>
                  </div>
                </div>

                <div class="rounded-2xl border border-slate-100 bg-slate-50/70 p-4">
                  <p class="text-sm font-bold text-slate-900">{{ aiInsightTitle }}</p>
                  <p class="mt-2 text-xs leading-5 text-slate-500">{{ aiInsightDescription }}</p>
                  <div class="mt-4 grid grid-cols-2 gap-2">
                    <div v-for="item in insightMetrics" :key="item.label" class="rounded-lg bg-white px-3 py-2 shadow-sm border border-slate-100">
                      <div class="text-[11px] text-slate-400">{{ item.label }}</div>
                      <div class="mt-1 break-words text-sm font-bold leading-5 text-slate-900">{{ item.value }}</div>
                    </div>
                  </div>
                </div>
              </section>
            </div>

            <div
              class="wk-customer-detail-activity group/activity"
              :class="[detailTab === 'activity' ? 'block' : 'hidden', 'lg:block lg:col-span-6 space-y-4']"
            >
              <div class="flex min-w-0 flex-col items-start gap-2 md:flex-row md:items-center md:justify-between">
                <div class="flex w-full min-w-0 items-center gap-2">
                  <span :class="sectionIconBoxClass" :style="getSectionIconStyle('activity')">
                    <span :class="sectionMaterialIconClass">history</span>
                  </span>
                  <h3 class="min-w-0 text-sm font-bold leading-snug text-slate-900 break-words">最近活动 - AI时间轴</h3>
                </div>
                <div class="flex w-full justify-start md:w-auto shrink-0 flex-wrap items-center gap-2 md:gap-3">
                  <div class="flex flex-wrap items-center gap-2">
                    <button
                      v-for="option in activityFilters"
                      :key="option.value"
                      type="button"
                      :class="[
                        'rounded-full px-3 py-1.5 text-xs font-medium transition-colors',
                        selectedActivityType === option.value
                          ? 'bg-primary text-white shadow-sm'
                          : 'bg-slate-100 text-slate-500 hover:bg-slate-200'
                      ]"
                      @click="selectedActivityType = option.value"
                    >
                      {{ option.label }}
                    </button>
                  </div>
                </div>
              </div>

              <div class="bg-white border border-slate-200 rounded-xl p-12 text-center shadow-sm">
                <span class="material-symbols-outlined mb-3 text-4xl text-slate-300">event_note</span>
                <p class="text-sm text-slate-400">暂无动态记录</p>
                <p class="text-xs text-slate-300 mt-1">AI 写入、手动修正和附件变化会在这里形成时间轴</p>
              </div>
            </div>

            <div
              class="wk-related-modules"
              :class="[detailTab === 'related' ? 'block' : 'hidden', 'lg:block lg:col-span-3 space-y-4']"
            >
              <div class="flex items-center justify-between px-1">
                <h3 class="text-base font-bold text-slate-900 flex items-center gap-2">
                  <span :class="sectionIconBoxClass" :style="getSectionIconStyle('related')">
                    <span :class="sectionMaterialIconClass">hub</span>
                  </span>
                  关联业务模块
                </h3>
                <span class="text-xs font-bold text-slate-400 bg-slate-100 px-2 py-0.5 rounded-full uppercase tracking-tighter">{{ visibleRelatedModuleCount }}个模块</span>
              </div>

              <section class="finance-related-module">
                <div class="mb-4 flex items-center justify-between">
                  <h4 class="flex items-center gap-2 text-sm font-bold text-slate-900">
                    <span :class="relatedSectionIconBoxClass" :style="{ backgroundColor: '#8d4f34' }">
                      <span class="material-symbols-outlined text-[17px] leading-none">hub</span>
                    </span>
                    关联对象
                  </h4>
                  <button
                    type="button"
                    class="group/module-action relative inline-flex size-7 shrink-0 items-center justify-center rounded-lg bg-white text-slate-500 transition-[background-color,color,border-color] hover:bg-[#efefef] hover:text-[#0d0d0d]"
                    aria-label="查看基础信息"
                    @click="showBasicInfoDrawer = true"
                  >
                    <span class="material-symbols-outlined text-[16px] leading-none">keyboard_arrow_right</span>
                    <span class="finance-tooltip">查看基础信息</span>
                  </button>
                </div>

                <div v-if="relatedObjectCount > 0" class="space-y-3">
                  <button v-if="record.customerId" class="finance-object-card" @click="goCustomer(record.customerId)">
                    <div class="flex size-10 shrink-0 items-center justify-center rounded-xl border border-slate-200 bg-slate-50 text-sm font-bold text-slate-600 transition-colors group-hover:border-primary/20 group-hover:bg-primary/10 group-hover:text-primary">
                      {{ (record.customerName || '客').charAt(0) }}
                    </div>
                    <div class="min-w-0 flex-1 text-left">
                      <div class="mb-1 flex min-w-0 items-center justify-between gap-2">
                        <h5 class="min-w-0 truncate text-sm font-bold text-slate-900 transition-colors group-hover:text-primary">{{ record.customerName || record.customerId }}</h5>
                        <span class="shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-bold text-slate-500">客户</span>
                      </div>
                      <p class="truncate text-xs font-medium text-slate-400">查看客户详情与完整关联业务</p>
                    </div>
                  </button>

                  <button v-if="record.projectId" class="finance-object-card" @click="goProject(record.projectId)">
                    <div class="flex size-10 shrink-0 items-center justify-center rounded-xl border border-slate-200 bg-slate-50 text-sm font-bold text-slate-600 transition-colors group-hover:border-primary/20 group-hover:bg-primary/10 group-hover:text-primary">
                      {{ (record.projectName || '项').charAt(0) }}
                    </div>
                    <div class="min-w-0 flex-1 text-left">
                      <div class="mb-1 flex min-w-0 items-center justify-between gap-2">
                        <h5 class="min-w-0 truncate text-sm font-bold text-slate-900 transition-colors group-hover:text-primary">{{ record.projectName || record.projectId }}</h5>
                        <span class="shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-bold text-slate-500">项目</span>
                      </div>
                      <p class="truncate text-xs font-medium text-slate-400">查看项目详情与执行信息</p>
                    </div>
                  </button>

                  <button v-if="record.contractId && activeType !== 'contract'" class="finance-object-card" @click="goFinanceDetail('contract', record.contractId)">
                    <div class="flex size-10 shrink-0 items-center justify-center rounded-xl border border-slate-200 bg-slate-50 text-sm font-bold text-slate-600 transition-colors group-hover:border-primary/20 group-hover:bg-primary/10 group-hover:text-primary">
                      合
                    </div>
                    <div class="min-w-0 flex-1 text-left">
                      <div class="mb-1 flex min-w-0 items-center justify-between gap-2">
                        <h5 class="min-w-0 truncate text-sm font-bold text-slate-900 transition-colors group-hover:text-primary">{{ record.contractName || record.contractId }}</h5>
                        <span class="shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-bold text-slate-500">合同</span>
                      </div>
                      <p class="truncate text-xs font-medium text-slate-400">查看关联合同详情</p>
                    </div>
                  </button>

                  <button v-if="record.receivableId && activeType !== 'receivable'" class="finance-object-card" @click="goFinanceDetail('receivable', record.receivableId)">
                    <div class="flex size-10 shrink-0 items-center justify-center rounded-xl border border-slate-200 bg-slate-50 text-sm font-bold text-slate-600 transition-colors group-hover:border-primary/20 group-hover:bg-primary/10 group-hover:text-primary">
                      应
                    </div>
                    <div class="min-w-0 flex-1 text-left">
                      <div class="mb-1 flex min-w-0 items-center justify-between gap-2">
                        <h5 class="min-w-0 truncate text-sm font-bold text-slate-900 transition-colors group-hover:text-primary">{{ record.receivableId }}</h5>
                        <span class="shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-bold text-slate-500">应收</span>
                      </div>
                      <p class="truncate text-xs font-medium text-slate-400">查看关联应收详情</p>
                    </div>
                  </button>
                </div>

                <div v-else class="rounded-2xl border-2 border-dashed border-slate-200 bg-slate-50/70 py-8 text-center">
                  <span class="material-symbols-outlined text-3xl leading-none text-slate-300">hub</span>
                  <p class="mt-2 text-xs font-medium text-slate-400">暂无关联对象</p>
                </div>
              </section>

              <section class="finance-related-module">
                <div class="mb-4 flex items-center justify-between">
                  <h4 class="flex items-center gap-2 text-sm font-bold text-slate-900">
                    <span :class="relatedSectionIconBoxClass" :style="{ backgroundColor: '#1f1e1c' }">
                      <span class="material-symbols-outlined text-[17px] leading-none">folder</span>
                    </span>
                    附件
                  </h4>
                  <button
                    type="button"
                    class="group/module-action relative flex size-7 items-center justify-center rounded-lg border border-slate-200 bg-white text-slate-400 transition-all hover:border-primary/30 hover:bg-[#efefef] hover:text-primary"
                    aria-label="上传附件"
                  >
                    <span class="material-symbols-outlined text-[18px] leading-none">add</span>
                    <span class="finance-tooltip">上传附件</span>
                  </button>
                </div>
                <div class="rounded-2xl border-2 border-dashed border-slate-200 bg-slate-50/70 py-8 text-center">
                  <span class="material-symbols-outlined text-3xl leading-none text-slate-300">folder_off</span>
                  <p class="mt-2 text-xs font-medium text-slate-400">暂无附件</p>
                </div>
              </section>
            </div>
          </div>
        </div>
      </div>

      <el-drawer v-model="showBasicInfoDrawer" title="基本信息" size="520px" append-to-body>
        <div class="space-y-5">
          <section class="finance-related-card">
            <div class="finance-related-card__header">
              <h4>基础信息</h4>
            </div>
            <div class="grid gap-x-8 gap-y-5 p-5 md:grid-cols-2">
              <div v-for="item in baseItems" :key="item.label" class="min-w-0">
                <p class="text-xs text-slate-400">{{ item.label }}</p>
                <p class="mt-1 break-words text-sm font-medium leading-6 text-slate-700">{{ item.value || '-' }}</p>
              </div>
            </div>
          </section>

          <section class="finance-related-card">
            <div class="finance-related-card__header">
              <h4>自定义字段</h4>
            </div>
            <div v-if="customFieldItems.length > 0" class="grid gap-x-8 gap-y-5 p-5 md:grid-cols-2">
              <div v-for="item in customFieldItems" :key="item.label" class="min-w-0">
                <p class="text-xs text-slate-400">{{ item.label }}</p>
                <p class="mt-1 break-words text-sm font-medium leading-6 text-slate-700">{{ item.value || '-' }}</p>
              </div>
            </div>
            <div v-else class="p-6 text-center text-xs text-slate-400">暂无自定义字段</div>
          </section>

          <section class="finance-related-card">
            <div class="finance-related-card__header">
              <h4>创建信息</h4>
            </div>
            <div class="grid gap-x-8 gap-y-5 p-5 md:grid-cols-2">
              <div>
                <p class="text-xs text-slate-400">创建人</p>
                <p class="mt-1 text-sm font-medium text-slate-700">{{ record.createUserName || record.createUserId || '-' }}</p>
              </div>
              <div>
                <p class="text-xs text-slate-400">创建时间</p>
                <p class="mt-1 text-sm font-medium text-slate-700">{{ record.createTime || '-' }}</p>
              </div>
              <div class="md:col-span-2">
                <p class="text-xs text-slate-400">原始文本</p>
                <p class="mt-1 whitespace-pre-wrap break-words text-sm font-medium leading-6 text-slate-700">{{ record.sourceText || '-' }}</p>
              </div>
            </div>
          </section>
        </div>
      </el-drawer>
    </template>

    <div v-else class="flex-1 flex items-center justify-center text-sm text-slate-400">
      财务记录不存在或已被删除
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getFinanceContractDetail,
  getFinanceExpenseDetail,
  getFinanceInvoiceDetail,
  getFinancePaymentDetail,
  getFinanceReceivableDetail
} from '@/api/finance'
import { getFormFieldsByEntity } from '@/api/customField'
import type { CustomField, EntityType } from '@/types/customField'
import type { FinanceRecordVO } from '@/types/finance'
import { formatCustomFieldValue } from '@/utils/customFieldDisplay'

type FinanceDetailType = 'contract' | 'receivable' | 'payment' | 'invoice' | 'expense'
type ActivityFilter = 'all' | FinanceDetailType

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const record = ref<FinanceRecordVO | null>(null)
const customFields = ref<CustomField[]>([])
const showBasicInfoDrawer = ref(false)
const detailTab = ref<'ai' | 'activity' | 'related'>('ai')
const selectedActivityType = ref<ActivityFilter>('all')

const metas: Record<FinanceDetailType, { label: string; primaryLabel: string; dateLabel: string }> = {
  contract: { label: '合同', primaryLabel: '合同名称', dateLabel: '签约日期' },
  receivable: { label: '应收', primaryLabel: '应收标题', dateLabel: '应收日期' },
  payment: { label: '回款', primaryLabel: '回款记录', dateLabel: '回款日期' },
  invoice: { label: '发票', primaryLabel: '发票抬头', dateLabel: '开票日期' },
  expense: { label: '费用', primaryLabel: '费用类型', dateLabel: '发生日期' }
}

const stageMap: Record<FinanceDetailType, Array<{ value: string; label: string }>> = {
  contract: [
    { value: 'draft', label: '录入' },
    { value: 'active', label: '执行中' },
    { value: 'completed', label: '完成' },
    { value: 'terminated', label: '终止' }
  ],
  receivable: [
    { value: 'pending', label: '待收' },
    { value: 'partial', label: '部分回款' },
    { value: 'paid', label: '已收' },
    { value: 'overdue', label: '逾期' }
  ],
  payment: [
    { value: 'recorded', label: '已记录' },
    { value: 'matched', label: '已匹配' },
    { value: 'confirmed', label: '已确认' }
  ],
  invoice: [
    { value: 'pending', label: '待开票' },
    { value: 'issued', label: '已开票' },
    { value: 'voided', label: '已作废' }
  ],
  expense: [
    { value: 'recorded', label: '已记录' },
    { value: 'approved', label: '已确认' },
    { value: 'reimbursed', label: '已报销' }
  ]
}

const activityFilters: Array<{ value: ActivityFilter; label: string }> = [
  { value: 'all', label: '全部' },
  { value: 'contract', label: '合同' },
  { value: 'receivable', label: '应收' },
  { value: 'payment', label: '回款' },
  { value: 'invoice', label: '发票' },
  { value: 'expense', label: '费用' }
]

const sectionIconBoxClass = 'inline-flex size-6 shrink-0 items-center justify-center rounded-md text-white shadow-[0_6px_14px_rgba(15,23,42,0.08)]'
const sectionMaterialIconClass = 'material-symbols-outlined text-[14px] leading-none'
const relatedSectionIconBoxClass = 'inline-flex size-7 shrink-0 items-center justify-center rounded-lg text-white shadow-sm'

const activeType = computed(() => route.params.type as FinanceDetailType)
const activeId = computed(() => String(route.params.id || ''))
const activeMeta = computed(() => metas[activeType.value] || metas.contract)
const activeEntityType = computed<EntityType>(() => `finance_${activeType.value}` as EntityType)
const title = computed(() => record.value ? primaryText(record.value) : activeMeta.value.label)
const financeStageFlow = computed(() => stageMap[activeType.value] || stageMap.contract)

const currentStageIndex = computed(() => {
  const status = record.value?.status || defaultStatus(activeType.value)
  const index = financeStageFlow.value.findIndex(stage => stage.value === status)
  return index >= 0 ? index : 0
})

const statusBadgeClass = computed(() => {
  const status = record.value?.status
  if (status === 'paid' || status === 'completed' || status === 'reimbursed') return 'bg-emerald-100 text-emerald-700'
  if (status === 'overdue' || status === 'voided' || status === 'terminated') return 'bg-rose-100 text-rose-700'
  if (status === 'partial') return 'bg-amber-100 text-amber-700'
  return 'bg-slate-100 text-slate-600'
})

const relatedObjectCount = computed(() => {
  const row = record.value
  if (!row) return 0
  return [
    row.customerId,
    row.projectId,
    row.contractId && activeType.value !== 'contract',
    row.receivableId && activeType.value !== 'receivable'
  ].filter(Boolean).length
})

const visibleRelatedModuleCount = computed(() => 2)

const baseItems = computed(() => {
  const row = record.value
  if (!row) return []
  const items = [
    { label: activeMeta.value.primaryLabel, value: primaryText(row) },
    { label: '客户', value: row.customerName || row.customerId },
    { label: '项目', value: row.projectName || row.projectId },
    { label: '负责人', value: row.ownerName || row.ownerId },
    { label: '金额', value: money(row.amount) },
    { label: activeMeta.value.dateLabel, value: rowDate(row) },
    { label: '状态', value: statusLabel(row.status) },
    { label: '备注', value: row.remark }
  ]
  if (activeType.value === 'contract') {
    items.splice(1, 0, { label: '合同编号', value: row.contractNo })
    items.push({ label: '开始日期', value: row.startDate }, { label: '结束日期', value: row.endDate })
  }
  if (activeType.value === 'receivable') {
    items.push({ label: '已收金额', value: money(row.receivedAmount) }, { label: '未收金额', value: money(row.unpaidAmount) }, { label: '逾期天数', value: String(row.overdueDays || 0) })
  }
  if (activeType.value === 'payment') {
    items.push({ label: '回款方式', value: row.paymentMethod })
  }
  if (activeType.value === 'invoice') {
    items.splice(1, 0, { label: '发票号码', value: row.invoiceNo }, { label: '税号', value: row.taxNo })
  }
  return items
})

const customFieldItems = computed(() => customFields.value.map(field => ({
  label: field.fieldLabel,
  value: formatCustomFieldValue(field, record.value?.customFields?.[field.fieldName])
})))

const insightMetrics = computed(() => {
  const row = record.value
  return [
    { label: '金额', value: money(row?.amount) },
    { label: '当前状态', value: statusLabel(row?.status) },
    { label: '业务日期', value: rowDate(row) || '-' },
    { label: '来源', value: row?.aiCreated ? 'AI 写入' : '手动录入' }
  ]
})

const aiInsightTitle = computed(() => {
  if (record.value?.aiCreated) return '这条记录由 AI 写入，已保留来源文本'
  return '这条记录当前为手动录入，可继续由 AI 补充分析'
})

const aiInsightDescription = computed(() => {
  const row = record.value
  if (!row) return '暂无分析'
  const customer = row.customerName ? `客户 ${row.customerName}` : '未关联客户'
  const amount = `金额 ${money(row.amount)}`
  return `${customer}，${amount}，状态为${statusLabel(row.status)}。后续可在财务对话中继续查询收支、应收和开票情况。`
})

onMounted(() => {
  void loadDetail()
})

watch(() => [activeType.value, activeId.value], () => {
  void loadDetail()
})

async function loadDetail() {
  loading.value = true
  try {
    const [detail, fields] = await Promise.all([
      fetchDetail(activeType.value, activeId.value),
      getFormFieldsByEntity(activeEntityType.value)
    ])
    record.value = detail
    customFields.value = fields
  } finally {
    loading.value = false
  }
}

function fetchDetail(type: FinanceDetailType, id: string) {
  switch (type) {
    case 'contract': return getFinanceContractDetail(id)
    case 'receivable': return getFinanceReceivableDetail(id)
    case 'payment': return getFinancePaymentDetail(id)
    case 'invoice': return getFinanceInvoiceDetail(id)
    case 'expense': return getFinanceExpenseDetail(id)
  }
}

function goBack() {
  void router.push({ path: '/finance', query: { view: 'list', tab: activeType.value } })
}

function handleOpenFinanceList() {
  goBack()
}

function goCustomer(customerId?: string) {
  if (customerId) void router.push(`/customer/${customerId}`)
}

function goProject(projectId?: string) {
  if (projectId) void router.push({ name: 'ProjectDetail', params: { id: projectId } })
}

function goFinanceDetail(type: FinanceDetailType, id?: string) {
  if (id) void router.push({ name: 'FinanceDetail', params: { type, id } })
}

function handleAiInput() {
  void router.push({ path: '/chat', query: { appCode: 'finance' } })
}

function handleRefreshAiAnalysis() {
  ElMessage.info('AI 财务分析会根据当前正式记录生成')
}

function primaryText(row: FinanceRecordVO) {
  return row.contractName || row.title || row.expenseType || row.invoiceNo || row.paymentMethod || row.remark || '财务记录'
}

function rowDate(row?: FinanceRecordVO | null) {
  return row?.signDate || row?.dueDate || row?.paymentDate || row?.invoiceDate || row?.expenseDate || ''
}

function money(value: unknown) {
  const amount = Number(value || 0)
  return amount.toLocaleString('zh-CN', { style: 'currency', currency: 'CNY' })
}

function statusLabel(status?: string) {
  const map: Record<string, string> = {
    active: '执行中',
    completed: '完成',
    terminated: '终止',
    pending: '待收',
    partial: '部分回款',
    paid: '已收',
    overdue: '逾期',
    issued: '已开票',
    voided: '已作废',
    recorded: '已记录',
    matched: '已匹配',
    confirmed: '已确认',
    approved: '已确认',
    reimbursed: '已报销'
  }
  return status ? map[status] || status : '-'
}

function defaultStatus(type: FinanceDetailType) {
  const map: Record<FinanceDetailType, string> = {
    contract: 'active',
    receivable: 'pending',
    payment: 'recorded',
    invoice: 'issued',
    expense: 'recorded'
  }
  return map[type]
}

function getStepperSegmentBgClass(idx: number) {
  if (idx < currentStageIndex.value) return 'bg-primary/70'
  if (idx === currentStageIndex.value) return 'bg-primary'
  return 'bg-slate-100'
}

function getStepperLabelClass(idx: number) {
  return idx <= currentStageIndex.value ? 'text-white' : 'text-slate-400'
}

function getStepperClipPath(idx: number) {
  if (financeStageFlow.value.length === 1) return 'inset(0 round 8px)'
  if (idx === 0) return 'polygon(0 0, calc(100% - 14px) 0, 100% 50%, calc(100% - 14px) 100%, 0 100%)'
  if (idx === financeStageFlow.value.length - 1) return 'polygon(0 0, 100% 0, 100% 100%, 0 100%, 14px 50%)'
  return 'polygon(0 0, calc(100% - 14px) 0, 100% 50%, calc(100% - 14px) 100%, 0 100%, 14px 50%)'
}

function getSectionIconStyle(key: 'ai' | 'activity' | 'related'): { backgroundColor: string } {
  const map = {
    ai: '#1f1e1c',
    activity: '#5f704a',
    related: '#8d4f34'
  }
  return { backgroundColor: map[key] }
}
</script>

<style scoped>
.wk-customer-stage-scroll {
  padding-bottom: 2px;
}

.wk-customer-stage-scroll::-webkit-scrollbar {
  height: 0;
}

.finance-related-card {
  overflow: hidden;
  border: 1px solid rgb(226 232 240);
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 1px 2px rgb(15 23 42 / 0.04);
}

.finance-related-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid rgb(241 245 249);
  background: rgb(248 250 252 / 0.8);
  padding: 12px 20px;
}

.finance-related-card__header h4 {
  font-size: 12px;
  font-weight: 700;
  color: rgb(51 65 85);
}

.finance-related-module {
  border-radius: 16px;
  border: 1px solid rgb(226 232 240);
  background: #fff;
  padding: 16px;
  box-shadow: 0 1px 2px rgb(15 23 42 / 0.04);
}

.finance-object-card {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
  width: 100%;
  overflow: hidden;
  border-radius: 16px;
  border: 1px solid rgb(226 232 240);
  background: linear-gradient(135deg, #fff 0%, #fff 58%, rgb(248 250 252 / 0.8) 100%);
  padding: 14px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.finance-object-card:hover {
  transform: translateY(-2px);
  border-color: color-mix(in srgb, var(--wk-primary) 30%, rgb(226 232 240));
  box-shadow: 0 10px 20px rgb(226 232 240 / 0.7);
}

.finance-tooltip {
  pointer-events: none;
  position: absolute;
  right: 100%;
  top: 50%;
  z-index: 200;
  margin-right: 8px;
  transform: translateY(-50%);
  white-space: nowrap;
  border-radius: 8px;
  background: #000;
  padding: 6px 10px;
  font-size: 13px;
  font-weight: 500;
  color: #fff;
  opacity: 0;
  box-shadow: 0 8px 18px rgb(15 23 42 / 0.16);
  transition: opacity 0.15s ease;
}

.group\/module-action:hover .finance-tooltip {
  opacity: 1;
}
</style>
