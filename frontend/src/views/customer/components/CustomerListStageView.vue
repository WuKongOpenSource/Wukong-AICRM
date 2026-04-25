<template>
  <div class="wk-stage-board-wrap min-h-0 min-w-0 flex-1 overflow-hidden px-1 py-2 sm:px-2">
    <div
      class="wk-stage-board flex items-stretch gap-4 overflow-x-auto overflow-y-hidden sm:gap-6"
      :style="{ height: `${bodyHeight}px`, minHeight: '200px' }"
    >
      <div
        v-for="col in KANBAN_STAGE_COLUMNS"
        :key="col.id"
        class="flex h-full min-h-0 w-72 shrink-0 flex-col gap-3"
        @dragover.prevent
        @drop.prevent="onColumnDrop($event, col.id)"
      >
        <div class="z-10 flex shrink-0 flex-col gap-1 rounded-xl border px-3 py-1.5" :class="col.headerClass">
          <div class="flex items-center justify-between gap-1">
            <div class="flex min-w-0 items-center gap-1.5">
              <div class="size-1.5 shrink-0 rounded-full" :class="col.dotClass" />
              <span class="truncate text-xs font-bold tracking-tight text-slate-800">{{ col.label }}</span>
              <span
                class="shrink-0 rounded-full border border-slate-200/80 bg-white/60 px-1.5 py-0.5 text-[9px] font-bold text-slate-600"
              >
                {{ columnCount(col.id) }}
              </span>
            </div>
            <button
              type="button"
              class="flex size-6 shrink-0 items-center justify-center rounded-lg text-slate-600 transition-colors hover:bg-white/60"
              title="在此阶段新建客户"
              @click="emit('createInStage', col.id)"
            >
              <span class="material-symbols-outlined text-sm">add</span>
            </button>
          </div>
        </div>
        <div class="wk-stage-column-scroll min-h-0 flex-1 space-y-3 overflow-y-auto overflow-x-hidden pr-0.5">
          <div
            v-for="row in rowsInStage(col.id)"
            :key="row.customerId"
            :draggable="canChangeStage"
            class="group relative cursor-pointer overflow-hidden rounded-2xl border border-slate-200 bg-white p-4 transition-all hover:-translate-y-0.5 hover:border-primary/50"
            :class="canChangeStage ? 'cursor-grab active:cursor-grabbing' : ''"
            @dragstart="onCardDragStart($event, row)"
            @click="emit('rowClick', row)"
          >
            <div class="mb-3 flex items-start justify-between gap-2">
              <div class="flex min-w-0 items-center gap-2.5">
                <div class="flex size-8 shrink-0 items-center justify-center overflow-hidden rounded-lg border border-slate-100 bg-slate-50">
                  <img
                    v-if="row.logoUrl"
                    :src="row.logoUrl"
                    alt=""
                    class="size-full object-contain p-1"
                  />
                  <span v-else class="text-xs font-bold text-slate-400">{{ row.companyName?.charAt(0) || '?' }}</span>
                </div>
                <div class="min-w-0">
                  <h4 class="max-w-[140px] truncate text-sm font-bold text-slate-900 transition-colors group-hover:text-primary">
                    {{ row.companyName || '-' }}
                  </h4>
                  <span class="mt-0.5 block text-[10px] text-slate-400">{{ industryLabel(row) || '通用行业' }}</span>
                </div>
              </div>
              <div v-if="getAiStatusMeta(row.aiStatusDetection)" class="shrink-0">
                <span
                  class="inline-flex max-w-[100px] items-center truncate rounded-full px-2 py-0.5 text-[10px] font-medium"
                  :class="getAiStatusMeta(row.aiStatusDetection)?.badgeClass"
                >
                  {{ getAiStatusMeta(row.aiStatusDetection)?.label }}
                </span>
              </div>
            </div>
            <div class="space-y-1.5 text-[11px]">
              <div class="flex items-center gap-1.5">
                <span class="whitespace-nowrap text-slate-400">预计价值</span>
                <span class="font-bold text-slate-900">{{ formatCardQuotation(row.quotation) }}</span>
              </div>
              <div class="flex min-w-0 items-center gap-1.5">
                <span class="shrink-0 whitespace-nowrap text-slate-400">联系人</span>
                <span class="truncate font-medium text-slate-700">
                  {{ row.primaryContactName || '-' }}
                  <span v-if="row.primaryContactPhone" class="ml-1 font-medium">· {{ row.primaryContactPhone }}</span>
                </span>
              </div>
              <div class="flex items-center gap-1.5">
                <span class="whitespace-nowrap text-slate-400">最后跟进</span>
                <span :class="lastFollowUpHighlightClass(row.lastContactTime)">
                  {{ formatLastContactDate(row.lastContactTime) }}
                </span>
              </div>
            </div>
            <div class="mt-4 flex items-center justify-between border-t border-slate-50 pt-3" data-row-action="true" @click.stop>
              <div class="flex items-center gap-2">
                <div
                  class="flex size-6 items-center justify-center rounded-full border border-white bg-slate-100 text-[10px] font-bold text-slate-500 shadow-sm"
                >
                  {{ row.ownerName?.charAt(0) || '?' }}
                </div>
                <span class="text-[10px] font-medium text-slate-600">{{ row.ownerName || '-' }}</span>
              </div>
              <button
                type="button"
                class="flex h-7 items-center gap-1.5 rounded-lg bg-primary/5 px-2 text-primary transition-all hover:bg-primary/10 active:scale-95"
                @click="emit('aiFollowUp', row)"
              >
                <span class="material-symbols-outlined text-[14px]">auto_awesome</span>
                <span class="text-[10px] font-bold">AI 跟进</span>
              </button>
            </div>
          </div>
          <div
            v-if="rowsInStage(col.id).length === 0"
            class="rounded-2xl border-2 border-dashed border-slate-100 bg-slate-50/30 py-12 text-center"
          >
            <span class="material-symbols-outlined mb-2 block text-3xl text-slate-200">drag_indicator</span>
            <p class="text-[11px] text-slate-400">
              {{ canChangeStage ? '拖拽客户至此阶段' : '暂无客户' }}
            </p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { CustomerListVO, CustomerStage } from '@/types/customer'
import { getCustomerAiStatusMeta } from '@/utils/customerAi'
import {
  KANBAN_STAGE_COLUMNS,
  customersInStage,
  formatCardQuotation,
  formatLastContactDate,
  lastFollowUpHighlightClass,
  normalizeListStage
} from '@/utils/customerListViewUi'

const props = defineProps<{
  customers: CustomerListVO[]
  bodyHeight: number
  canChangeStage: boolean
  industryLabel: (row: CustomerListVO) => string
}>()

const emit = defineEmits<{
  rowClick: [row: CustomerListVO]
  aiFollowUp: [row: CustomerListVO]
  createInStage: [stage: CustomerStage]
  stageDropped: [payload: { customerId: string; stage: CustomerStage }]
}>()

function rowsInStage(stage: CustomerStage) {
  return customersInStage(props.customers, stage)
}

function columnCount(stage: CustomerStage) {
  return rowsInStage(stage).length
}

function getAiStatusMeta(value: string | undefined | null) {
  return getCustomerAiStatusMeta(value)
}

function onCardDragStart(e: DragEvent, row: CustomerListVO) {
  if (!props.canChangeStage) {
    e.preventDefault()
    return
  }
  e.dataTransfer?.setData('customerId', row.customerId)
  e.dataTransfer?.setData('text/plain', row.customerId)
  if (e.dataTransfer) e.dataTransfer.effectAllowed = 'move'
}

function onColumnDrop(e: DragEvent, stage: CustomerStage) {
  if (!props.canChangeStage) return
  const id = e.dataTransfer?.getData('customerId') || e.dataTransfer?.getData('text/plain')
  if (!id?.trim()) return
  const row = props.customers.find(c => c.customerId === id)
  if (!row) return
  if (normalizeListStage(row.stage) === stage) return
  emit('stageDropped', { customerId: id, stage })
}
</script>

<style scoped>
.wk-stage-board-wrap {
  min-width: 0;
}

.wk-stage-column-scroll {
  -webkit-overflow-scrolling: touch;
  scrollbar-width: thin;
}

.wk-stage-column-scroll::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.wk-stage-column-scroll::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgb(148 163 184 / 0.55);
}
</style>
