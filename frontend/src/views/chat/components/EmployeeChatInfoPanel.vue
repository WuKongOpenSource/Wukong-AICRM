<template>
  <div class="wk-object-detail-embedded flex h-full flex-col bg-[var(--wk-bg-surface)]">
    <div class="shrink-0 border-b border-[#ececec] px-4 py-4">
      <div class="flex items-start gap-3">
        <img v-if="employee.imgUrl" :src="employee.imgUrl" class="size-12 shrink-0 rounded-xl object-cover" alt="avatar" />
        <div v-else class="flex size-12 shrink-0 items-center justify-center rounded-xl bg-[#0d0d0d] text-base font-bold text-white">
          {{ employeeInitial }}
        </div>
        <div class="min-w-0 flex-1">
          <div class="flex min-w-0 items-center gap-2">
            <h3 class="truncate text-base font-bold text-[#0d0d0d]">{{ employeeName }}</h3>
            <span class="shrink-0 rounded-full px-2 py-1 text-[11px] font-bold" :class="statusClass">{{ statusLabel }}</span>
          </div>
          <p class="mt-1 truncate text-sm text-[#8f8f8f]">{{ employee.post || '员工' }}</p>
          <p class="mt-0.5 truncate text-xs text-[#8f8f8f]">{{ employee.deptName || '-' }}</p>
        </div>
      </div>

      <div class="mt-4 grid grid-cols-2 gap-2 text-sm">
        <InfoItem label="手机" :value="employee.mobile" />
        <InfoItem label="邮箱" :value="employee.email" />
        <InfoItem label="上级" :value="employee.parentName || '无'" />
        <InfoItem label="部门" :value="employee.deptName" />
      </div>
    </div>

    <div class="min-h-0 flex-1 overflow-y-auto px-4 py-4">
      <ObjectRelatedModules
        :tasks="tasks"
        :schedules="schedules"
        :attachments="attachments"
        @add-task="emit('add-task')"
        @add-schedule="emit('add-schedule')"
        @add-attachment="emit('add-attachment')"
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
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h } from 'vue'
import type { AddressBookDetail } from '@/types/addressBook'
import ObjectRelatedModules from './ObjectRelatedModules.vue'

const props = defineProps<{
  employee: AddressBookDetail
}>()

const emit = defineEmits<{
  (e: 'add-task'): void
  (e: 'add-schedule'): void
  (e: 'add-attachment'): void
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

const statusClass = computed(() => {
  if (normalizedStatus.value === 'resigned') return 'bg-[#f7f7f7] text-[#8f8f8f]'
  if (normalizedStatus.value === 'disabled') return 'bg-amber-50 text-amber-600'
  return 'bg-emerald-50 text-emerald-600'
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

const InfoItem = defineComponent({
  props: {
    label: { type: String, required: true },
    value: { type: String, default: '' }
  },
  setup(componentProps) {
    return () => h('div', { class: 'min-w-0 rounded-lg bg-[#f7f7f7] px-3 py-2 text-left' }, [
      h('p', { class: 'text-xs text-[#8f8f8f]' }, componentProps.label),
      h('p', { class: 'mt-1 truncate text-sm font-medium text-[#0d0d0d]', title: componentProps.value || '-' }, componentProps.value || '-')
    ])
  }
})

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
