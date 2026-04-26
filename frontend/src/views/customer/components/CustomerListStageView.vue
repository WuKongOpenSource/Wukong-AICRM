<template>
  <div
    class="wk-stage-board-wrap min-h-0 min-w-0 flex-1 overflow-hidden bg-[#F4F5F7] px-1 py-2 sm:px-2"
  >
    <div
      class="wk-stage-board flex items-stretch gap-4 overflow-x-auto overflow-y-hidden pb-2 sm:gap-4"
      :style="{ height: `${bodyHeight}px`, minHeight: '200px' }"
    >
      <div
        v-for="col in KANBAN_STAGE_COLUMNS"
        :key="col.id"
        class="flex h-full min-h-0 min-w-[280px] max-w-[320px] shrink-0 flex-col overflow-hidden rounded-xl p-2 transition-[box-shadow,background-color] duration-150"
        :class="[col.laneClass, columnDropHighlightClass(col)]"
        @dragover.prevent="onColumnDragOver($event, col.id)"
        @drop.prevent="onColumnDrop($event, col.id)"
      >
        <div
          class="mb-3 flex shrink-0 items-center justify-between px-2"
          @dragover.prevent="onColumnDragOver($event, col.id)"
        >
          <div class="flex min-w-0 items-center gap-2">
            <span
              class="truncate text-[11px] font-bold uppercase tracking-widest"
              :class="col.titleClass"
            >
              {{ col.label }}
            </span>
            <span
              class="inline-flex size-5 shrink-0 items-center justify-center rounded-full text-[9px] font-bold leading-none tabular-nums"
              :class="col.countBadgeClass"
            >
              {{ columnCount(col.id) }}
            </span>
          </div>
          <button
            type="button"
            class="flex size-7 shrink-0 items-center justify-center rounded-lg text-slate-400 transition-colors hover:bg-black/[0.04] hover:text-slate-600"
            title="在此阶段新建客户"
            @click="emit('createInStage', col.id)"
          >
            <span class="material-symbols-outlined text-[18px]">add</span>
          </button>
        </div>
        <div
          class="wk-stage-column-scroll flex min-h-0 flex-1 flex-col space-y-3 overflow-y-auto overflow-x-hidden pr-1"
          @dragover.prevent="onColumnDragOver($event, col.id)"
        >
          <div
            v-for="row in rowsInStage(col.id)"
            :key="row.customerId"
            :draggable="canChangeStage"
            class="group relative cursor-pointer rounded-xl border border-[#DFE1E6] bg-white p-3 shadow-sm transition-all duration-150 hover:border-primary"
            :class="[
              canChangeStage ? 'cursor-grab active:cursor-grabbing' : '',
              draggingCustomerId === row.customerId ? cardDraggingClass : '',
              cardHotClass(row)
            ]"
            @dragstart="onCardDragStart($event, row)"
            @dragend="onCardDragEnd"
            @dragover.prevent="onColumnDragOver($event, col.id)"
            @click="emit('rowClick', row)"
          >
            <div class="mb-1 flex items-start gap-2">
              <div
                class="mt-0.5 flex size-8 shrink-0 items-center justify-center overflow-hidden rounded-md border border-slate-100 bg-white"
              >
                <img
                  v-if="row.logoUrl"
                  :src="row.logoUrl"
                  alt=""
                  class="size-full object-contain p-0.5"
                />
                <span v-else class="text-xs font-bold text-slate-400">{{ row.companyName?.charAt(0) || '?' }}</span>
              </div>
              <div class="min-w-0 flex-1">
                <h4 class="truncate text-sm font-semibold leading-tight text-[#051a3e] transition-colors group-hover:text-primary">
                  {{ row.companyName || '-' }}
                </h4>
                <p class="mt-0.5 text-[10px] font-medium text-slate-400">
                  {{ industryLabel(row) || '通用行业' }}
                </p>
              </div>
            </div>
            <div class="mb-3 flex items-center justify-between gap-2">
              <span class="text-sm font-bold text-primary">{{ formatCardQuotation(row.quotation) }}</span>
              <span
                v-if="getAiStatusMeta(row.aiStatusDetection)"
                class="inline-flex max-w-[104px] shrink-0 truncate rounded-lg border px-1.5 py-0.5 text-[10px] font-bold"
                :class="aiBadgeTemplateClass(row.aiStatusDetection)"
              >
                {{ getAiStatusMeta(row.aiStatusDetection)?.label }}
              </span>
            </div>
            <div class="mb-4 space-y-1.5">
              <div class="flex items-center gap-2 text-[11px] text-slate-500">
                <span class="material-symbols-outlined shrink-0 text-[14px] text-slate-400">schedule</span>
                <span :class="lastFollowUpHighlightClass(row.lastContactTime)">
                  最近跟进: {{ formatLastContactDate(row.lastContactTime) }}
                </span>
              </div>
              <div class="flex min-w-0 items-center gap-2 text-[11px] text-slate-500">
                <span class="material-symbols-outlined shrink-0 text-[14px] text-slate-400">person</span>
                <span class="min-w-0 truncate font-medium text-slate-600">
                  联系人: {{ row.primaryContactName || '-' }}
                  <template v-if="row.primaryContactPhone">· {{ row.primaryContactPhone }}</template>
                </span>
              </div>
            </div>
            <div class="flex items-center justify-between border-t border-slate-100 pt-3" data-row-action="true" @click.stop>
              <div class="flex min-w-0 items-center gap-2">
                <div
                  class="flex size-5 shrink-0 items-center justify-center overflow-hidden rounded-full border border-slate-200 bg-slate-50 text-[10px] font-bold text-slate-500"
                >
                  {{ row.ownerName?.charAt(0) || '?' }}
                </div>
                <span class="truncate text-[11px] font-medium text-slate-600">{{ row.ownerName || '-' }}</span>
              </div>
              <button
                type="button"
                class="flex items-center gap-1 rounded-lg border border-primary/20 px-2 py-1 text-primary transition-all hover:bg-primary/5"
                @click="emit('aiFollowUp', row)"
              >
                <span class="material-symbols-outlined text-[14px]">auto_awesome</span>
                <span class="text-[10px] font-bold uppercase tracking-wider">AI 跟进</span>
              </button>
            </div>
          </div>

          <div
            v-if="!canChangeStage && rowsInStage(col.id).length === 0"
            class="flex flex-col items-center justify-center rounded-xl border border-dashed border-slate-200 bg-white/40 py-12"
          >
            <span class="material-symbols-outlined mb-2 text-3xl text-slate-200">inbox</span>
            <p class="text-[11px] text-slate-400">暂无客户</p>
          </div>

          <div
            v-if="canChangeStage"
            class="group flex flex-col items-center justify-center gap-1 rounded-lg border border-dashed py-3 transition-colors duration-150"
            :class="dropZoneClassList(col)"
            @dragover.prevent="onColumnDragOver($event, col.id)"
          >
            <span class="material-symbols-outlined text-[20px] transition-colors" :class="dropZoneIconClass(col)">
              {{ dropZoneIcon(col) }}
            </span>
            <span class="text-[9px] font-medium transition-colors" :class="dropZoneHintClass(col)">
              {{ dropTargetStage === col.id ? '松开即可放入该阶段' : '拖拽客户至此阶段' }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, shallowRef } from 'vue'
import type { CustomerListVO, CustomerStage } from '@/types/customer'
import { getCustomerAiStatusMeta } from '@/utils/customerAi'
import {
  KANBAN_STAGE_COLUMNS,
  customersInStage,
  formatCardQuotation,
  formatLastContactDate,
  lastFollowUpHighlightClass,
  normalizeListStage,
  type KanbanDropTone
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

const draggingCustomerId = ref<string | null>(null)
const dropTargetStage = shallowRef<CustomerStage | null>(null)

/** 拖拽时留在原位的卡片：不用 ring-offset + ring-primary，否则会出现双层浅蓝线框。勿加 pointer-events-none，会破坏原生 drag。 */
const cardDraggingClass =
  'opacity-[0.5] scale-[0.99] shadow-none ring-1 ring-inset ring-slate-300/70 bg-slate-50/50'

function rowsInStage(stage: CustomerStage) {
  return customersInStage(props.customers, stage)
}

function columnCount(stage: CustomerStage) {
  return rowsInStage(stage).length
}

function getAiStatusMeta(value: string | undefined | null) {
  return getCustomerAiStatusMeta(value)
}

/** 贴近 1.html 卡片上的状态胶囊：蓝底高亮 / 灰底次要 */
function aiBadgeTemplateClass(ai: string | undefined | null) {
  const meta = getCustomerAiStatusMeta(ai)
  if (!meta) return 'border-slate-200 bg-slate-100 text-slate-500'
  const label = meta.label
  if (label === '活跃状态' || label === '高意向') {
    return 'border-primary/10 bg-blue-50 text-primary'
  }
  return `${meta.badgeClass} border-slate-200/80`
}

function cardHotClass(row: CustomerListVO) {
  const meta = getCustomerAiStatusMeta(row.aiStatusDetection)
  if (meta?.label === '高意向' || meta?.label === '活跃状态') {
    return 'border-l-4 border-l-primary'
  }
  return ''
}

function dropZoneIcon(col: (typeof KANBAN_STAGE_COLUMNS)[number]) {
  if (col.dropTone === 'success') return 'check_circle'
  if (col.dropTone === 'danger') return 'cancel'
  return 'move_to_inbox'
}

function isDropTarget(stage: CustomerStage) {
  return props.canChangeStage && dropTargetStage.value === stage
}

function columnDropHighlightClass(col: (typeof KANBAN_STAGE_COLUMNS)[number]) {
  if (!isDropTarget(col.id)) return ''
  if (col.dropTone === 'success') return 'ring-2 ring-inset ring-green-400/45 shadow-[inset_0_0_0_1px_rgba(74,222,128,0.35)]'
  if (col.dropTone === 'danger') return 'ring-2 ring-inset ring-red-400/45 shadow-[inset_0_0_0_1px_rgba(248,113,113,0.35)]'
  return 'ring-2 ring-inset ring-primary/30 shadow-[inset_0_0_0_1px_rgba(19,127,236,0.25)]'
}

function dropZoneClassList(col: (typeof KANBAN_STAGE_COLUMNS)[number]) {
  const active = isDropTarget(col.id)
  const base: Record<KanbanDropTone, string> = {
    neutral: active
      ? 'border-primary/50 bg-primary/[0.07]'
      : 'border-slate-200 hover:border-primary/40',
    success: active ? 'border-green-400 bg-green-50/80' : 'border-green-100 hover:border-green-300',
    danger: active ? 'border-red-400 bg-red-50/80' : 'border-red-100 hover:border-red-300'
  }
  return base[col.dropTone]
}

function dropZoneIconClass(col: (typeof KANBAN_STAGE_COLUMNS)[number]) {
  const active = isDropTarget(col.id)
  if (col.dropTone === 'success') {
    return active ? 'text-green-600' : 'text-green-200 group-hover:text-green-400'
  }
  if (col.dropTone === 'danger') {
    return active ? 'text-red-600' : 'text-red-200 group-hover:text-red-400'
  }
  return active ? 'text-primary' : 'text-slate-300'
}

function dropZoneHintClass(col: (typeof KANBAN_STAGE_COLUMNS)[number]) {
  const active = isDropTarget(col.id)
  if (col.dropTone === 'success') {
    return active ? 'text-green-700' : 'text-green-200'
  }
  if (col.dropTone === 'danger') {
    return active ? 'text-red-700' : 'text-red-200'
  }
  return active ? 'text-primary/90' : 'text-slate-300'
}

function clearDragUi() {
  draggingCustomerId.value = null
  dropTargetStage.value = null
}

function onColumnDragOver(e: DragEvent, stage: CustomerStage) {
  if (!props.canChangeStage) return
  e.preventDefault()
  if (e.dataTransfer) e.dataTransfer.dropEffect = 'move'
  dropTargetStage.value = stage
}

function onCardDragStart(e: DragEvent, row: CustomerListVO) {
  if (!props.canChangeStage) {
    e.preventDefault()
    return
  }
  e.dataTransfer?.setData('customerId', row.customerId)
  e.dataTransfer?.setData('text/plain', row.customerId)
  if (e.dataTransfer) e.dataTransfer.effectAllowed = 'move'
  draggingCustomerId.value = row.customerId
}

function onCardDragEnd() {
  clearDragUi()
}

function onColumnDrop(e: DragEvent, stage: CustomerStage) {
  if (!props.canChangeStage) return
  const id = e.dataTransfer?.getData('customerId') || e.dataTransfer?.getData('text/plain')
  clearDragUi()
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

.wk-stage-board {
  scrollbar-width: none;
}

.wk-stage-board::-webkit-scrollbar {
  display: none;
}

.wk-stage-column-scroll {
  -webkit-overflow-scrolling: touch;
  scrollbar-width: thin;
}

.wk-stage-column-scroll::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

.wk-stage-column-scroll::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgb(148 163 184 / 0.45);
}
</style>
