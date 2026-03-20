<template>
  <div class="bg-slate-50/50 -m-4 md:-m-6 p-4 md:p-8">
    <div class="bg-white border border-slate-200 rounded-2xl shadow-sm flex flex-col overflow-hidden">
      <div class="p-6 border-b border-slate-100 flex items-center justify-between bg-white">
        <div>
          <h2 class="text-lg font-bold text-slate-900">角色列表</h2>
          <p class="text-sm text-slate-500 mt-1">管理系统角色，并为不同角色分配功能权限与数据范围。</p>
        </div>
        <button
          class="bg-primary text-white px-4 py-2 rounded-xl text-sm font-bold flex items-center gap-2 hover:bg-primary/90 transition-all shadow-sm shadow-primary/20"
          @click="$emit('add-role')"
        >
          <span class="material-symbols-outlined wk-plus-button-icon">add</span>
          新增角色
        </button>
      </div>

      <div class="flex-1 overflow-auto p-6">
        <div v-if="loadingRoles" class="text-center py-16">
          <span class="material-symbols-outlined text-slate-300 text-3xl animate-spin">progress_activity</span>
        </div>
        <div v-else-if="roleList.length === 0" class="text-center py-16 text-slate-400">
          <span class="material-symbols-outlined text-3xl mb-2 opacity-50">admin_panel_settings</span>
          <p class="text-sm">暂无角色，请创建</p>
        </div>
        <table v-else class="w-full text-left text-sm">
          <thead class="bg-slate-50/80 border-b border-slate-100 text-slate-500">
            <tr>
              <th class="px-6 py-4 font-bold rounded-tl-xl">角色名称</th>
              <th class="px-6 py-4 font-bold">角色描述</th>
              <th class="px-6 py-4 font-bold">成员数量</th>
              <th class="px-6 py-4 font-bold text-right rounded-tr-xl">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-50">
            <tr v-for="role in roleList" :key="role.roleId" class="hover:bg-slate-50/80 transition-colors group">
              <td class="px-6 py-4">
                <div class="flex items-center gap-2">
                  <span class="font-bold text-slate-900">{{ role.roleName }}</span>
                  <span v-if="role.realm === 'super_admin'" class="px-2 py-0.5 bg-slate-100 text-slate-500 text-xs font-bold rounded-md uppercase tracking-wider">系统预设</span>
                </div>
              </td>
              <td class="px-6 py-4 text-slate-500 whitespace-normal min-w-[200px]">
                {{ role.description || '暂无描述' }}
              </td>
              <td class="px-6 py-4">
                <span class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-slate-100 text-slate-600 text-xs font-medium">
                  <span class="material-symbols-outlined text-[14px]">group</span>
                  {{ role.userCount }} 人
                </span>
              </td>
              <td class="px-6 py-4 text-right">
                <div class="flex items-center justify-end gap-3">
                  <button
                    class="text-primary hover:text-primary/80 font-bold transition-colors text-xs flex items-center gap-1"
                    @click="$emit('open-drawer', role, 'members')"
                  >
                    <span class="material-symbols-outlined text-[14px]">manage_accounts</span>
                    管理用户
                  </button>
                  <button
                    v-if="role.realm !== 'super_admin'"
                    class="text-primary hover:text-primary/80 font-bold transition-colors text-xs flex items-center gap-1"
                    @click="$emit('open-drawer', role, 'permissions')"
                  >
                    <span class="material-symbols-outlined text-[14px]">admin_panel_settings</span>
                    管理权限
                  </button>
                  <button
                    class="text-slate-400 hover:text-primary font-bold transition-colors text-xs"
                    @click="$emit('edit-role', role)"
                  >
                    编辑
                  </button>
                  <button
                    v-if="role.realm !== 'super_admin'"
                    class="text-slate-400 hover:text-red-600 font-bold transition-colors text-xs"
                    @click="$emit('delete-role', role)"
                  >
                    删除
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { RoleVO } from '@/types/role'

defineProps<{
  roleList: RoleVO[]
  loadingRoles: boolean
}>()

defineEmits<{
  (e: 'add-role'): void
  (e: 'edit-role', role: RoleVO): void
  (e: 'delete-role', role: RoleVO): void
  (e: 'open-drawer', role: RoleVO, tab: 'members' | 'permissions'): void
}>()
</script>
