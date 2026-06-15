<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div v-if="dialogVisible" class="fixed inset-0 z-[3600] flex items-center justify-center p-4 sm:p-6">
        <div class="absolute inset-0 bg-slate-900/60 backdrop-blur-sm" @click="dialogVisible = false" />

        <div
          :class="[
            'relative flex w-full flex-col overflow-hidden bg-slate-50 shadow-2xl wk-crm-el-field-scope',
            isMobile ? 'max-h-full max-w-full rounded-[1rem]' : 'max-h-[90vh] max-w-[520px] rounded-[2.5rem]'
          ]"
        >
          <div class="flex shrink-0 items-center justify-between border-b border-slate-200 bg-white px-6 py-4 sm:px-8 sm:py-5">
            <div class="flex min-w-0 items-center gap-3 sm:gap-4">
              <div class="flex size-10 shrink-0 items-center justify-center rounded-2xl bg-primary/10 text-primary sm:size-12">
                <span class="material-symbols-outlined">{{ editingField ? 'edit' : 'add' }}</span>
              </div>
              <div class="min-w-0">
                <h2 class="truncate text-lg font-bold text-slate-900 sm:text-xl">{{ editingField ? '编辑自定义字段' : '添加自定义字段' }}</h2>
                <p class="truncate text-xs text-slate-500">{{ editingField ? '调整字段展示与校验规则' : '配置客户或联系人扩展字段' }}</p>
              </div>
            </div>
            <div class="flex shrink-0 items-center gap-2 sm:gap-3">
              <button
                type="button"
                class="min-w-[3.5rem] whitespace-nowrap rounded-xl px-4 py-2 text-sm font-bold text-slate-600 transition-colors hover:bg-slate-100 sm:px-6 sm:py-2.5"
                @click="dialogVisible = false"
              >
                取消
              </button>
              <button
                type="button"
                class="inline-flex min-w-[4rem] items-center justify-center gap-2 whitespace-nowrap rounded-xl bg-primary px-5 py-2 text-sm font-bold text-white shadow-lg shadow-primary/20 transition-all hover:bg-primary/90 disabled:opacity-50 sm:px-8 sm:py-2.5"
                :disabled="submitting"
                @click="$emit('save')"
              >
                <span v-if="submitting" class="size-3 rounded-full border-2 border-white/30 border-t-white animate-spin"></span>
                <span v-else class="material-symbols-outlined text-sm">save</span>
                保存
              </button>
            </div>
          </div>

          <div class="min-h-0 flex-1 overflow-y-auto p-4 sm:p-6">
            <section class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">
              <el-form :model="fieldForm" label-position="top">
                <el-form-item label="字段标签" required>
                  <el-input v-model="fieldForm.fieldLabel" placeholder="显示名称，如：合同类型" class="w-full wk-crm-el-field-input" size="large" />
                </el-form-item>
                <el-form-item v-if="!editingField" label="字段类型" required>
                  <el-select
                    v-model="fieldForm.fieldType"
                    class="w-full wk-crm-el-field-select"
                    size="large"
                    @change="$emit('field-type-change', $event)"
                  >
                    <el-option label="单行文本" value="text" />
                    <el-option label="多行文本" value="textarea" />
                    <el-option label="数字" value="number" />
                    <el-option label="日期" value="date" />
                    <el-option label="日期时间" value="datetime" />
                    <el-option label="单选下拉" value="select" />
                    <el-option label="多选下拉" value="multiselect" />
                    <el-option label="开关" value="checkbox" />
                  </el-select>
                </el-form-item>
                <el-form-item label="占位提示">
                  <el-input v-model="fieldForm.placeholder" placeholder="输入框提示文字" class="w-full wk-crm-el-field-input" size="large" />
                </el-form-item>
                <el-form-item v-if="!['multiselect', 'checkbox'].includes(fieldForm.fieldType)" label="默认值">
                  <el-input-number
                    v-if="fieldForm.fieldType === 'number'"
                    v-model="fieldForm.defaultValue"
                    placeholder="字段默认值"
                    :controls="false"
                    class="w-full"
                    size="large"
                  />
                  <el-date-picker
                    v-else-if="fieldForm.fieldType === 'date'"
                    v-model="fieldForm.defaultValue"
                    type="date"
                    value-format="YYYY-MM-DD"
                    placeholder="选择默认日期"
                    class="w-full wk-crm-el-field-date"
                    size="large"
                  />
                  <el-date-picker
                    v-else-if="fieldForm.fieldType === 'datetime'"
                    v-model="fieldForm.defaultValue"
                    type="datetime"
                    value-format="YYYY-MM-DD HH:mm:ss"
                    placeholder="选择默认日期时间"
                    class="w-full wk-crm-el-field-date"
                    size="large"
                  />
                  <el-select
                    v-else-if="fieldForm.fieldType === 'select'"
                    v-model="fieldForm.defaultValue"
                    placeholder="选择默认值"
                    class="w-full wk-crm-el-field-select"
                    size="large"
                    clearable
                  >
                    <el-option
                      v-for="option in fieldForm.options.filter((item) => item.value && item.label)"
                      :key="option.value"
                      :label="option.label"
                      :value="option.value"
                    />
                  </el-select>
                  <el-input v-else v-model="fieldForm.defaultValue" placeholder="字段默认值" class="w-full wk-crm-el-field-input" size="large" />
                </el-form-item>
                <el-form-item v-if="fieldForm.fieldType === 'checkbox'" label="默认值">
                  <el-switch v-model="fieldForm.defaultValue" />
                </el-form-item>

                <el-form-item v-if="['select', 'multiselect'].includes(fieldForm.fieldType)" label="选项配置">
                  <div class="w-full space-y-2">
                    <div v-for="(option, index) in fieldForm.options" :key="index" class="flex gap-2">
                      <el-input v-model="option.value" placeholder="值" :disabled="isOptionValueLocked(option)" class="w-1/3 min-w-0 wk-crm-el-field-input" size="large" />
                      <el-input v-model="option.label" placeholder="显示文字" class="min-w-0 flex-1 wk-crm-el-field-input" size="large" />
                      <el-button v-if="!isOptionValueLocked(option)" text type="danger" @click="fieldForm.options.splice(index, 1)">
                        <el-icon><Delete /></el-icon>
                      </el-button>
                    </div>
                    <el-button text @click="fieldForm.options.push({ value: '', label: '' })">
                      <span class="inline-flex items-center gap-1.5">
                        <span class="wk-plus-button-mark" aria-hidden="true">+</span>
                        <span>添加选项</span>
                      </span>
                    </el-button>
                    <p v-if="isSystemField" class="text-xs text-slate-400">系统内置选项的“值”不可修改或删除，可改显示文字或新增选项</p>
                  </div>
                </el-form-item>

                <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
                  <el-form-item label="必填">
                    <el-switch v-model="fieldForm.isRequired" />
                  </el-form-item>
                  <el-form-item label="可搜索">
                    <el-switch v-model="fieldForm.isSearchable" />
                  </el-form-item>
                  <el-form-item label="列表显示">
                    <el-switch v-model="fieldForm.isShowInList" />
                  </el-form-item>
                  <el-form-item label="唯一">
                    <el-switch v-model="fieldForm.isUnique" />
                  </el-form-item>
                </div>
              </el-form>
            </section>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Delete } from '@element-plus/icons-vue'
import type { CustomField, FieldOption, FieldType } from '@/types/customField'

const props = defineProps<{
  visible: boolean
  isMobile: boolean
  editingField: CustomField | null
  fieldForm: {
    fieldLabel: string
    fieldType: FieldType
    placeholder: string
    defaultValue: any
    isRequired: boolean
    isSearchable: boolean
    isShowInList: boolean
    isUnique: boolean
    options: FieldOption[]
  }
  submitting: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'save'): void
  (e: 'field-type-change', value: FieldType): void
}>()

const dialogVisible = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value)
})

const isSystemField = computed(() => props.editingField?.fieldSource === 'system')

// 系统字段：弹窗打开时已存在的选项值视为“内置值”，锁定不可改/删；仅可改标签或新增选项
const lockedOptionValues = ref<Set<string>>(new Set())

watch(
  () => props.visible,
  (visible) => {
    if (visible && isSystemField.value) {
      lockedOptionValues.value = new Set((props.editingField?.options ?? []).map((o) => o.value))
    } else if (!visible) {
      lockedOptionValues.value = new Set()
    }
  },
  { immediate: true }
)

function isOptionValueLocked(option: FieldOption): boolean {
  return isSystemField.value && lockedOptionValues.value.has(option.value)
}
</script>
