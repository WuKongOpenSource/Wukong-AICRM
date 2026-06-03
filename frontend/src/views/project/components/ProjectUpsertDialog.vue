<template>
  <el-dialog
    v-model="open"
    :width="isMobile ? 'calc(100% - 24px)' : '680px'"
    :show-close="false"
    destroy-on-close
    class="wk-dialog--flush wk-project-dialog wk-crm-el-field-scope"
  >
    <template #header>
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-3">
          <div class="flex size-11 items-center justify-center rounded-2xl bg-primary/10 text-primary">
            <span class="material-symbols-outlined text-[22px]">folder_open</span>
          </div>
          <div>
            <h2 class="text-lg font-bold text-slate-900">{{ editingProject ? '编辑项目' : '新建项目' }}</h2>
            <p class="mt-0.5 text-xs text-slate-500">围绕具体项目统一管理任务、AI 沟通和进展。</p>
          </div>
        </div>
        <button
          type="button"
          class="flex size-8 items-center justify-center rounded-full text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700"
          @click="open = false"
        >
          <span class="material-symbols-outlined text-[18px]">close</span>
        </button>
      </div>
    </template>

    <div class="space-y-5 bg-white px-5 pb-6 pt-5 md:px-6 md:pb-7">
      <div>
        <label class="mb-1.5 block text-xs font-bold text-slate-500">项目名称 <span class="text-red-500">*</span></label>
        <el-input v-model="form.name" placeholder="请输入项目名称" size="large" class="wk-crm-el-field-input" />
      </div>

      <div>
        <label class="mb-1.5 block text-xs font-bold text-slate-500">项目描述</label>
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="4"
          resize="none"
          placeholder="补充项目背景、交付内容或关键节点"
          class="wk-crm-el-field-input"
        />
      </div>

      <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
        <div>
          <label class="mb-1.5 block text-xs font-bold text-slate-500">关联客户</label>
          <el-select
            v-model="form.customerId"
            filterable
            remote
            reserve-keyword
            clearable
            default-first-option
            placeholder="搜索客户名称"
            :remote-method="searchCustomers"
            :loading="customerLoading"
            class="wk-crm-el-field-select w-full"
            size="large"
            @change="syncCustomerName"
          >
            <el-option
              v-for="item in customerOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </div>

        <div>
          <label class="mb-1.5 block text-xs font-bold text-slate-500">项目负责人</label>
          <el-select
            v-model="form.ownerId"
            filterable
            remote
            reserve-keyword
            clearable
            default-first-option
            placeholder="搜索成员"
            :remote-method="searchUsers"
            :loading="userLoading"
            class="wk-crm-el-field-select w-full"
            size="large"
            @change="syncOwnerName"
          >
            <el-option
              v-for="item in userOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </div>
      </div>

      <div class="grid grid-cols-1 gap-4 md:grid-cols-3">
        <div>
          <label class="mb-1.5 block text-xs font-bold text-slate-500">开始时间</label>
          <el-date-picker
            v-model="form.startDate"
            type="date"
            value-format="YYYY-MM-DD"
            format="YYYY-MM-DD"
            placeholder="选择日期"
            size="large"
            class="wk-crm-el-field-date w-full"
          />
        </div>
        <div>
          <label class="mb-1.5 block text-xs font-bold text-slate-500">截止时间</label>
          <el-date-picker
            v-model="form.dueDate"
            type="date"
            value-format="YYYY-MM-DD"
            format="YYYY-MM-DD"
            placeholder="选择日期"
            size="large"
            class="wk-crm-el-field-date w-full"
          />
        </div>
        <div>
          <label class="mb-1.5 block text-xs font-bold text-slate-500">项目状态</label>
          <el-select v-model="form.status" class="wk-crm-el-field-select w-full" size="large">
            <el-option
              v-for="item in PROJECT_STATUS_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </div>
      </div>
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
          :disabled="submitting || !form.name.trim()"
          @click="handleSubmit"
        >
          {{ submitting ? '提交中...' : (editingProject ? '保存修改' : '创建项目') }}
        </button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { queryUserList } from '@/api/auth'
import { queryCustomerList } from '@/api/customer'
import { useResponsive } from '@/composables/useResponsive'
import type { ProjectEntity, ProjectStatus } from '@/types/project'
import { PROJECT_STATUS_OPTIONS } from '@/utils/project'

type SelectOption = { value: string; label: string }

const props = withDefaults(defineProps<{
  modelValue: boolean
  editingProject?: ProjectEntity | null
}>(), {
  editingProject: null
})

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'submit', value: {
    name: string
    description?: string
    customerId?: string
    customerName?: string
    ownerId?: string
    ownerName?: string
    startDate?: string
    dueDate?: string
    status: ProjectStatus
  }): void
}>()

const { isMobile } = useResponsive()

const submitting = ref(false)
const customerLoading = ref(false)
const userLoading = ref(false)
const customerOptions = ref<SelectOption[]>([])
const userOptions = ref<SelectOption[]>([])

const form = reactive({
  name: '',
  description: '',
  customerId: '',
  customerName: '',
  ownerId: '',
  ownerName: '',
  startDate: '',
  dueDate: '',
  status: 'NOT_STARTED' as ProjectStatus
})

const open = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const editingProject = computed(() => props.editingProject)

watch(
  () => [props.modelValue, props.editingProject?.projectId] as const,
  ([visible]) => {
    if (!visible) return
    hydrateForm()
  },
  { immediate: true }
)

function hydrateForm() {
  customerOptions.value = []
  userOptions.value = []

  if (editingProject.value) {
    form.name = editingProject.value.name
    form.description = editingProject.value.description || ''
    form.customerId = editingProject.value.customerId || ''
    form.customerName = editingProject.value.customerName || ''
    form.ownerId = editingProject.value.ownerId || ''
    form.ownerName = editingProject.value.ownerName || ''
    form.startDate = editingProject.value.startDate || ''
    form.dueDate = editingProject.value.dueDate || ''
    form.status = editingProject.value.status

    if (form.customerId && form.customerName) {
      customerOptions.value = [{ value: form.customerId, label: form.customerName }]
    }
    if (form.ownerId && form.ownerName) {
      userOptions.value = [{ value: form.ownerId, label: form.ownerName }]
    }
    return
  }

  form.name = ''
  form.description = ''
  form.customerId = ''
  form.customerName = ''
  form.ownerId = ''
  form.ownerName = ''
  form.startDate = ''
  form.dueDate = ''
  form.status = 'NOT_STARTED'
}

async function searchCustomers(query: string) {
  if (!query.trim()) {
    customerOptions.value = []
    return
  }
  customerLoading.value = true
  try {
    const response = await queryCustomerList({ keyword: query.trim(), page: 1, limit: 20 })
    customerOptions.value = (response.list || []).map((item: { customerId: string; companyName?: string }) => ({
      value: String(item.customerId),
      label: item.companyName || ''
    }))
  } finally {
    customerLoading.value = false
  }
}

async function searchUsers(query: string) {
  if (!query.trim()) {
    userOptions.value = []
    return
  }
  userLoading.value = true
  try {
    const response = await queryUserList({ search: query.trim(), page: 1, limit: 20 })
    userOptions.value = (response.list || []).map((item: { userId: string | number; realname?: string; username?: string }) => ({
      value: String(item.userId),
      label: item.realname || item.username || ''
    })).filter((item: SelectOption) => item.label)
  } finally {
    userLoading.value = false
  }
}

function syncCustomerName() {
  const selected = customerOptions.value.find(item => item.value === form.customerId)
  form.customerName = selected?.label || ''
}

function syncOwnerName() {
  const selected = userOptions.value.find(item => item.value === form.ownerId)
  form.ownerName = selected?.label || ''
}

async function handleSubmit() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入项目名称')
    return
  }
  submitting.value = true
  try {
    emit('submit', {
      name: form.name.trim(),
      description: form.description.trim() || undefined,
      customerId: form.customerId || undefined,
      customerName: form.customerName || undefined,
      ownerId: form.ownerId || undefined,
      ownerName: form.ownerName || undefined,
      startDate: form.startDate || undefined,
      dueDate: form.dueDate || undefined,
      status: form.status
    })
    open.value = false
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.wk-project-dialog :deep(.el-dialog__header) {
  padding: 22px 24px 14px;
  margin-right: 0;
}

.wk-project-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.wk-project-dialog :deep(.el-dialog__footer) {
  padding: 14px 24px 22px;
}
</style>
