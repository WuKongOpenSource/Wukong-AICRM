<template>
  <div>
    <RoleListPanel
      :role-list="roleList"
      :loading-roles="loadingRoles"
      @add-role="handleAddRole"
      @edit-role="handleEditRole"
      @delete-role="handleDeleteRole"
      @open-drawer="openRoleDrawer"
    />

    <RoleDialog
      :visible="showRoleDialog"
      :is-mobile="isMobile"
      :editing-role="editingRole"
      :role-form="roleForm"
      :saving-role="savingRole"
      @update:visible="showRoleDialog = $event"
      @save="handleSaveRole"
    />

    <RoleDrawer
      :visible="showRoleDrawer"
      :role-drawer-role="roleDrawerRole"
      :role-drawer-tab="roleDrawerTab"
      :loading-role-users="loadingRoleUsers"
      :role-user-search="roleUserSearch"
      :filtered-role-users="filteredRoleUsers"
      :loading-permissions="loadingPermissions"
      :permission-list="permissionList"
      :saving-permissions="savingPermissions"
      :data-scope-options="dataScopeOptions"
      :get-avatar-color="getAvatarColor"
      @update:visible="showRoleDrawer = $event"
      @update:tab="roleDrawerTab = $event"
      @update:role-user-search="roleUserSearch = $event"
      @open-add-user="showAddRoleUserDialog = true"
      @remove-user="handleRemoveRoleUser"
      @save-permissions="handleSavePermissions"
    />

    <AddRoleUserDialog
      :visible="showAddRoleUserDialog"
      :is-mobile="isMobile"
      :all-user-search="allUserSearch"
      :available-users-for-role="availableUsersForRole"
      :selected-user-ids="selectedUserIds"
      :adding-role-users="addingRoleUsers"
      :get-avatar-color="getAvatarColor"
      @update:visible="showAddRoleUserDialog = $event"
      @update:all-user-search="allUserSearch = $event"
      @toggle-user="toggleSelectUser"
      @confirm="handleConfirmAddRoleUsers"
    />
  </div>
</template>

<script setup lang="ts">
import AddRoleUserDialog from './components/AddRoleUserDialog.vue'
import RoleDialog from './components/RoleDialog.vue'
import RoleDrawer from './components/RoleDrawer.vue'
import RoleListPanel from './components/RoleListPanel.vue'
import { useRoleManagement } from './useRoleManagement'

const {
  isMobile,
  roleList,
  loadingRoles,
  showRoleDialog,
  savingRole,
  editingRole,
  roleForm,
  showRoleDrawer,
  roleDrawerRole,
  roleDrawerTab,
  permissionList,
  loadingPermissions,
  savingPermissions,
  loadingRoleUsers,
  roleUserSearch,
  filteredRoleUsers,
  showAddRoleUserDialog,
  allUserSearch,
  availableUsersForRole,
  selectedUserIds,
  addingRoleUsers,
  dataScopeOptions,
  getAvatarColor,
  handleAddRole,
  handleEditRole,
  handleSaveRole,
  handleDeleteRole,
  openRoleDrawer,
  handleSavePermissions,
  handleRemoveRoleUser,
  toggleSelectUser,
  handleConfirmAddRoleUsers
} = useRoleManagement()
</script>
