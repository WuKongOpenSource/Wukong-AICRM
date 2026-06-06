<template>
  <section v-if="visible" :class="sectionClasses">
    <div class="mb-4 flex items-center justify-between">
      <h4 class="flex items-center gap-2 text-sm font-bold text-slate-900">
        <span :class="sectionIconBoxClass" :style="{ backgroundColor: '#8d4f34' }">
          <span class="material-symbols-outlined text-[17px] leading-none">event_note</span>
        </span>
        日程
        <button
          v-if="showToggle"
          type="button"
          class="group/module-action relative inline-flex size-7 shrink-0 items-center justify-center rounded-lg bg-white text-slate-500 transition-[background-color,color,border-color] hover:bg-[#efefef] hover:text-[#0d0d0d]"
          :aria-expanded="expanded"
          :aria-label="expanded ? '收起日程' : '展开日程'"
          @click="emit('update:expanded', !expanded)"
        >
          <span class="material-symbols-outlined text-[16px] leading-none">
            {{ expanded ? 'keyboard_arrow_down' : 'keyboard_arrow_right' }}
          </span>
          <span
            class="pointer-events-none absolute left-1/2 top-full z-[200] mt-2 -translate-x-1/2 whitespace-nowrap rounded-lg bg-black px-3 py-1.5 text-[13px] font-medium text-white opacity-0 shadow-md transition-opacity duration-150 group-hover/module-action:opacity-100"
            role="tooltip"
          >
            {{ expanded ? '收起日程' : '展开日程' }}
          </span>
        </button>
      </h4>
      <div class="flex shrink-0 items-center gap-2">
        <button
          v-if="canCreate"
          type="button"
          class="group/module-action relative flex size-7 items-center justify-center rounded-lg border border-slate-200 bg-white text-slate-400 transition-all hover:border-primary/30 hover:bg-[#efefef] hover:text-primary"
          aria-label="新建日程"
          @click="emit('add')"
        >
          <span class="material-symbols-outlined wk-plus-button-icon wk-plus-button-icon--compact">add</span>
          <span
            class="pointer-events-none absolute right-full top-1/2 z-[200] mr-2 -translate-y-1/2 whitespace-nowrap rounded-lg bg-black px-3 py-1.5 text-[13px] font-medium text-white opacity-0 shadow-md transition-opacity duration-150 group-hover/module-action:opacity-100"
            role="tooltip"
          >
            新建日程
          </span>
        </button>
      </div>
    </div>

    <div v-if="isModuleVisible" class="space-y-4">
      <div v-if="loading" :class="listClass">
        <div
          v-for="index in 3"
          :key="`schedule-skeleton-${index}`"
          class="relative rounded-xl border border-slate-200 bg-white p-3"
        >
          <div v-if="!embeddedLayout" class="absolute -left-[27px] top-4 size-3 animate-pulse rounded-full border-2 border-slate-200 bg-white" />
          <div class="flex items-start justify-between gap-3">
            <div class="min-w-0 flex-1 space-y-2">
              <div class="h-4 w-2/3 animate-pulse rounded-full bg-slate-100" />
              <div class="flex flex-wrap items-center gap-2">
                <div class="h-5 w-16 animate-pulse rounded-full bg-slate-100" />
                <div class="h-5 w-20 animate-pulse rounded-full bg-slate-100" />
              </div>
              <div class="h-3 w-4/5 animate-pulse rounded-full bg-slate-100" />
            </div>
            <div class="h-8 w-24 shrink-0 animate-pulse rounded-lg bg-slate-100" />
          </div>
        </div>
      </div>
      <RelatedEmptyState v-else-if="schedules.length === 0" icon="event_busy" text="暂无关联日程" />
      <div v-else :class="listClass">
        <div
          v-for="schedule in schedules"
          :key="schedule.scheduleId"
          class="group relative rounded-xl border border-slate-200 bg-white p-3 transition-all hover:shadow-md"
          :class="[
            clickable ? 'cursor-pointer' : '',
            selectedScheduleId && String(schedule.scheduleId) === String(selectedScheduleId) ? 'border-primary ring-1 ring-primary/20' : ''
          ]"
          @click="emit('view', schedule)"
        >
          <div v-if="!embeddedLayout" class="absolute -left-[27px] top-4 size-3 rounded-full border-2 border-primary bg-white" />
          <div class="flex items-start justify-between gap-3">
            <div class="min-w-0 flex-1">
              <h5 class="mb-1 truncate text-sm font-bold text-slate-900 transition-colors group-hover:text-primary" :title="schedule.title">
                {{ schedule.title || '-' }}
              </h5>
              <div class="flex flex-wrap items-center gap-2">
                <span v-if="schedule.customerName" class="text-xs font-medium text-slate-500">{{ schedule.customerName }}</span>
                <span class="rounded-full bg-primary/10 px-2 py-0.5 text-xs font-bold text-primary">
                  {{ getScheduleTypeLabel(schedule) }}
                </span>
                <span v-if="schedule.location" class="rounded-full bg-slate-50 px-2 py-0.5 text-xs font-bold text-slate-600">
                  {{ schedule.location }}
                </span>
              </div>
              <p v-if="getScheduleListSummary(schedule)" class="mt-2 line-clamp-2 text-xs leading-relaxed text-slate-500">
                {{ getScheduleListSummary(schedule) }}
              </p>
            </div>
            <div class="shrink-0 text-right">
              <span class="inline-block max-w-[9rem] whitespace-normal rounded-lg bg-slate-50 px-2 py-1 text-xs font-bold leading-4 text-slate-600">
                {{ formatScheduleDateTime(schedule.startTime) }}
                <template v-if="schedule.endTime"> - {{ formatScheduleEndTime(schedule.startTime, schedule.endTime) }}</template>
              </span>
            </div>
          </div>
        </div>
      </div>
      <div v-if="total > pageSize" class="flex justify-center pt-2">
        <el-pagination
          :current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          small
          @current-change="handlePageChange"
        />
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ScheduleVO } from '@/api/schedule'
import RelatedEmptyState from './RelatedEmptyState.vue'

const props = withDefaults(defineProps<{
  schedules?: ScheduleVO[]
  loading?: boolean
  visible?: boolean
  embeddedLayout?: boolean
  expanded?: boolean
  canCreate?: boolean
  clickable?: boolean
  selectedScheduleId?: string | number | null
  total?: number
  page?: number
  pageSize?: number
}>(), {
  schedules: () => [],
  loading: false,
  visible: true,
  embeddedLayout: true,
  expanded: true,
  canCreate: true,
  clickable: false,
  selectedScheduleId: null,
  total: 0,
  page: 1,
  pageSize: 100
})

const emit = defineEmits<{
  (e: 'update:expanded', value: boolean): void
  (e: 'update:page', value: number): void
  (e: 'add'): void
  (e: 'view', schedule: ScheduleVO): void
}>()

const sectionIconBoxClass = 'inline-flex size-7 shrink-0 items-center justify-center rounded-lg text-white shadow-sm'
const showToggle = computed(() => props.loading || props.schedules.length > 0)
const isModuleVisible = computed(() => props.expanded || !showToggle.value)
const sectionClasses = computed(() => [
  'group/schedules-module',
  props.embeddedLayout
    ? 'mt-5 border-t border-slate-100 pt-5'
    : 'rounded-2xl border border-slate-200 bg-white p-4 shadow-sm'
])
const listClass = computed(() => props.embeddedLayout ? 'space-y-3' : 'ml-3 space-y-3 border-l-2 border-slate-100 pl-5')

function handlePageChange(value: number) {
  emit('update:page', value)
}

function getScheduleTypeLabel(schedule: ScheduleVO) {
  if (schedule.typeName) return schedule.typeName
  const labels: Record<string, string> = {
    meeting: '会议',
    call: '电话',
    visit: '拜访',
    other: '其他'
  }
  return labels[String(schedule.type || '').toLowerCase()] || '日程'
}

function getScheduleParticipantsLine(schedule: ScheduleVO) {
  if (schedule.participantUsers?.length) {
    return schedule.participantUsers
      .map(user => (user.realname || user.username || '').trim())
      .filter(Boolean)
      .join('、')
  }
  return (schedule.participantNames || '').trim()
}

function getScheduleListSummary(schedule: ScheduleVO) {
  const participants = getScheduleParticipantsLine(schedule)
  if (participants) return `参与人：${participants}`
  return schedule.description || ''
}

function formatScheduleDateTime(dateStr?: string) {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  if (Number.isNaN(date.getTime())) return dateStr
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

function formatScheduleTime(dateStr?: string) {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  if (Number.isNaN(date.getTime())) return '-'
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

function formatScheduleEndTime(startDateStr?: string, endDateStr?: string) {
  if (!endDateStr) return ''
  if (!startDateStr) return formatScheduleDateTime(endDateStr)

  const startDate = new Date(startDateStr)
  const endDate = new Date(endDateStr)
  if (Number.isNaN(startDate.getTime()) || Number.isNaN(endDate.getTime())) {
    return formatScheduleDateTime(endDateStr)
  }

  const sameDay = startDate.getFullYear() === endDate.getFullYear()
    && startDate.getMonth() === endDate.getMonth()
    && startDate.getDate() === endDate.getDate()
  return sameDay ? formatScheduleTime(endDateStr) : formatScheduleDateTime(endDateStr)
}
</script>
