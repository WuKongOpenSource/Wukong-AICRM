<template>
  <el-dialog
    v-model="dialogVisible"
    :title="editingRole ? '编辑角色' : '创建角色'"
    :width="isMobile ? '95%' : '460px'"
    :fullscreen="isMobile"
    class="wk-dialog--flush"
  >
    <el-form :model="roleForm" label-width="80px">
      <el-form-item label="角色名称" required>
        <el-input v-model="roleForm.roleName" placeholder="请输入角色名称" />
      </el-form-item>
      <el-form-item label="角色描述">
        <el-input v-model="roleForm.description" type="textarea" :rows="3" placeholder="请输入角色描述" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="savingRole" @click="$emit('save')">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { RoleVO } from '@/types/role'

const props = defineProps<{
  visible: boolean
  isMobile: boolean
  editingRole: RoleVO | null
  roleForm: {
    roleName: string
    description: string
  }
  savingRole: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'save'): void
}>()

const dialogVisible = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value)
})
</script>
