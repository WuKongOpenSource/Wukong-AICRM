<template>
  <div>
    <RelatedTasksModule
      :tasks="tasks"
      :embedded-layout="true"
      :can-create="true"
      :expanded="tasksExpanded"
      @update:expanded="tasksExpanded = $event"
      @add="emit('add-task')"
    />
    <RelatedSchedulesModule
      :schedules="schedules"
      :embedded-layout="true"
      :can-create="true"
      :expanded="schedulesExpanded"
      @update:expanded="schedulesExpanded = $event"
      @add="emit('add-schedule')"
    />
    <RelatedDocumentsModule
      :documents="attachments"
      :embedded-layout="true"
      :can-upload="true"
      :expanded="documentsExpanded"
      @update:expanded="documentsExpanded = $event"
      @upload="emit('add-attachment')"
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
}>(), {
  tasks: () => [],
  schedules: () => [],
  attachments: () => []
})

const emit = defineEmits<{
  (e: 'add-task'): void
  (e: 'add-schedule'): void
  (e: 'add-attachment'): void
}>()

const tasksExpanded = ref(true)
const schedulesExpanded = ref(true)
const documentsExpanded = ref(true)
</script>
