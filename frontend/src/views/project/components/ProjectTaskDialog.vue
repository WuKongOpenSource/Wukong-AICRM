<template>
  <el-dialog
    v-model="open"
    :width="isMobile ? 'calc(100% - 24px)' : '720px'"
    :show-close="false"
    destroy-on-close
    class="wk-dialog--flush wk-project-task-dialog wk-crm-el-field-scope"
  >
    <template #header>
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-3">
          <div class="flex size-11 items-center justify-center rounded-2xl bg-primary/10 text-primary">
            <span class="material-symbols-outlined text-[22px]">task_alt</span>
          </div>
          <div>
            <h2 class="text-lg font-bold text-slate-900">{{ editingTask ? '编辑项目任务' : '新建项目任务' }}</h2>
            <p class="mt-0.5 text-xs text-slate-500">{{ editingTask ? '修改项目任务详细信息' : '手动填写或使用 AI 智能解析' }}</p>
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
      <div v-if="!editingTask" class="space-y-3 rounded-2xl border border-[var(--wk-input-border)] bg-white p-3">
        <div class="flex items-center gap-2">
          <WkIcon name="ai" class="text-primary text-sm" />
          <span class="text-xs font-bold text-primary">AI 智能解析（可选）</span>
        </div>
        <div class="relative">
          <el-input
            v-model="aiParseInput"
            type="textarea"
            :rows="5"
            resize="none"
            placeholder="例如：明天下午两点前给科技创新有限公司的张总发一份 Q4 扩容方案的报价单，标记为高优先级..."
            class="wk-crm-el-field-input wk-crm-el-field-ai w-full"
          />
          <button
            type="button"
            class="absolute bottom-3 right-3 flex items-center gap-1.5 rounded-lg bg-slate-800 px-3 py-1.5 text-xs font-bold text-white transition-colors hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-50"
            :disabled="!aiParseInput.trim() || aiParsing"
            @click="handleAiParse"
          >
            <span v-if="aiParsing" class="material-symbols-outlined animate-spin text-sm">progress_activity</span>
            <WkIcon v-else name="ai" class="text-sm" />
            {{ aiParsing ? '解析中...' : '一键解析' }}
          </button>
        </div>
      </div>

      <div>
        <label class="mb-1.5 block text-xs font-bold text-slate-500">任务名称 <span class="text-red-500">*</span></label>
        <el-input v-model="form.title" placeholder="请输入任务名称" size="large" class="wk-crm-el-field-input" />
      </div>

      <div>
        <label class="mb-1.5 block text-xs font-bold text-slate-500">任务描述</label>
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="4"
          resize="none"
          placeholder="补充任务说明、产出要求或备注"
          class="wk-crm-el-field-input"
        />
      </div>

      <div>
        <div class="mb-2 flex items-center justify-between gap-3">
          <label class="block text-xs font-bold text-slate-500">附件</label>
          <button
            type="button"
            class="inline-flex items-center gap-1.5 rounded-lg border border-[var(--wk-input-border)] bg-white px-3 py-1.5 text-xs font-semibold text-slate-600 transition-colors hover:border-slate-300 hover:bg-slate-50"
            :disabled="taskAttachmentUploading"
            @click="taskAttachmentFileInputRef?.click()"
          >
            <span class="material-symbols-outlined text-[16px] leading-none">attach_file</span>
            上传附件
          </button>
          <input
            ref="taskAttachmentFileInputRef"
            type="file"
            multiple
            class="hidden"
            @change="handleTaskAttachmentFileChange"
          />
        </div>
        <div
          v-if="selectedAttachmentFiles.length || editingTask?.attachments?.length"
          class="space-y-2 rounded-2xl border border-[var(--wk-input-border)] bg-white p-3"
        >
          <article
            v-for="attachment in editingTask?.attachments || []"
            :key="attachment.attachmentId"
            class="flex items-center gap-3 rounded-xl bg-slate-50 px-3 py-2"
          >
            <span class="material-symbols-outlined text-[18px] text-slate-400">draft</span>
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-medium text-slate-700">{{ attachment.name }}</p>
              <p class="text-xs text-slate-400">已上传</p>
            </div>
          </article>
          <article
            v-for="(file, index) in selectedAttachmentFiles"
            :key="`${file.name}-${file.size}-${index}`"
            class="flex items-center gap-3 rounded-xl bg-slate-50 px-3 py-2"
          >
            <span class="material-symbols-outlined text-[18px] text-slate-400">upload_file</span>
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-medium text-slate-700">{{ file.name }}</p>
              <p class="text-xs text-slate-400">{{ formatFileSize(file.size) }}</p>
            </div>
            <button
              type="button"
              class="flex size-7 items-center justify-center rounded-lg text-slate-400 transition-colors hover:bg-slate-100 hover:text-red-500"
              :disabled="taskAttachmentUploading"
              @click="removeTaskAttachmentFile(index)"
            >
              <span class="material-symbols-outlined text-[16px] leading-none">close</span>
            </button>
          </article>
        </div>
        <p v-else class="rounded-2xl border border-dashed border-[var(--wk-input-border)] px-3 py-3 text-xs text-slate-400">
          暂无附件
        </p>
      </div>

      <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
        <div>
          <label class="mb-1.5 block text-xs font-bold text-slate-500">所属泳道</label>
          <el-select v-model="form.laneId" class="wk-crm-el-field-select w-full" size="large">
            <el-option
              v-for="lane in lanes"
              :key="lane.laneId"
              :label="lane.name"
              :value="lane.laneId"
            />
          </el-select>
        </div>
        <div>
          <label class="mb-1.5 block text-xs font-bold text-slate-500">优先级</label>
          <el-select v-model="form.priority" class="wk-crm-el-field-select w-full" size="large">
            <el-option
              v-for="item in PROJECT_TASK_PRIORITY_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </div>
      </div>

      <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
        <div>
          <label class="mb-1.5 block text-xs font-bold text-slate-500">截止时间</label>
          <el-date-picker
            v-model="form.dueDate"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            format="YYYY-MM-DD HH:mm"
            placeholder="选择日期时间"
            size="large"
            class="wk-crm-el-field-date w-full"
          />
        </div>
        <div>
          <label class="mb-1.5 block text-xs font-bold text-slate-500">负责人</label>
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

      <div>
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
      </div>

      <div>
        <label class="mb-1.5 block text-xs font-bold text-slate-500">任务参与人</label>
        <el-select
          v-model="participantIds"
          multiple
          filterable
          remote
          reserve-keyword
          clearable
          default-first-option
          placeholder="搜索并添加参与人"
          :remote-method="searchUsers"
          :loading="userLoading"
          class="wk-crm-el-field-select w-full"
          size="large"
          @change="syncParticipantNames"
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
          :disabled="submitting || taskAttachmentUploading || !form.title.trim()"
          @click="handleSubmit"
        >
          {{ submitting ? '提交中...' : (editingTask ? '保存修改' : '创建任务') }}
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
import { getPresignedUploadUrl, uploadToMinIO } from '@/api/file'
import { aiParseTask } from '@/api/task'
import { useResponsive } from '@/composables/useResponsive'
import type { ProjectLane, ProjectTask, ProjectTaskAttachmentPayload, ProjectTaskPriority } from '@/types/project'
import { PROJECT_TASK_PRIORITY_OPTIONS } from '@/utils/project'
import { formatFileSize } from '@/utils/formatFileSize'
import { isRequestErrorHandled } from '@/utils/requestError'

type SelectOption = { value: string; label: string }
type UserListItem = { userId: string | number; realname?: string; username?: string }
type CustomerListItem = { customerId: string | number; companyName?: string }

const props = withDefaults(defineProps<{
  modelValue: boolean
  lanes: ProjectLane[]
  editingTask?: ProjectTask | null
  defaultLaneId?: string
  defaultCustomerId?: string
  defaultCustomerName?: string
}>(), {
  editingTask: null,
  defaultLaneId: '',
  defaultCustomerId: '',
  defaultCustomerName: ''
})

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'submit', value: {
    title: string
    description?: string
    laneId: string
    dueDate?: string
    ownerId?: string
    ownerName?: string
    participantIds?: string[]
    participantNames?: string[]
    priority: ProjectTaskPriority
    customerId?: string
    customerName?: string
    hasAttachments: boolean
    hasSchedule: boolean
    attachments?: ProjectTaskAttachmentPayload[]
  }): void
}>()

const { isMobile } = useResponsive()

const submitting = ref(false)
const aiParsing = ref(false)
const aiParseInput = ref('')
const userLoading = ref(false)
const customerLoading = ref(false)
const userOptions = ref<SelectOption[]>([])
const customerOptions = ref<SelectOption[]>([])
const participantIds = ref<string[]>([])
const participantNames = ref<string[]>([])
const selectedAttachmentFiles = ref<File[]>([])
const taskAttachmentFileInputRef = ref<HTMLInputElement | null>(null)
const taskAttachmentUploading = ref(false)

const form = reactive({
  title: '',
  description: '',
  laneId: '',
  dueDate: '',
  ownerId: '',
  ownerName: '',
  priority: 'MEDIUM' as ProjectTaskPriority,
  customerId: '',
  customerName: '',
  hasAttachments: false,
  hasSchedule: false
})

const open = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const editingTask = computed(() => props.editingTask)

watch(
  () => [props.modelValue, props.editingTask?.taskId, props.defaultLaneId] as const,
  ([visible]) => {
    if (!visible) return
    hydrateForm()
  },
  { immediate: true }
)

function hydrateForm() {
  aiParseInput.value = ''
  userOptions.value = []
  customerOptions.value = []
  selectedAttachmentFiles.value = []

  if (editingTask.value) {
    form.title = editingTask.value.title
    form.description = editingTask.value.description || ''
    form.laneId = editingTask.value.laneId
    form.dueDate = formatDateTimeLocal(editingTask.value.dueDate)
    form.ownerId = editingTask.value.ownerId || ''
    form.ownerName = editingTask.value.ownerName || ''
    form.priority = editingTask.value.priority
    form.customerId = editingTask.value.customerId || ''
    form.customerName = editingTask.value.customerName || ''
    form.hasAttachments = editingTask.value.hasAttachments
    form.hasSchedule = editingTask.value.hasSchedule
    participantIds.value = editingTask.value.participantIds || []
    participantNames.value = editingTask.value.participantNames || []

    if (form.ownerId && form.ownerName) {
      userOptions.value = [{ value: form.ownerId, label: form.ownerName }]
    }
    if (participantIds.value.length) {
      userOptions.value = [
        ...userOptions.value,
        ...participantIds.value.map((value, index) => ({
          value,
          label: participantNames.value[index] || value
        }))
      ].filter((item, index, list) => list.findIndex(target => target.value === item.value) === index)
    }
    if (form.customerId && form.customerName) {
      customerOptions.value = [{ value: form.customerId, label: form.customerName }]
    }
    return
  }

  form.title = ''
  form.description = ''
  form.laneId = props.defaultLaneId || props.lanes[0]?.laneId || ''
  form.dueDate = ''
  form.ownerId = ''
  form.ownerName = ''
  form.priority = 'MEDIUM'
  form.customerId = props.defaultCustomerId || ''
  form.customerName = props.defaultCustomerName || ''
  form.hasAttachments = false
  form.hasSchedule = false
  participantIds.value = []
  participantNames.value = []

  if (form.customerId && form.customerName) {
    customerOptions.value = [{ value: form.customerId, label: form.customerName }]
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
    userOptions.value = (response.list || []).map((item: UserListItem) => ({
      value: String(item.userId),
      label: item.realname || item.username || ''
    })).filter((item: SelectOption) => item.label)
  } finally {
    userLoading.value = false
  }
}

async function searchCustomers(query: string) {
  if (!query.trim()) {
    customerOptions.value = []
    return
  }
  customerLoading.value = true
  try {
    const response = await queryCustomerList({ keyword: query.trim(), page: 1, limit: 20 })
    customerOptions.value = (response.list || []).map((item: CustomerListItem) => ({
      value: String(item.customerId),
      label: item.companyName || ''
    }))
  } finally {
    customerLoading.value = false
  }
}

function syncOwnerName() {
  const selected = userOptions.value.find(item => item.value === form.ownerId)
  form.ownerName = selected?.label || ''
}

function syncCustomerName() {
  const selected = customerOptions.value.find(item => item.value === form.customerId)
  form.customerName = selected?.label || ''
}

function syncParticipantNames() {
  participantNames.value = participantIds.value.map(value =>
    userOptions.value.find(item => item.value === value)?.label || value
  )
}

function handleTaskAttachmentFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  if (!files.length) return

  const existingKeys = new Set(selectedAttachmentFiles.value.map(file => `${file.name}-${file.size}-${file.lastModified}`))
  const toAdd = files.filter(file => !existingKeys.has(`${file.name}-${file.size}-${file.lastModified}`))
  if (!toAdd.length) {
    ElMessage.warning('所选附件已在列表中')
    return
  }
  selectedAttachmentFiles.value = [...selectedAttachmentFiles.value, ...toAdd]
}

function removeTaskAttachmentFile(index: number) {
  selectedAttachmentFiles.value.splice(index, 1)
}

async function uploadTaskAttachmentFiles(): Promise<ProjectTaskAttachmentPayload[]> {
  if (!selectedAttachmentFiles.value.length) return []

  taskAttachmentUploading.value = true
  try {
    const files = [...selectedAttachmentFiles.value]
    const uploads = await Promise.all(files.map(async (file) => {
      const presigned = await getPresignedUploadUrl(file.name, file.type)
      await uploadToMinIO(file, presigned.uploadUrl)
      return {
        fileName: file.name,
        filePath: presigned.objectKey,
        fileSize: file.size,
        mimeType: file.type || 'application/octet-stream'
      }
    }))
    selectedAttachmentFiles.value = []
    return uploads
  } catch (error) {
    console.error('项目任务附件上传失败:', error)
    if (!isRequestErrorHandled(error)) {
      ElMessage.error('附件上传失败，请重试')
    }
    throw error
  } finally {
    taskAttachmentUploading.value = false
  }
}

async function handleAiParse() {
  const content = aiParseInput.value.trim()
  if (!content) return

  aiParsing.value = true
  try {
    const result = await aiParseTask(content)
    if (result.title) form.title = result.title
    if (result.description) form.description = result.description
    if (result.dueDate) form.dueDate = toProjectDateTimeValue(result.dueDate)
    if (result.priority) form.priority = normalizeProjectTaskPriority(result.priority)
    if (result.customerName) await applyParsedCustomer(result.customerName)
    if (result.assignedToName) await applyParsedOwner(result.assignedToName)
    if (result.participantNames) await applyParsedParticipants(result.participantNames)
    ElMessage.success('AI 解析完成，请确认后再创建任务')
  } catch (error) {
    console.error('AI parse project task failed:', error)
    ElMessage.error('AI 解析失败，请稍后重试')
  } finally {
    aiParsing.value = false
  }
}

function normalizeProjectTaskPriority(value: string): ProjectTaskPriority {
  const normalized = value.trim().toUpperCase()
  if (normalized === 'URGENT' || value.includes('紧急')) return 'URGENT'
  if (normalized === 'HIGH' || value.includes('高')) return 'HIGH'
  if (normalized === 'LOW' || value.includes('低')) return 'LOW'
  return 'MEDIUM'
}

function toProjectDateTimeValue(value: string) {
  const trimmed = value.trim()
  if (!trimmed) return ''
  const normalized = trimmed.includes('T') ? trimmed : trimmed.replace(' ', 'T')
  return normalized.length === 16 ? `${normalized}:00` : normalized
}

function splitParsedNames(value: string) {
  return value
    .split(/[、,，;；\s]+/)
    .map(item => item.trim())
    .filter(Boolean)
}

async function applyParsedCustomer(customerName: string) {
  const keyword = customerName.trim()
  if (!keyword) return
  const response = await queryCustomerList({ keyword, page: 1, limit: 5 })
  const customers = response.list || []
  customerOptions.value = customers.map((item: CustomerListItem) => ({
    value: String(item.customerId),
    label: item.companyName || ''
  }))
  const matched = customerOptions.value.find(item => item.label === keyword) || customerOptions.value[0]
  if (matched) {
    form.customerId = matched.value
    form.customerName = matched.label
  } else {
    form.customerId = ''
    form.customerName = keyword
  }
}

async function applyParsedOwner(ownerName: string) {
  const matched = await findUserByName(ownerName)
  if (!matched) return
  mergeUserOption(matched)
  form.ownerId = matched.value
  form.ownerName = matched.label
}

async function applyParsedParticipants(namesText: string) {
  const names = splitParsedNames(namesText)
  if (!names.length) return

  const ids: string[] = []
  const namesForSubmit: string[] = []
  for (const name of names) {
    const matched = await findUserByName(name)
    if (matched) {
      mergeUserOption(matched)
      ids.push(matched.value)
      namesForSubmit.push(matched.label)
    } else {
      namesForSubmit.push(name)
    }
  }
  participantIds.value = ids
  participantNames.value = namesForSubmit
}

async function findUserByName(name: string): Promise<SelectOption | null> {
  const keyword = name.trim()
  if (!keyword) return null
  const response = await queryUserList({ search: keyword, page: 1, limit: 5 })
  const options: SelectOption[] = (response.list || []).map((item: UserListItem) => ({
    value: String(item.userId),
    label: item.realname || item.username || ''
  })).filter((item: SelectOption) => item.label)
  return options.find(item => item.label === keyword) || options[0] || null
}

function mergeUserOption(option: SelectOption) {
  if (userOptions.value.some(item => item.value === option.value)) return
  userOptions.value = [...userOptions.value, option]
}

async function handleSubmit() {
  if (!form.title.trim()) {
    ElMessage.warning('请输入任务名称')
    return
  }
  submitting.value = true
  try {
    const uploadedAttachments = await uploadTaskAttachmentFiles()
    emit('submit', {
      title: form.title.trim(),
      description: form.description.trim() || undefined,
      laneId: form.laneId,
      dueDate: form.dueDate || undefined,
      ownerId: form.ownerId || undefined,
      ownerName: form.ownerName || undefined,
      participantIds: participantIds.value,
      participantNames: participantNames.value,
      priority: form.priority,
      customerId: form.customerId || undefined,
      customerName: form.customerName || undefined,
      hasAttachments: form.hasAttachments || uploadedAttachments.length > 0 || Boolean(editingTask.value?.attachments?.length),
      hasSchedule: form.hasSchedule,
      attachments: uploadedAttachments.length ? uploadedAttachments : undefined
    })
    open.value = false
  } finally {
    submitting.value = false
  }
}

function formatDateTimeLocal(value?: string) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  const second = String(date.getSeconds()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}T${hour}:${minute}:${second}`
}
</script>

<style scoped>
:global(.wk-project-task-dialog.el-dialog) {
  display: flex;
  max-height: min(760px, calc(100dvh - 32px)) !important;
  flex-direction: column;
  overflow: hidden;
  margin: 0 auto !important;
}

:global(.wk-project-task-dialog.el-dialog .el-dialog__header) {
  flex: 0 0 auto;
  padding: 22px 24px 14px;
  margin-right: 0;
}

:global(.wk-project-task-dialog.el-dialog .el-dialog__body) {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 0 !important;
}

:global(.wk-project-task-dialog.el-dialog .el-dialog__footer) {
  flex: 0 0 auto;
  border-top: 1px solid var(--wk-border-subtle);
  background: #fff;
  padding: 14px 24px 22px !important;
}

:global(.el-overlay:has(.wk-project-task-dialog)),
:global(.el-overlay-dialog:has(.wk-project-task-dialog)) {
  overflow: hidden;
}

:global(.el-overlay-dialog:has(.wk-project-task-dialog)) {
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  padding: 16px;
}

@media (max-width: 767px) {
  :global(.wk-project-task-dialog.el-dialog) {
    max-height: calc(100dvh - 24px) !important;
    margin: 0 auto !important;
  }

  :global(.el-overlay-dialog:has(.wk-project-task-dialog)) {
    padding: 12px;
  }
}
</style>
