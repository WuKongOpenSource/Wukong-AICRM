<template>
  <div class="system-sub-tabs max-w-4xl mx-auto mb-6">
    <div class="flex gap-2 overflow-x-auto">
      <button
        v-for="tab in SYSTEM_SETTINGS_TABS"
        :key="tab.value"
        class="system-sub-tabs__button"
        :class="{ 'is-active': activeTab === tab.value }"
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

<style scoped>
.system-sub-tabs__button {
  white-space: nowrap;
  border: 1px solid var(--wk-border-subtle);
  border-radius: 999px;
  background: var(--wk-bg-surface);
  padding: 0.375rem 1rem;
  color: var(--wk-text-muted);
  font-size: 0.75rem;
  line-height: 1rem;
  font-weight: 700;
  transition:
    background-color 0.18s ease,
    border-color 0.18s ease,
    color 0.18s ease;
}

.system-sub-tabs__button:hover {
  background: var(--wk-bg-surface-hover);
  color: var(--wk-text-secondary);
}

.system-sub-tabs__button.is-active {
  border-color: var(--wk-primary);
  background: var(--wk-primary);
  color: #fff;
}
</style>
