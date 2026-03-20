<template>
  <div
    v-if="variant === 'desktop'"
    class="w-72 shrink-0 bg-white border-r border-slate-200 flex flex-col min-h-0"
  >
    <div class="p-6 border-b border-slate-100 flex items-center justify-between">
      <h3 class="text-sm font-bold text-slate-900 flex items-center gap-2">
        <span class="material-symbols-outlined text-primary text-lg">account_tree</span>
        组织架构
      </h3>
      <button
        class="size-8 flex items-center justify-center bg-primary/10 text-primary rounded-lg hover:bg-primary/20 transition-all"
        title="添加一级部门"
        @click="$emit('add-root-dept')"
      >
        <span class="material-symbols-outlined wk-plus-button-icon">add</span>
      </button>
    </div>
    <div class="flex-1 min-h-0 overflow-y-auto p-4">
      <div v-if="loading" class="text-center py-8">
        <span class="material-symbols-outlined text-slate-300 animate-spin">progress_activity</span>
      </div>
      <el-tree
        v-else
        :data="deptTree"
        :props="{ label: 'deptName', children: 'children' }"
        :current-node-key="selectedDept?.deptId"
        node-key="deptId"
        highlight-current
        default-expand-all
        :expand-on-click-node="false"
        @node-click="handleDesktopNodeClick"
      >
        <template #default="{ data }">
          <div class="group w-full overflow-hidden px-2 py-0">
            <div
              class="flex items-center justify-between w-full min-h-[40px] overflow-hidden rounded-xl px-2 py-1 transition-colors"
              :class="selectedDept?.deptId === data.deptId ? 'bg-primary/10' : 'hover:bg-slate-50'"
            >
              <span class="flex items-center gap-2 text-sm min-w-0 overflow-hidden">
                <span
                  class="material-symbols-outlined text-base shrink-0 transition-colors"
                  :class="selectedDept?.deptId === data.deptId ? 'text-primary' : 'text-slate-400'"
                >
                  {{ data.children && data.children.length > 0 ? 'folder_open' : 'description' }}
                </span>
                <span
                  class="font-semibold truncate transition-colors"
                  :class="selectedDept?.deptId === data.deptId ? 'text-primary' : 'text-slate-700'"
                >
                  {{ data.deptName }}
                </span>
              </span>
              <div class="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity shrink-0">
                <button
                  class="size-6 flex items-center justify-center rounded hover:bg-slate-100 text-slate-400 hover:text-primary"
                  title="添加子部门"
                  @click.stop="$emit('dept-command', 'addChild', data)"
                >
                  <span class="material-symbols-outlined wk-plus-button-icon wk-plus-button-icon--compact">add</span>
                </button>
                <button
                  class="size-6 flex items-center justify-center rounded hover:bg-slate-100 text-slate-400 hover:text-primary"
                  title="编辑"
                  @click.stop="$emit('dept-command', 'edit', data)"
                >
                  <span class="material-symbols-outlined text-sm">edit</span>
                </button>
                <button
                  class="size-6 flex items-center justify-center rounded hover:bg-slate-100 text-slate-400 hover:text-red-500"
                  title="删除"
                  @click.stop="$emit('dept-command', 'delete', data)"
                >
                  <span class="material-symbols-outlined text-sm">delete</span>
                </button>
              </div>
            </div>
          </div>
        </template>
      </el-tree>
    </div>
  </div>

  <el-drawer
    v-else
    v-model="drawerVisible"
    title="组织架构"
    direction="ltr"
    size="80%"
  >
    <button
      class="w-full mb-3 px-4 py-2 bg-primary/10 text-primary rounded-xl text-sm font-bold flex items-center justify-center gap-2 hover:bg-primary/20 transition-all"
      @click="$emit('add-root-dept')"
    >
      <span class="material-symbols-outlined wk-plus-button-icon">add</span>
      添加部门
    </button>
    <el-tree
      :data="deptTree"
      :props="{ label: 'deptName', children: 'children' }"
      :current-node-key="selectedDept?.deptId"
      node-key="deptId"
      highlight-current
      default-expand-all
      :expand-on-click-node="false"
      @node-click="handleDrawerNodeClick"
    >
      <template #default="{ data }">
        <div class="group flex items-center justify-between w-full pr-1 overflow-hidden">
          <span class="flex items-center gap-2 text-sm min-w-0 overflow-hidden">
            <span class="material-symbols-outlined text-base opacity-60 shrink-0">
              {{ data.children && data.children.length > 0 ? 'folder_open' : 'description' }}
            </span>
            <span class="font-medium truncate">{{ data.deptName }}</span>
          </span>
          <div class="flex items-center gap-1 shrink-0">
            <button
              class="size-6 flex items-center justify-center rounded hover:bg-slate-100 text-slate-400 hover:text-primary"
              @click.stop="$emit('dept-command', 'addChild', data)"
            >
              <span class="material-symbols-outlined wk-plus-button-icon wk-plus-button-icon--compact">add</span>
            </button>
            <button
              class="size-6 flex items-center justify-center rounded hover:bg-slate-100 text-slate-400 hover:text-primary"
              @click.stop="$emit('dept-command', 'edit', data)"
            >
              <span class="material-symbols-outlined text-sm">edit</span>
            </button>
            <button
              class="size-6 flex items-center justify-center rounded hover:bg-slate-100 text-slate-400 hover:text-red-500"
              @click.stop="$emit('dept-command', 'delete', data)"
            >
              <span class="material-symbols-outlined text-sm">delete</span>
            </button>
          </div>
        </div>
      </template>
    </el-tree>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { DeptVO } from '@/types/dept'

const props = defineProps<{
  variant: 'desktop' | 'drawer'
  deptTree: DeptVO[]
  selectedDept: DeptVO | null
  loading: boolean
  showDrawer?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:showDrawer', value: boolean): void
  (e: 'select-dept', value: DeptVO): void
  (e: 'dept-command', command: string, value: DeptVO): void
  (e: 'add-root-dept'): void
}>()

const drawerVisible = computed({
  get: () => !!props.showDrawer,
  set: (value: boolean) => emit('update:showDrawer', value)
})

function handleDesktopNodeClick(data: DeptVO) {
  emit('select-dept', data)
}

function handleDrawerNodeClick(data: DeptVO) {
  emit('select-dept', data)
  drawerVisible.value = false
}
</script>

<style scoped>
:deep(.el-tree-node__content) {
  height: auto !important;
  min-height: 40px !important;
  padding: 0 !important;
  align-items: stretch !important;
  box-sizing: border-box;
}

:deep(.el-tree-node__expand-icon) {
  align-self: center;
}

:deep(.el-tree-node.is-current > .el-tree-node__content) {
  background-color: transparent;
}

:deep(.el-tree-node__content:hover) {
  background-color: transparent;
}
</style>
