<template>
  <div class="h-full overflow-y-auto px-4 py-4">
    <section class="rounded-lg border border-slate-200 p-4">
      <div class="flex items-start gap-3">
        <img v-if="avatarUrl" :src="avatarUrl" class="size-12 rounded-full object-cover" alt="avatar" />
        <div v-else class="flex size-12 shrink-0 items-center justify-center rounded-full bg-slate-900 text-base font-bold text-white">
          {{ relation.name?.charAt(0) || '?' }}
        </div>
        <div class="min-w-0 flex-1">
          <h3 class="truncate text-base font-bold text-slate-900">{{ relation.name || '未命名关系人' }}</h3>
          <p class="mt-1 text-sm text-slate-500">{{ relation.relationTypeName || relation.relationType || '关系' }}</p>
        </div>
      </div>
      <div class="mt-4 space-y-2 text-sm text-slate-600">
        <p><span class="text-slate-400">手机号：</span>{{ relation.phone || '-' }}</p>
        <p><span class="text-slate-400">微信号：</span>{{ relation.wechat || '-' }}</p>
        <p><span class="text-slate-400">邮箱：</span>{{ relation.email || '-' }}</p>
        <p><span class="text-slate-400">所属公司：</span>{{ relation.company || '-' }}</p>
        <p><span class="text-slate-400">来源：</span>{{ relation.sourceName || sourceLabel }}</p>
      </div>
      <p class="mt-4 whitespace-pre-wrap rounded-lg bg-slate-50 p-3 text-sm leading-6 text-slate-600">
        {{ relation.remark || '暂无备注' }}
      </p>
    </section>

    <section v-for="section in sections" :key="section.key" class="mt-4 rounded-lg border border-slate-200 p-4">
      <div class="mb-3 flex items-center justify-between">
        <h4 class="text-sm font-bold text-slate-900">{{ section.title }}</h4>
        <span class="text-xs text-slate-400">{{ section.items.length }}</span>
      </div>
      <div v-if="section.items.length === 0" class="py-5 text-center text-xs text-slate-400">
        暂无数据
      </div>
      <div v-else class="space-y-2">
        <div v-for="item in section.items" :key="item.key" class="rounded-lg bg-slate-50 px-3 py-2">
          <p class="truncate text-sm font-semibold text-slate-800">{{ item.title }}</p>
          <p v-if="item.subtitle" class="mt-1 line-clamp-2 text-xs leading-5 text-slate-500">{{ item.subtitle }}</p>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { RelationDetailVO } from '@/types/relation'

const props = defineProps<{
  detail: RelationDetailVO
}>()

const relation = computed(() => props.detail.relation)
const avatarUrl = computed(() => relation.value.avatarUrl || relation.value.avatar || '')
const sourceLabel = computed(() => {
  if (relation.value.source === 'customer_contact') return '客户联系人'
  if (relation.value.source === 'manual') return '手动创建'
  return relation.value.source || '-'
})

const sections = computed(() => [
  {
    key: 'tasks',
    title: '相关任务',
    items: (props.detail.tasks || []).map(task => ({
      key: `task-${task.taskId}`,
      title: task.title,
      subtitle: [task.statusName || task.status, task.dueDate ? `截止 ${formatDateTime(task.dueDate)}` : ''].filter(Boolean).join(' · ')
    }))
  },
  {
    key: 'schedules',
    title: '相关日程',
    items: (props.detail.schedules || []).map(schedule => ({
      key: `schedule-${schedule.scheduleId}`,
      title: schedule.title,
      subtitle: [schedule.startTime ? formatDateTime(schedule.startTime) : '', schedule.location || ''].filter(Boolean).join(' · ')
    }))
  },
  {
    key: 'attachments',
    title: '相关附件',
    items: (props.detail.attachments || []).map(knowledge => ({
      key: `knowledge-${knowledge.knowledgeId}`,
      title: knowledge.name,
      subtitle: [knowledge.summary || '', knowledge.createTime ? formatDateTime(knowledge.createTime) : ''].filter(Boolean).join(' · ')
    }))
  },
  {
    key: 'histories',
    title: '历史记录',
    items: (props.detail.histories || []).map(history => ({
      key: `history-${history.followUpId}`,
      title: history.content,
      subtitle: [history.typeName || history.type, history.followTime ? formatDateTime(history.followTime) : ''].filter(Boolean).join(' · ')
    }))
  }
])

function formatDateTime(value?: string) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}
</script>
