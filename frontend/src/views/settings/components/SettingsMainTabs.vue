<template>
  <div class="bg-white border-b border-slate-200 px-4 md:px-8">
    <div class="flex items-center gap-4 md:gap-8 overflow-x-auto">
      <button
        v-for="tab in SETTINGS_MAIN_TABS"
        :key="tab.value"
        class="py-4 text-sm font-bold transition-all border-b-2 whitespace-nowrap"
        :class="activeTab === tab.value ? 'border-primary text-primary' : 'border-transparent text-slate-400 hover:text-slate-600'"
        @click="$emit('update:activeTab', tab.value)"
      >
        {{ tab.label }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { SETTINGS_MAIN_TABS } from '../constants'
import type { SettingsMainTab } from '../types'

defineProps<{
  activeTab: SettingsMainTab
}>()

defineEmits<{
  (e: 'update:activeTab', value: SettingsMainTab): void
}>()
</script>
