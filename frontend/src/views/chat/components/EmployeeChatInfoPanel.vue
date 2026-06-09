<template>
  <div class="wk-object-detail-embedded h-full overflow-y-auto bg-[var(--wk-bg-surface)] px-4 py-4">
    <section class="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <div class="flex items-start gap-3">
        <img v-if="employee.imgUrl" :src="employee.imgUrl" class="size-12 shrink-0 rounded-full object-cover" alt="avatar" />
        <div v-else class="flex size-12 shrink-0 items-center justify-center rounded-full bg-slate-900 text-base font-bold text-white">
          {{ employeeInitial }}
        </div>
        <div class="min-w-0 flex-1">
          <h3 class="truncate text-base font-bold text-slate-900">{{ employeeName }}</h3>
          <p class="mt-1 text-sm text-slate-500">{{ employee.post || '员工' }}</p>
        </div>
      </div>

      <div class="mt-4 space-y-2 text-sm text-slate-600">
        <p><span class="text-slate-400">手机号：</span>{{ employee.mobile || '-' }}</p>
        <p><span class="text-slate-400">邮箱：</span>{{ employee.email || '-' }}</p>
        <p><span class="text-slate-400">所属部门：</span>{{ employee.deptName || '-' }}</p>
        <p><span class="text-slate-400">上级：</span>{{ employee.parentName || '无' }}</p>
        <p><span class="text-slate-400">状态：</span>{{ statusLabel }}</p>
      </div>
    </section>

    <ObjectRelatedModules
      :tasks="tasks"
      :schedules="schedules"
      :attachments="attachments"
      :selected-task-id="selectedTaskId"
      :selected-schedule-id="selectedScheduleId"
      @add-task="emit('add-task')"
      @add-schedule="emit('add-schedule')"
      @add-attachment="emit('add-attachment')"
      @view-task="emit('view-task', $event)"
      @view-schedule="emit('view-schedule', $event)"
      @view-attachment="emit('view-attachment', $event)"
    />

    <PanelSection title="最近记录" :empty="records.length === 0">
      <div class="space-y-3">
        <div v-for="record in records" :key="`${record.type}-${record.title}-${record.recordTime}`" class="flex gap-3">
          <span class="mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-lg bg-[#f7f7f7] text-[#8f8f8f]">
            <span class="material-symbols-outlined text-[16px] leading-none">{{ recordIcon(record.type) }}</span>
          </span>
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm font-medium text-[#0d0d0d]">{{ record.title || '-' }}</p>
            <p class="mt-0.5 truncate text-xs text-[#8f8f8f]">{{ formatDate(record.recordTime) }}</p>
          </div>
        </div>
      </div>
    </PanelSection>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h } from 'vue'
import type { ScheduleVO } from '@/api/schedule'
import type { AddressBookDetail } from '@/types/addressBook'
import type { Knowledge, Task } from '@/types/common'
import ObjectRelatedModules from './ObjectRelatedModules.vue'

const props = defineProps<{
  employee: AddressBookDetail
  selectedTaskId?: string | number | null
  selectedScheduleId?: string | number | null
}>()

const emit = defineEmits<{
  (e: 'add-task'): void
  (e: 'add-schedule'): void
  (e: 'add-attachment'): void
  (e: 'view-task', task: Task): void
  (e: 'view-schedule', schedule: ScheduleVO): void
  (e: 'view-attachment', attachment: Knowledge): void
}>()

const employeeName = computed(() => props.employee.realname || '未命名员工')
const employeeInitial = computed(() => employeeName.value.charAt(0) || '?')
const tasks = computed(() => props.employee.relatedTasks || [])
const schedules = computed(() => props.employee.relatedSchedules || [])
const attachments = computed(() => props.employee.relatedAttachments || [])
const records = computed(() => props.employee.recentRecords || [])

const normalizedStatus = computed(() => {
  const value = String(props.employee.employeeStatus || '').trim()
  return value === 'resigned' || value === 'disabled' ? value : 'active'
})

const statusLabel = computed(() => {
  if (props.employee.employeeStatusName) return props.employee.employeeStatusName
  if (normalizedStatus.value === 'resigned') return '离职'
  if (normalizedStatus.value === 'disabled') return '停用'
  return '在职'
})

function formatDate(value?: string) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function recordIcon(type?: string) {
  if (type === 'schedule') return 'event'
  if (type === 'attachment') return 'description'
  return 'task_alt'
}

const PanelSection = defineComponent({
  props: {
    title: { type: String, required: true },
    empty: { type: Boolean, default: false }
  },
  setup(componentProps, { slots }) {
    return () => h('section', { class: 'mt-5 border-t border-slate-100 pt-5' }, [
      h('h3', { class: 'mb-3 text-sm font-bold text-slate-900' }, componentProps.title),
      componentProps.empty
        ? h('div', { class: 'rounded-2xl border-2 border-dashed border-slate-200 bg-slate-50/70 py-4 text-center' }, [
          h('span', { class: 'material-symbols-outlined mb-1 text-[22px] text-slate-300' }, 'history'),
          h('p', { class: 'text-xs font-medium text-slate-400' }, '暂无数据')
        ])
        : slots.default?.()
    ])
  }
})
</script>
