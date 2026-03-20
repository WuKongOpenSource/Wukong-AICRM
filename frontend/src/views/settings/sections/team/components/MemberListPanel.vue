<template>
  <div class="flex-1 min-h-0 overflow-y-auto" :class="isMobile ? 'p-4' : 'p-8'">
    <div class="max-w-6xl mx-auto w-full space-y-6">
      <div v-if="isMobile" class="flex items-center gap-2 p-4 border border-slate-100 bg-white rounded-2xl">
        <button
          class="flex items-center gap-2 px-4 py-2 bg-white border border-slate-200 rounded-xl text-sm max-w-[200px]"
          @click="$emit('open-dept-drawer')"
        >
          <span class="material-symbols-outlined text-slate-400 text-base">account_tree</span>
          <span class="truncate">{{ selectedDept ? selectedDept.deptName : '选择部门' }}</span>
        </button>
        <button
          class="px-4 py-2 bg-primary text-white rounded-xl text-sm font-bold flex items-center gap-1 hover:bg-primary/90 transition-all disabled:opacity-50"
          :disabled="!selectedDept"
          @click="$emit('add-member')"
        >
          <span class="material-symbols-outlined wk-plus-button-icon">person_add</span>
          添加成员
        </button>
      </div>

      <div class="flex items-center justify-between">
        <div>
          <h2 class="text-2xl font-bold text-slate-900 truncate">
            {{ selectedDept ? selectedDept.deptName : '组织员工管理' }}
          </h2>
          <p class="text-sm text-slate-500 mt-1">管理您的团队成员、部门分配及账号状态</p>
        </div>
        <button
          v-if="!isMobile"
          class="px-6 py-2.5 bg-primary text-white rounded-xl font-bold shadow-lg shadow-primary/20 hover:bg-primary/90 transition-all flex items-center gap-2 disabled:opacity-50"
          :disabled="!selectedDept"
          @click="$emit('add-member')"
        >
          <span class="material-symbols-outlined wk-plus-button-icon">person_add</span>
          添加员工
        </button>
      </div>

      <div class="grid grid-cols-2 md:grid-cols-4 gap-4 md:gap-6">
        <div class="bg-white p-4 md:p-6 rounded-2xl border border-slate-200 shadow-sm">
          <p class="text-xs font-bold text-slate-400 uppercase tracking-widest mb-2">总员工数</p>
          <p class="text-2xl md:text-3xl font-black text-slate-900">{{ deptMemberList.length }}</p>
        </div>
        <div class="bg-white p-4 md:p-6 rounded-2xl border border-slate-200 shadow-sm">
          <p class="text-xs font-bold text-slate-400 uppercase tracking-widest mb-2">活跃账号</p>
          <p class="text-2xl md:text-3xl font-black text-emerald-500">{{ deptMemberList.filter((member) => member.status === 1).length }}</p>
        </div>
        <div class="bg-white p-4 md:p-6 rounded-2xl border border-slate-200 shadow-sm">
          <p class="text-xs font-bold text-slate-400 uppercase tracking-widest mb-2">待激活</p>
          <p class="text-2xl md:text-3xl font-black text-amber-500">{{ deptMemberList.filter((member) => member.status !== 1 && member.status !== 0).length }}</p>
        </div>
        <div class="bg-white p-4 md:p-6 rounded-2xl border border-slate-200 shadow-sm">
          <p class="text-xs font-bold text-slate-400 uppercase tracking-widest mb-2">部门数量</p>
          <p class="text-2xl md:text-3xl font-black text-primary">{{ deptCount }}</p>
        </div>
      </div>

      <div class="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
        <div v-if="loadingMembers" class="text-center py-16">
          <span class="material-symbols-outlined text-slate-300 text-3xl animate-spin">progress_activity</span>
        </div>
        <template v-else-if="deptMemberList.length === 0">
          <div class="text-center py-16 text-slate-400">
            <span class="material-symbols-outlined text-3xl mb-2 opacity-50">group_off</span>
            <p class="text-sm">{{ selectedDept ? '该部门暂无成员' : '请先选择一个部门' }}</p>
          </div>
        </template>
        <template v-else>
          <div class="p-4 border-b border-slate-100 bg-slate-50/50 flex items-center gap-4">
            <div class="relative flex-1">
              <span class="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 text-sm">search</span>
              <input
                v-model="searchValue"
                type="text"
                placeholder="搜索员工姓名、邮箱或手机号..."
                class="w-full pl-10 pr-4 py-2 bg-white border border-slate-200 rounded-xl text-sm focus:ring-2 focus:ring-primary/50 focus:border-primary/50 outline-none transition-shadow"
              />
            </div>
            <select
              v-model="roleFilterValue"
              class="bg-white border border-slate-200 rounded-xl px-4 py-2 text-sm text-slate-600 outline-none focus:ring-2 focus:ring-primary/50"
            >
              <option :value="'0'">所有角色</option>
              <option v-for="role in allRoleOptions" :key="role.roleId" :value="String(role.roleId)">
                {{ role.roleName }}
              </option>
            </select>
          </div>

          <div class="overflow-x-auto">
            <table class="w-full text-left border-collapse">
              <thead>
                <tr class="border-b border-slate-100">
                  <th class="px-6 py-4 text-xs font-bold text-slate-400 uppercase tracking-widest">员工</th>
                  <th class="px-6 py-4 text-xs font-bold text-slate-400 uppercase tracking-widest">部门</th>
                  <th class="px-6 py-4 text-xs font-bold text-slate-400 uppercase tracking-widest">角色</th>
                  <th class="px-6 py-4 text-xs font-bold text-slate-400 uppercase tracking-widest">联系方式</th>
                  <th class="px-6 py-4 text-xs font-bold text-slate-400 uppercase tracking-widest">状态</th>
                  <th class="px-6 py-4 text-xs font-bold text-slate-400 uppercase tracking-widest text-right">操作</th>
                </tr>
              </thead>
              <tbody v-if="filteredMembers.length > 0" class="divide-y divide-slate-50">
                <tr
                  v-for="member in filteredMembers"
                  :key="member.userId"
                  class="hover:bg-slate-50/50 transition-colors group cursor-pointer"
                  @click="$emit('row-click', member)"
                >
                  <td class="px-6 py-4">
                    <div class="flex items-center gap-3">
                      <img v-if="member.imgUrl" :src="member.imgUrl" class="size-10 rounded-full object-cover shrink-0 shadow-sm" alt="avatar" />
                      <div v-else class="size-10 rounded-full flex items-center justify-center text-white text-sm font-bold shrink-0 shadow-sm" :class="getAvatarColor(member.realname)">
                        {{ (member.realname || member.username || '?').charAt(0) }}
                      </div>
                      <div>
                        <p class="text-sm font-bold text-slate-900 hover:text-primary transition-colors">{{ member.realname || member.username }}</p>
                        <p class="text-xs text-slate-400">{{ member.email || '' }}</p>
                      </div>
                    </div>
                  </td>
                  <td class="px-6 py-4">
                    <p class="text-sm text-slate-700">{{ selectedDept?.deptName || '-' }}</p>
                  </td>
                  <td class="px-6 py-4">
                    <div class="flex flex-wrap gap-1">
                      <span v-for="roleName in member.roleNames || []" :key="roleName" class="px-2 py-0.5 bg-amber-50 text-amber-700 rounded text-xs font-bold">{{ roleName }}</span>
                      <span v-if="!member.roleNames || member.roleNames.length === 0" class="text-sm text-slate-400">-</span>
                    </div>
                  </td>
                  <td class="px-6 py-4">
                    <div>
                      <p v-if="member.mobile" class="text-sm text-slate-700">{{ member.mobile }}</p>
                      <p v-if="member.post" class="text-xs text-slate-400">{{ member.post }}</p>
                    </div>
                  </td>
                  <td class="px-6 py-4">
                    <span
                      class="px-2 py-1 rounded-full text-xs font-bold uppercase tracking-widest"
                      :class="member.status === 1 ? 'bg-emerald-50 text-emerald-600' : member.status === 0 ? 'bg-red-50 text-red-500' : 'bg-slate-100 text-slate-400'"
                    >
                      {{ member.status === 1 ? '活跃' : member.status === 0 ? '禁用' : '未激活' }}
                    </span>
                  </td>
                  <td class="px-6 py-4 text-right">
                    <div class="flex items-center justify-end gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                      <button
                        class="size-8 flex items-center justify-center rounded-lg hover:bg-white hover:shadow-sm text-slate-400 hover:text-primary transition-all"
                        title="编辑"
                        @click.stop="$emit('edit-member', member)"
                      >
                        <span class="material-symbols-outlined text-sm">edit</span>
                      </button>
                      <button
                        class="size-8 flex items-center justify-center rounded-lg hover:bg-white hover:shadow-sm text-slate-400 hover:text-amber-500 transition-all"
                        :title="member.status === 1 ? '停用' : '启用'"
                        @click.stop="$emit('toggle-status', member)"
                      >
                        <span class="material-symbols-outlined text-sm">{{ member.status === 1 ? 'block' : 'check_circle' }}</span>
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
              <tbody v-else>
                <tr>
                  <td colspan="6" class="text-center py-16 text-slate-400">
                    <span class="material-symbols-outlined text-3xl mb-2 opacity-50">search_off</span>
                    <div class="text-sm">未找到匹配的员工</div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { DeptVO } from '@/types/dept'
import type { RoleVO } from '@/types/role'

const props = defineProps<{
  isMobile: boolean
  selectedDept: DeptVO | null
  deptMemberList: any[]
  filteredMembers: any[]
  loadingMembers: boolean
  memberSearch: string
  memberRoleId: string
  allRoleOptions: RoleVO[]
  deptCount: number
  getAvatarColor: (name: string) => string
}>()

const emit = defineEmits<{
  (e: 'update:memberSearch', value: string): void
  (e: 'update:memberRoleId', value: string): void
  (e: 'open-dept-drawer'): void
  (e: 'add-member'): void
  (e: 'row-click', member: any): void
  (e: 'edit-member', member: any): void
  (e: 'toggle-status', member: any): void
}>()

const searchValue = computed({
  get: () => props.memberSearch,
  set: (value: string) => emit('update:memberSearch', value)
})

const roleFilterValue = computed({
  get: () => props.memberRoleId,
  set: (value: string) => emit('update:memberRoleId', value)
})
</script>
