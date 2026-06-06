<template>
  <section v-if="visible" :class="sectionClasses">
    <div class="mb-4 flex items-center justify-between">
      <h4 class="flex items-center gap-2 text-sm font-bold text-slate-900">
        <span :class="sectionIconBoxClass" :style="{ backgroundColor: '#5f704a' }">
          <WkIcon name="task" :size="14" />
        </span>
        任务
        <button
          v-if="showToggle"
          type="button"
          class="group/module-action relative inline-flex size-7 shrink-0 items-center justify-center rounded-lg bg-white text-slate-500 transition-[background-color,color,border-color] hover:bg-[#efefef] hover:text-[#0d0d0d]"
          :aria-expanded="expanded"
          :aria-label="expanded ? '收起任务' : '展开任务'"
          @click="emit('update:expanded', !expanded)"
        >
          <span class="material-symbols-outlined text-[16px] leading-none">
            {{ expanded ? 'keyboard_arrow_down' : 'keyboard_arrow_right' }}
          </span>
          <span
            class="pointer-events-none absolute left-1/2 top-full z-[200] mt-2 -translate-x-1/2 whitespace-nowrap rounded-lg bg-black px-3 py-1.5 text-[13px] font-medium text-white opacity-0 shadow-md transition-opacity duration-150 group-hover/module-action:opacity-100"
            role="tooltip"
          >
            {{ expanded ? '收起任务' : '展开任务' }}
          </span>
        </button>
      </h4>
      <div class="flex shrink-0 items-center gap-2">
        <button
          v-if="canCreate"
          type="button"
          class="group/module-action relative flex size-7 items-center justify-center rounded-lg border border-slate-200 bg-white text-slate-400 transition-all hover:border-primary/30 hover:bg-[#efefef] hover:text-primary"
          aria-label="新建任务"
          @click="emit('add')"
        >
          <span class="material-symbols-outlined wk-plus-button-icon wk-plus-button-icon--compact">add</span>
          <span
            class="pointer-events-none absolute right-full top-1/2 z-[200] mr-2 -translate-y-1/2 whitespace-nowrap rounded-lg bg-black px-3 py-1.5 text-[13px] font-medium text-white opacity-0 shadow-md transition-opacity duration-150 group-hover/module-action:opacity-100"
            role="tooltip"
          >
            新建任务
          </span>
        </button>
      </div>
    </div>

    <div v-if="isModuleVisible" class="space-y-4">
      <div v-if="loading" :class="listClass">
        <div
          v-for="index in 3"
          :key="`task-skeleton-${index}`"
          class="relative rounded-xl border border-slate-200 bg-white p-3"
        >
          <div v-if="!embeddedLayout" class="absolute -left-[27px] top-4 size-3 animate-pulse rounded-full border-2 border-slate-200 bg-white" />
          <div class="flex items-start gap-3">
            <div class="mt-0.5 size-5 shrink-0 animate-pulse rounded border border-slate-200 bg-slate-100" />
            <div class="min-w-0 flex-1 space-y-2">
              <div class="h-4 w-3/4 animate-pulse rounded-full bg-slate-100" />
              <div class="flex items-center gap-2">
                <div class="h-5 w-20 animate-pulse rounded-full bg-slate-100" />
                <div class="h-5 w-16 animate-pulse rounded-full bg-slate-100" />
              </div>
            </div>
          </div>
        </div>
      </div>
      <RelatedEmptyState v-else-if="tasks.length === 0" icon="task_alt" text="暂无待办任务" />
      <div v-else :class="listClass">
        <div
          v-for="task in tasks"
          :key="task.taskId"
          class="group relative rounded-xl border border-slate-200 bg-white p-3 transition-all hover:shadow-md"
          :class="[
            clickable ? 'cursor-pointer' : '',
            selectedTaskId && String(task.taskId) === String(selectedTaskId) ? 'border-primary ring-1 ring-primary/20' : ''
          ]"
          @click="emit('view', task)"
        >
          <div
            v-if="!embeddedLayout"
            class="absolute -left-[27px] top-4 size-3 rounded-full border-2 bg-white"
            :class="isTaskCompleted(task) ? 'border-emerald-500' : 'border-slate-300'"
          />
          <div class="flex items-start gap-3">
            <button
              v-if="canToggle"
              type="button"
              class="mt-0.5 flex size-5 shrink-0 items-center justify-center rounded border transition-colors"
              :class="isTaskCompleted(task)
                ? 'border-emerald-500 bg-emerald-500 text-white'
                : 'border-slate-300 text-transparent hover:border-primary hover:text-primary/20'"
              :aria-label="isTaskCompleted(task) ? '标记为未完成' : '标记为已完成'"
              :title="isTaskCompleted(task) ? '标记为未完成' : '标记为已完成'"
              @click.stop="emit('toggle', task)"
            >
              <span class="material-symbols-outlined text-[14px] font-bold">check</span>
            </button>
            <span
              v-else
              class="mt-0.5 flex size-5 shrink-0 items-center justify-center rounded border"
              :class="isTaskCompleted(task) ? 'border-emerald-500 bg-emerald-500 text-white' : 'border-slate-300 text-transparent'"
            >
              <span class="material-symbols-outlined text-[14px] font-bold">check</span>
            </span>
            <div class="min-w-0 flex-1">
              <h5
                class="mb-1 truncate text-sm font-bold transition-colors group-hover:text-primary"
                :class="isTaskCompleted(task) ? 'text-slate-400 line-through' : 'text-slate-900'"
                :title="task.title"
              >
                {{ task.title || '-' }}
              </h5>
              <div class="flex flex-wrap items-center gap-2">
                <span
                  v-if="task.dueDate"
                  class="rounded-full px-2 py-0.5 text-xs font-bold"
                  :class="isTaskOverdue(task) ? 'bg-red-50 text-red-500' : 'bg-slate-50 text-slate-600'"
                >
                  截止 {{ formatDate(task.dueDate) }}
                </span>
                <span
                  v-if="task.priority"
                  class="rounded-full px-2 py-0.5 text-xs font-bold"
                  :class="getTaskPriorityClass(task.priority)"
                >
                  {{ getTaskPriorityLabel(task.priority) }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Task, TaskPriority } from '@/types/common'
import RelatedEmptyState from './RelatedEmptyState.vue'

const props = withDefaults(defineProps<{
  tasks?: Task[]
  loading?: boolean
  visible?: boolean
  embeddedLayout?: boolean
  expanded?: boolean
  canCreate?: boolean
  canToggle?: boolean
  clickable?: boolean
  selectedTaskId?: string | number | null
}>(), {
  tasks: () => [],
  loading: false,
  visible: true,
  embeddedLayout: true,
  expanded: true,
  canCreate: true,
  canToggle: false,
  clickable: false,
  selectedTaskId: null
})

const emit = defineEmits<{
  (e: 'update:expanded', value: boolean): void
  (e: 'add'): void
  (e: 'view', task: Task): void
  (e: 'toggle', task: Task): void
}>()

const sectionIconBoxClass = 'inline-flex size-7 shrink-0 items-center justify-center rounded-lg text-white shadow-sm'
const showToggle = computed(() => props.loading || props.tasks.length > 0)
const isModuleVisible = computed(() => props.expanded || !showToggle.value)
const sectionClasses = computed(() => [
  'group/tasks-module',
  props.embeddedLayout
    ? 'mt-5 border-t border-slate-100 pt-5'
    : 'rounded-2xl border border-slate-200 bg-white p-4 shadow-sm'
])
const listClass = computed(() => props.embeddedLayout ? 'space-y-3' : 'ml-3 space-y-3 border-l-2 border-slate-100 pl-5')

function isTaskCompleted(task: Task): boolean {
  return task.status === 'COMPLETED' || task.statusName === '已完成'
}

function isTaskOverdue(task: Task): boolean {
  if (task.overdue !== undefined) return Boolean(task.overdue)
  if (!task.dueDate || isTaskCompleted(task)) return false
  return new Date(task.dueDate) < new Date()
}

function getTaskPriorityLabel(priority?: TaskPriority | string) {
  const labels: Record<string, string> = {
    HIGH: '高优先级',
    MEDIUM: '中优先级',
    LOW: '低优先级'
  }
  return labels[String(priority || '').toUpperCase()] || '中优先级'
}

function getTaskPriorityClass(priority?: TaskPriority | string) {
  const classes: Record<string, string> = {
    HIGH: 'bg-red-50 text-red-500',
    MEDIUM: 'bg-amber-50 text-amber-500',
    LOW: 'bg-slate-100 text-slate-500'
  }
  return classes[String(priority || '').toUpperCase()] || classes.MEDIUM
}

function formatDate(dateStr?: string) {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  if (Number.isNaN(date.getTime())) return dateStr
  return date.toLocaleDateString('zh-CN')
}
</script>
