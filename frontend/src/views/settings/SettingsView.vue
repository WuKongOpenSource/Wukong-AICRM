<template>
  <div class="flex flex-col h-full bg-background-light">
    <SettingsMainTabs v-model:active-tab="activeMainTab" />

    <div class="flex-1 min-h-0" :class="activeMainTab === 'team' ? 'overflow-hidden' : 'overflow-auto'">
      <div :class="activeMainTab === 'team' ? 'h-full min-h-0 flex flex-col' : 'p-4 md:p-6'">
        <KeepAlive>
          <component
            :is="activeSectionComponent"
            v-bind="activeSectionProps"
          />
        </KeepAlive>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import SettingsMainTabs from './components/SettingsMainTabs.vue'
import type { SettingsMainTab, SystemSettingsTab } from './types'
import TeamManagementSection from './sections/team/TeamManagementSection.vue'
import RolePermissionSection from './sections/role/RolePermissionSection.vue'
import SystemSettingsSection from './sections/system/SystemSettingsSection.vue'

const activeMainTab = ref<SettingsMainTab>('team')
const activeSystemTab = ref<SystemSettingsTab>('profile')

const sectionMap = {
  team: TeamManagementSection,
  role: RolePermissionSection,
  system: SystemSettingsSection
} as const

const activeSectionComponent = computed(() => sectionMap[activeMainTab.value])
const activeSectionProps = computed(() => {
  if (activeMainTab.value !== 'system') {
    return {}
  }
  return {
    activeTab: activeSystemTab.value,
    'onUpdate:activeTab': (value: SystemSettingsTab) => {
      activeSystemTab.value = value
    }
  }
})
</script>
