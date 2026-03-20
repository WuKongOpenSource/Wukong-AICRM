<template>
  <el-dialog
    v-model="dialogVisible"
    :title="editingDept ? '编辑部门' : '添加部门'"
    :width="isMobile ? '95%' : '420px'"
    :fullscreen="isMobile"
    class="wk-dialog--flush"
  >
    <el-form :model="deptForm" label-width="80px">
      <el-form-item label="部门名称" required>
        <el-input v-model="deptForm.deptName" placeholder="请输入部门名称" />
      </el-form-item>
      <el-form-item label="排序号">
        <el-input-number v-model="deptForm.sortOrder" :min="0" :max="999" class="w-full" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="$emit('save')">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { DeptVO } from '@/types/dept'

const props = defineProps<{
  visible: boolean
  isMobile: boolean
  editingDept: DeptVO | null
  deptForm: {
    deptName: string
    sortOrder: number
  }
  submitting: boolean
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
