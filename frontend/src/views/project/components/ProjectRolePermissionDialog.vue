<template>
  <el-dialog
    v-model="open"
    :width="isMobile ? 'calc(100% - 24px)' : '860px'"
    :show-close="false"
    destroy-on-close
    class="wk-dialog--flush wk-project-role-permission-dialog wk-crm-el-field-scope"
  >
    <template #header>
      <div class="flex items-center justify-between gap-4">
        <div class="flex min-w-0 items-center gap-3">
          <div class="flex size-11 shrink-0 items-center justify-center rounded-2xl bg-primary/10 text-primary">
            <span class="material-symbols-outlined text-[22px]">admin_panel_settings</span>
          </div>
          <div class="min-w-0">
            <h2 class="truncate text-lg font-bold text-slate-900">项目角色权限设置</h2>
            <p class="mt-0.5 text-xs text-slate-500">配置新增成员和切换角色时自动带出的默认权限</p>
          </div>
        </div>
        <button
          type="button"
          class="flex size-8 shrink-0 items-center justify-center rounded-full text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700"
          @click="open = false"
        >
          <span class="material-symbols-outlined text-[18px]">close</span>
        </button>
      </div>
    </template>

    <div class="grid min-h-0 grid-cols-1 gap-0 bg-white md:grid-cols-[220px_minmax(0,1fr)]" v-loading="loading">
      <aside class="border-b border-slate-200 bg-slate-50/70 p-4 md:border-b-0 md:border-r">
        <div class="space-y-1">
          <template v-for="role in roleOptions" :key="role.value">
            <div v-if="editingRole === role.value" class="rounded-xl border border-primary/20 bg-white p-2 shadow-sm">
              <input
                v-model="editingRoleName"
                class="h-9 w-full rounded-lg border border-slate-200 px-3 text-sm outline-none transition-colors focus:border-primary"
                maxlength="20"
                placeholder="输入角色名称"
                @keydown.enter.prevent="handleRenameRole"
                @keydown.esc.prevent="cancelEditRole"
              >
              <div class="mt-2 flex gap-2">
                <button
                  type="button"
                  class="flex-1 rounded-lg bg-primary px-2 py-1.5 text-xs font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50"
                  :disabled="!editingRoleName.trim() || saving"
                  @click="handleRenameRole"
                >
                  确定
                </button>
                <button
                  type="button"
                  class="flex-1 rounded-lg bg-slate-100 px-2 py-1.5 text-xs font-semibold text-slate-600 hover:bg-slate-200"
                  @click="cancelEditRole"
                >
                  取消
                </button>
              </div>
            </div>
            <div
              v-else
              class="group/role flex w-full items-center gap-2 rounded-xl px-3 py-2.5 text-sm transition-colors"
              :class="activeRole === role.value ? 'bg-white font-bold text-primary shadow-sm' : 'text-slate-600 hover:bg-white/70 hover:text-slate-900'"
            >
              <button
                type="button"
                class="flex min-w-0 flex-1 items-center justify-between gap-3 text-left"
                @click="activeRole = role.value"
              >
                <span class="truncate">{{ role.label }}</span>
                <span class="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-500">
                  {{ rolePermissions[role.value]?.length || 0 }}
                </span>
              </button>
              <div
                v-if="isCustomRole(role.value)"
                class="flex w-0 shrink-0 items-center justify-end gap-1 overflow-hidden opacity-0 transition-all duration-150 group-hover/role:w-14 group-hover/role:opacity-100 group-focus-within/role:w-14 group-focus-within/role:opacity-100"
              >
                <button
                  type="button"
                  class="flex size-6 items-center justify-center rounded-md text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700"
                  aria-label="编辑角色"
                  title="编辑角色"
                  @click.stop="startEditRole(role.value)"
                >
                  <span class="material-symbols-outlined text-[15px]">edit</span>
                </button>
                <button
                  type="button"
                  class="flex size-6 items-center justify-center rounded-md text-slate-400 transition-colors hover:bg-red-50 hover:text-red-600"
                  aria-label="删除角色"
                  title="删除角色"
                  @click.stop="handleDeleteRole(role.value)"
                >
                  <span class="material-symbols-outlined text-[15px]">delete</span>
                </button>
              </div>
            </div>
          </template>
          <div v-if="creatingRole" class="rounded-xl border border-primary/20 bg-white p-2 shadow-sm">
            <input
              v-model="newRoleName"
              class="h-9 w-full rounded-lg border border-slate-200 px-3 text-sm outline-none transition-colors focus:border-primary"
              maxlength="20"
              placeholder="输入角色名称"
              @keydown.enter.prevent="handleCreateRole"
              @keydown.esc.prevent="cancelCreateRole"
            >
            <div class="mt-2 flex gap-2">
              <button
                type="button"
                class="flex-1 rounded-lg bg-primary px-2 py-1.5 text-xs font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50"
                :disabled="!newRoleName.trim() || saving"
                @click="handleCreateRole"
              >
                确定
              </button>
              <button
                type="button"
                class="flex-1 rounded-lg bg-slate-100 px-2 py-1.5 text-xs font-semibold text-slate-600 hover:bg-slate-200"
                @click="cancelCreateRole"
              >
                取消
              </button>
            </div>
          </div>
          <button
            v-else
            type="button"
            class="mt-2 flex w-full items-center justify-center gap-1.5 rounded-xl border border-dashed border-slate-300 bg-white/70 px-3 py-2 text-sm font-semibold text-slate-600 transition-colors hover:border-primary/40 hover:text-primary"
            @click="startCreateRole"
          >
            <span class="material-symbols-outlined text-[17px]">add</span>
            新增角色
          </button>
        </div>
      </aside>

      <section class="flex min-h-0 flex-col p-5 md:p-6">
        <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div class="min-w-0">
            <h3 class="text-base font-bold text-slate-900">{{ activeRoleLabel }}</h3>
            <p class="mt-1 text-xs text-slate-500">已选择 {{ rolePermissions[activeRole]?.length || 0 }} 项权限</p>
          </div>
          <div class="flex items-center gap-2">
            <button
              type="button"
              class="rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-600 transition-colors hover:bg-slate-50"
              @click="resetCurrentRole"
            >
              恢复当前角色默认
            </button>
            <button
              type="button"
              class="rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-600 transition-colors hover:bg-slate-50"
              @click="resetAllRoles"
            >
              恢复全部默认
            </button>
          </div>
        </div>

        <el-checkbox-group
          v-model="rolePermissions[activeRole]"
          class="grid min-h-0 grid-cols-1 gap-2 overflow-y-auto pr-1 md:grid-cols-2"
          @change="ensureActiveRoleBaseline"
        >
          <label
            v-for="permission in PROJECT_PERMISSION_OPTIONS"
            :key="permission.value"
            class="wk-project-role-permission-item flex min-h-[42px] items-center gap-2.5 rounded-xl border border-slate-200 bg-white px-3 py-2 transition-colors hover:border-primary/25 hover:bg-primary/[0.03]"
          >
            <el-checkbox
              :value="permission.value"
              :disabled="permission.value === 'VIEW_PROJECT' || activeRole === 'OWNER'"
              :aria-label="permission.label"
            />
            <span class="text-sm leading-5 text-slate-700">{{ permission.label }}</span>
          </label>
        </el-checkbox-group>
      </section>
    </div>

    <template #footer>
      <div class="flex gap-3">
        <button
          type="button"
          class="flex-1 rounded-xl bg-slate-100 py-2.5 text-sm font-bold text-slate-600 transition-colors hover:bg-slate-200"
          @click="open = false"
        >
          取消
        </button>
        <button
          type="button"
          class="flex-1 rounded-xl bg-primary py-2.5 text-sm font-bold text-white transition-colors hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50"
          :disabled="saving || loading"
          @click="handleSave"
        >
          保存配置
        </button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getProjectRolePermissionConfig,
  updateProjectRolePermissionConfig
} from '@/api/project'
import { useResponsive } from '@/composables/useResponsive'
import type {
  ProjectPermission,
  ProjectRole,
  ProjectRolePermissionConfig
} from '@/types/project'
import {
  DEFAULT_ROLE_PERMISSIONS,
  PROJECT_PERMISSION_OPTIONS,
  SYSTEM_PROJECT_ROLE_VALUES,
  getProjectRolePermissions,
  getProjectRoleOptions,
  projectRoleLabel,
  setProjectRolePermissions
} from '@/utils/project'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'saved'): void
}>()

const { isMobile } = useResponsive()

const loading = ref(false)
const saving = ref(false)
const activeRole = ref<ProjectRole>('MEMBER')
const creatingRole = ref(false)
const newRoleName = ref('')
const editingRole = ref<ProjectRole>('')
const editingRoleName = ref('')
const rolePermissions = reactive<ProjectRolePermissionConfig>(getProjectRolePermissions())

const open = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const activeRoleLabel = computed(() => projectRoleLabel(activeRole.value))
const roleOptions = computed(() => getProjectRoleOptions(rolePermissions))

watch(
  () => props.modelValue,
  visible => {
    if (!visible) return
    void loadConfig()
  },
  { immediate: true }
)

async function loadConfig() {
  loading.value = true
  try {
    const config = await getProjectRolePermissionConfig()
    const mergedConfig = mergeSavedRolePermissions(config.rolePermissions, getProjectRolePermissions())
    setProjectRolePermissions(mergedConfig)
    applyLocalRolePermissions(mergedConfig)
  } catch {
    applyLocalRolePermissions(getProjectRolePermissions())
  } finally {
    loading.value = false
  }
}

function applyLocalRolePermissions(config: ProjectRolePermissionConfig) {
  Object.keys(rolePermissions).forEach(role => {
    delete rolePermissions[role]
  })
  getProjectRoleOptions(config).forEach(role => {
    rolePermissions[role.value] = [...(config[role.value] || DEFAULT_ROLE_PERMISSIONS[role.value] || ['VIEW_PROJECT'])]
    ensureRoleBaseline(role.value)
  })
  if (!rolePermissions[activeRole.value]) {
    activeRole.value = rolePermissions.MEMBER ? 'MEMBER' : roleOptions.value[0]?.value || 'MEMBER'
  }
}

function resetCurrentRole() {
  rolePermissions[activeRole.value] = [...(DEFAULT_ROLE_PERMISSIONS[activeRole.value] || ['VIEW_PROJECT'])]
  ensureActiveRoleBaseline()
}

function resetAllRoles() {
  applyLocalRolePermissions(DEFAULT_ROLE_PERMISSIONS)
}

function ensureActiveRoleBaseline() {
  ensureRoleBaseline(activeRole.value)
}

function ensureRoleBaseline(role: ProjectRole) {
  const permissions = rolePermissions[role] || []
  if (role === 'OWNER') {
    rolePermissions.OWNER = [...DEFAULT_ROLE_PERMISSIONS.OWNER]
    return
  }
  if (!permissions.includes('VIEW_PROJECT')) {
    permissions.unshift('VIEW_PROJECT')
  }
  rolePermissions[role] = Array.from(new Set(permissions)) as ProjectPermission[]
}

function startCreateRole() {
  cancelEditRole()
  creatingRole.value = true
  newRoleName.value = ''
}

function cancelCreateRole() {
  creatingRole.value = false
  newRoleName.value = ''
}

async function handleCreateRole() {
  const roleName = newRoleName.value.trim()
  if (!roleName) {
    ElMessage.warning('请输入角色名称')
    return
  }
  if (isDuplicatedRoleName(roleName)) {
    ElMessage.warning('角色名称已存在')
    return
  }
  const previousRole = activeRole.value
  rolePermissions[roleName] = ['VIEW_PROJECT']
  activeRole.value = roleName
  cancelCreateRole()
  try {
    await persistRolePermissionConfig({ successMessage: '角色已新增' })
  } catch {
    delete rolePermissions[roleName]
    activeRole.value = previousRole
    ElMessage.error('新增角色保存失败，请稍后重试')
  }
}

function isCustomRole(role: ProjectRole) {
  return Boolean(role) && !SYSTEM_PROJECT_ROLE_VALUES.has(role)
}

function isDuplicatedRoleName(roleName: string, currentRole: ProjectRole = '') {
  const normalizedRoleName = roleName.toLowerCase()
  return roleOptions.value.some(option => {
    if (option.value === currentRole) return false
    return option.value.toLowerCase() === normalizedRoleName || option.label === roleName
  })
}

function startEditRole(role: ProjectRole) {
  if (!isCustomRole(role)) return
  cancelCreateRole()
  editingRole.value = role
  editingRoleName.value = role
}

function cancelEditRole() {
  editingRole.value = ''
  editingRoleName.value = ''
}

async function handleRenameRole() {
  const currentRole = editingRole.value
  const nextRole = editingRoleName.value.trim()
  if (!currentRole || !isCustomRole(currentRole)) {
    cancelEditRole()
    return
  }
  if (!nextRole) {
    ElMessage.warning('请输入角色名称')
    return
  }
  if (SYSTEM_PROJECT_ROLE_VALUES.has(nextRole) || isDuplicatedRoleName(nextRole, currentRole)) {
    ElMessage.warning('角色名称已存在')
    return
  }
  if (nextRole === currentRole) {
    cancelEditRole()
    return
  }
  const entries = Object.entries(rolePermissions).map(([role, permissions]) =>
    role === currentRole ? [nextRole, permissions] as const : [role, permissions] as const
  )
  Object.keys(rolePermissions).forEach(role => {
    delete rolePermissions[role]
  })
  entries.forEach(([role, permissions]) => {
    rolePermissions[role] = [...permissions]
  })
  activeRole.value = nextRole
  cancelEditRole()
  try {
    await persistRolePermissionConfig({ successMessage: '角色已更新' })
  } catch {
    ElMessage.error('角色更新保存失败，请稍后重试')
    void loadConfig()
  }
}

async function handleDeleteRole(role: ProjectRole) {
  if (!isCustomRole(role)) return
  try {
    await ElMessageBox.confirm('删除后该角色将从项目角色权限配置中移除，已使用该角色的成员不会自动变更角色。', '删除角色', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  delete rolePermissions[role]
  if (editingRole.value === role) {
    cancelEditRole()
  }
  if (activeRole.value === role) {
    activeRole.value = rolePermissions.MEMBER ? 'MEMBER' : Object.keys(rolePermissions)[0] || 'MEMBER'
  }
  try {
    await persistRolePermissionConfig({ successMessage: '角色已删除' })
  } catch {
    ElMessage.error('删除角色保存失败，请稍后重试')
    void loadConfig()
  }
}

function normalizeCurrentRolePermissions() {
  Object.keys(rolePermissions).forEach(role => {
    if (!role.trim()) {
      delete rolePermissions[role]
      return
    }
    if (!SYSTEM_PROJECT_ROLE_VALUES.has(role) && (rolePermissions[role]?.length || 0) === 0) {
      rolePermissions[role] = ['VIEW_PROJECT']
    }
    ensureRoleBaseline(role)
  })
}

function cloneCurrentRolePermissions(): ProjectRolePermissionConfig {
  return Object.entries(rolePermissions).reduce((config, [role, permissions]) => {
    if (!role.trim()) return config
    config[role] = [...(permissions || [])]
    return config
  }, {} as ProjectRolePermissionConfig)
}

function mergeSavedRolePermissions(
  saved: ProjectRolePermissionConfig,
  submitted: ProjectRolePermissionConfig
): ProjectRolePermissionConfig {
  const merged = Object.entries(saved || {}).reduce((config, [role, permissions]) => {
    if (!role.trim()) return config
    config[role] = [...(permissions || [])]
    return config
  }, {} as ProjectRolePermissionConfig)
  Object.entries(submitted || {}).forEach(([role, permissions]) => {
    if (!role.trim() || SYSTEM_PROJECT_ROLE_VALUES.has(role)) return
    merged[role] = [...(permissions || ['VIEW_PROJECT'])]
  })
  return merged
}

async function persistRolePermissionConfig(options: { close?: boolean; successMessage?: string } = {}) {
  saving.value = true
  try {
    normalizeCurrentRolePermissions()
    const submittedRolePermissions = cloneCurrentRolePermissions()
    const config = await updateProjectRolePermissionConfig({
      rolePermissions: { ...rolePermissions }
    })
    const mergedConfig = mergeSavedRolePermissions(config.rolePermissions, submittedRolePermissions)
    setProjectRolePermissions(mergedConfig)
    applyLocalRolePermissions(mergedConfig)
    if (options.successMessage) {
      ElMessage.success(options.successMessage)
    }
    emit('saved')
    if (options.close) {
      open.value = false
    }
  } finally {
    saving.value = false
  }
}

async function handleSave() {
  await persistRolePermissionConfig({
    close: true,
    successMessage: '项目角色权限配置已保存'
  })
}
</script>

<style scoped>
:global(.wk-project-role-permission-dialog.el-dialog) {
  display: flex;
  height: min(760px, calc(100dvh - 32px)) !important;
  max-height: min(760px, calc(100dvh - 32px)) !important;
  flex-direction: column;
  overflow: hidden;
  margin: 0 auto !important;
}

:global(.wk-project-role-permission-dialog.el-dialog .el-dialog__header) {
  flex: 0 0 auto;
  padding: 22px 24px 14px;
  margin-right: 0;
}

:global(.wk-project-role-permission-dialog.el-dialog .el-dialog__body) {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 0 !important;
}

:global(.wk-project-role-permission-dialog.el-dialog .el-dialog__footer) {
  flex: 0 0 auto;
  border-top: 1px solid var(--wk-border-subtle);
  background: #fff;
  padding: 14px 24px 22px !important;
}

.wk-project-role-permission-item :deep(.el-checkbox) {
  height: auto;
  margin-right: 0;
  line-height: 1;
}

.wk-project-role-permission-item :deep(.el-checkbox__input) {
  display: inline-flex;
  align-items: center;
}

:global(.el-overlay:has(.wk-project-role-permission-dialog)),
:global(.el-overlay-dialog:has(.wk-project-role-permission-dialog)) {
  overflow: hidden;
}

:global(.el-overlay-dialog:has(.wk-project-role-permission-dialog)) {
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  padding: 16px;
}

@media (max-width: 767px) {
  :global(.wk-project-role-permission-dialog.el-dialog) {
    height: calc(100dvh - 24px) !important;
    max-height: calc(100dvh - 24px) !important;
    margin: 0 auto !important;
  }

  :global(.el-overlay-dialog:has(.wk-project-role-permission-dialog)) {
    padding: 12px;
  }
}
</style>
