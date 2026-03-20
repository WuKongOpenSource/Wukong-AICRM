<template>
  <div class="max-w-4xl mx-auto mb-6">
    <div class="flex gap-2 overflow-x-auto">
      <button
        v-for="tab in SYSTEM_SETTINGS_TABS"
        :key="tab.value"
        class="px-4 py-1.5 text-xs font-bold rounded-full transition-all whitespace-nowrap"
        :class="activeTab === tab.value ? 'bg-primary text-white' : 'bg-white text-slate-500 border border-slate-200 hover:bg-slate-50'"
        @click="$emit('update:activeTab', tab.value)"
      >
        {{ tab.label }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { SYSTEM_SETTINGS_TABS } from '../constants'
import type { SystemSettingsTab } from '../types'

defineProps<{
  activeTab: SystemSettingsTab
}>()

defineEmits<{
  (e: 'update:activeTab', value: SystemSettingsTab): void
}>()
</script>
