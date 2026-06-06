<template>
  <div>
    <RelatedTasksModule
      :tasks="tasks"
      :embedded-layout="true"
      :can-create="true"
      :clickable="true"
      :selected-task-id="selectedTaskId"
      :expanded="tasksExpanded"
      @update:expanded="tasksExpanded = $event"
      @add="emit('add-task')"
      @view="emit('view-task', $event)"
    />
    <RelatedSchedulesModule
      :schedules="schedules"
      :embedded-layout="true"
      :can-create="true"
      :clickable="true"
      :selected-schedule-id="selectedScheduleId"
      :expanded="schedulesExpanded"
      @update:expanded="schedulesExpanded = $event"
      @add="emit('add-schedule')"
      @view="emit('view-schedule', $event)"
    />
    <RelatedDocumentsModule
      :documents="attachments"
      :embedded-layout="true"
      :can-upload="true"
      :clickable="true"
      :expanded="documentsExpanded"
      @update:expanded="documentsExpanded = $event"
      @upload="emit('add-attachment')"
      @open="emit('view-attachment', $event)"
    />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import RelatedDocumentsModule from '@/components/customer/related/RelatedDocumentsModule.vue'
import RelatedSchedulesModule from '@/components/customer/related/RelatedSchedulesModule.vue'
import RelatedTasksModule from '@/components/customer/related/RelatedTasksModule.vue'
import type { ScheduleVO } from '@/api/schedule'
import type { Knowledge, Task } from '@/types/common'

withDefaults(defineProps<{
  tasks?: Task[]
  schedules?: ScheduleVO[]
  attachments?: Knowledge[]
  selectedTaskId?: string | number | null
  selectedScheduleId?: string | number | null
}>(), {
  tasks: () => [],
  schedules: () => [],
  attachments: () => [],
  selectedTaskId: null,
  selectedScheduleId: null
})

const emit = defineEmits<{
  (e: 'add-task'): void
  (e: 'add-schedule'): void
  (e: 'add-attachment'): void
  (e: 'view-task', task: Task): void
  (e: 'view-schedule', schedule: ScheduleVO): void
  (e: 'view-attachment', attachment: Knowledge): void
}>()

const tasksExpanded = ref(true)
const schedulesExpanded = ref(true)
const documentsExpanded = ref(true)
</script>
