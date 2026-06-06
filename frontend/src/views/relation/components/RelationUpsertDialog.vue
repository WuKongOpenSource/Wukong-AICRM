<template>
  <el-dialog
    v-model="visible"
    :width="isMobile ? 'calc(100% - 32px)' : '720px'"
    :show-close="false"
    destroy-on-close
    :top="isMobile ? '16px' : '10vh'"
    :class="[
      '!rounded-2xl !p-0 overflow-hidden relation-dialog wk-crm-el-field-scope',
      isMobile ? 'relation-dialog--mobile' : 'relation-dialog--desktop'
    ]"
  >
    <template #header>
      <div class="flex items-center justify-between">
        <div class="flex min-w-0 items-center gap-3">
          <div class="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary/10">
            <span class="material-symbols-outlined text-xl text-primary">
              {{ isEdit ? 'edit_note' : 'person_add' }}
            </span>
          </div>
          <div class="min-w-0">
            <h2 class="truncate text-lg font-bold text-slate-900">{{ isEdit ? '编辑关系' : '新建关系' }}</h2>
            <p class="mt-0.5 truncate text-xs text-slate-500">
              {{ isEdit ? '修改关系人基本信息与备注' : '填写关系人基本信息与联系方式' }}
            </p>
          </div>
        </div>
        <button
          class="inline-flex size-9 shrink-0 items-center justify-center rounded-full text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600"
          type="button"
          aria-label="关闭"
          @click="visible = false"
        >
          <span class="material-symbols-outlined text-[22px] leading-none">close</span>
        </button>
      </div>
    </template>

    <div class="space-y-5 bg-white px-5 pb-6 pt-5 md:px-6 md:pb-7 md:pt-6">
      <div>
        <label class="relation-field-label">
          姓名
          <span class="text-red-500">*</span>
        </label>
        <el-input
          v-model="form.name"
          maxlength="100"
          placeholder="请输入姓名"
          size="large"
          class="w-full wk-crm-el-field-input"
        />
      </div>

      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div>
          <label class="relation-field-label">关系类型</label>
          <el-select
            v-model="form.relationType"
            class="w-full wk-crm-el-field-select"
            size="large"
          >
            <el-option
              v-for="option in relationTypeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </div>
        <div>
          <label class="relation-field-label">所属公司</label>
          <el-input
            v-model="form.company"
            maxlength="255"
            placeholder="请输入所属公司"
            size="large"
            class="w-full wk-crm-el-field-input"
          />
        </div>
      </div>

      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div>
          <label class="relation-field-label">手机号</label>
          <el-input
            v-model="form.phone"
            maxlength="50"
            placeholder="请输入手机号"
            size="large"
            class="w-full wk-crm-el-field-input"
          />
        </div>
        <div>
          <label class="relation-field-label">微信号</label>
          <el-input
            v-model="form.wechat"
            maxlength="100"
            placeholder="请输入微信号"
            size="large"
            class="w-full wk-crm-el-field-input"
          />
        </div>
      </div>

      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div>
          <label class="relation-field-label">邮箱</label>
          <el-input
            v-model="form.email"
            maxlength="100"
            placeholder="请输入邮箱"
            size="large"
            class="w-full wk-crm-el-field-input"
          />
        </div>
        <div>
          <label class="relation-field-label">头像 URL</label>
          <el-input
            v-model="form.avatar"
            maxlength="500"
            placeholder="请输入头像 URL"
            size="large"
            class="w-full wk-crm-el-field-input"
          />
        </div>
      </div>

      <div>
        <label class="relation-field-label">备注</label>
        <el-input
          v-model="form.remark"
          type="textarea"
          :rows="4"
          maxlength="1000"
          show-word-limit
          resize="none"
          placeholder="请输入备注信息..."
          class="w-full wk-crm-el-field-input"
        />
      </div>

      <DynamicFieldForm
        ref="dynamicFieldFormRef"
        v-model="customFieldValues"
        entity-type="relation"
        mode="custom"
        :entity-id="isEdit ? editingRelationId : null"
      />
    </div>

    <template #footer>
      <div class="flex gap-3">
        <button
          class="flex-1 rounded-xl bg-slate-100 py-2.5 text-sm font-bold text-slate-600 transition-colors hover:bg-slate-200"
          type="button"
          @click="visible = false"
        >
          取消
        </button>
        <button
          :disabled="!canSave"
          class="flex-1 rounded-xl bg-primary py-2.5 text-sm font-bold text-white shadow-sm transition-colors hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50"
          type="button"
          @click="submitRelation"
        >
          {{ submitting ? '保存中...' : (isEdit ? '确认更新' : '确认创建') }}
        </button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { addRelation, updateRelation } from '@/api/relation'
import DynamicFieldForm from '@/components/DynamicFieldForm.vue'
import { useResponsive } from '@/composables/useResponsive'
import { isRequestErrorHandled } from '@/utils/requestError'
import { relationTypeOptions } from '@/views/relation/constants'
import type { RelationAddBO, RelationUpdateBO, RelationVO } from '@/types/relation'

type RelationUpsertSavedPayload = {
  mode: 'create' | 'edit'
  relationId?: string
}

const props = withDefaults(defineProps<{
  modelValue: boolean
  editingRelation?: RelationVO | null
}>(), {
  editingRelation: null
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'saved', payload: RelationUpsertSavedPayload): void
}>()

const { isMobile } = useResponsive()

const submitting = ref(false)
const dynamicFieldFormRef = ref<InstanceType<typeof DynamicFieldForm> | null>(null)
const customFieldValues = ref<Record<string, unknown>>({})

const form = reactive<RelationAddBO>({
  name: '',
  avatar: '',
  phone: '',
  wechat: '',
  email: '',
  relationType: 'other',
  company: '',
  remark: ''
})

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const isEdit = computed(() => Boolean(props.editingRelation?.relationId))
const editingRelationId = computed(() => props.editingRelation?.relationId || '')
const canSave = computed(() => Boolean(form.name.trim()) && !submitting.value)

watch(
  () => [props.modelValue, props.editingRelation?.relationId] as const,
  ([open]) => {
    if (open) {
      hydrateForm()
    } else {
      resetForm()
    }
  },
  { immediate: true }
)

function resetForm() {
  form.name = ''
  form.avatar = ''
  form.phone = ''
  form.wechat = ''
  form.email = ''
  form.relationType = 'other'
  form.company = ''
  form.remark = ''
  customFieldValues.value = {}
}

function hydrateForm() {
  resetForm()
  const relation = props.editingRelation
  if (!relation) return
  form.name = relation.name || ''
  form.avatar = relation.avatar || relation.avatarUrl || ''
  form.phone = relation.phone || ''
  form.wechat = relation.wechat || ''
  form.email = relation.email || ''
  form.relationType = relation.relationType || 'other'
  form.company = relation.company || ''
  form.remark = relation.remark || ''
  customFieldValues.value = { ...(relation.customFields || {}) }
}

async function submitRelation() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写姓名')
    return
  }
  const missingFields = dynamicFieldFormRef.value?.getRequiredFieldLabels() || []
  if (missingFields.length > 0) {
    ElMessage.warning(`请填写必填字段: ${missingFields.join(', ')}`)
    return
  }
  const uniqueValid = await dynamicFieldFormRef.value?.validateUniqueFields()
  if (uniqueValid === false) return

  submitting.value = true
  try {
    const payload = normalizeRelationPayload()
    if (isEdit.value) {
      await updateRelation({ ...payload, relationId: editingRelationId.value } as RelationUpdateBO)
      ElMessage.success('关系人已更新')
      visible.value = false
      emit('saved', { mode: 'edit', relationId: editingRelationId.value })
      return
    }

    const relationId = await addRelation(payload)
    ElMessage.success('关系人已创建')
    visible.value = false
    emit('saved', { mode: 'create', relationId })
  } catch (error) {
    if (!isRequestErrorHandled(error)) {
      ElMessage.error('保存失败')
    }
  } finally {
    submitting.value = false
  }
}

function normalizeRelationPayload(): RelationAddBO {
  return {
    name: form.name.trim(),
    avatar: form.avatar?.trim() || undefined,
    phone: form.phone?.trim() || undefined,
    wechat: form.wechat?.trim() || undefined,
    email: form.email?.trim() || undefined,
    relationType: form.relationType || 'other',
    company: form.company?.trim() || undefined,
    remark: form.remark?.trim() || undefined,
    customFields: customFieldValues.value
  }
}
</script>

<style>
.relation-dialog .el-dialog__header {
  padding: 22px 24px 16px !important;
  margin-right: 0;
}

.relation-dialog .el-dialog__body {
  padding: 0 !important;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.relation-dialog .el-dialog__footer {
  padding: 14px 24px 22px !important;
}

.relation-dialog.el-dialog {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.relation-dialog--desktop.el-dialog {
  max-height: calc(100vh - 20vh);
  margin-bottom: 10vh;
}

.relation-dialog--mobile.el-dialog {
  height: calc(100vh - 32px);
  max-height: calc(100vh - 32px);
  margin: 16px auto !important;
  border-radius: 1rem !important;
}

.el-overlay:has(.relation-dialog),
.el-overlay-dialog:has(.relation-dialog) {
  overflow: hidden;
}
</style>

<style scoped>
.relation-field-label {
  display: block;
  margin-bottom: 0.375rem;
  font-size: 0.75rem;
  font-weight: 700;
  color: rgb(100 116 139);
}
</style>
