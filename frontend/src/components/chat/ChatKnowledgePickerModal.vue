<template>
  <el-dialog
    v-model="dialogVisible"
    width="880px"
    align-center
    append-to-body
    destroy-on-close
    class="wk-chat-knowledge-picker-dialog"
    :show-close="false"
    @closed="onDialogClosed"
  >
    <template #header>
      <div class="wk-chat-knowledge-picker__header">
        <div class="min-w-0 flex-1">
          <h2 class="wk-chat-knowledge-picker__title">选择知识库内容</h2>
          <p class="wk-chat-knowledge-picker__subtitle">选择文件或分类作为 AI 回复的参考背景</p>
        </div>
        <button
          type="button"
          class="wk-chat-knowledge-picker__close"
          aria-label="关闭"
          @click="dialogVisible = false"
        >
          <span class="material-symbols-outlined wk-chat-knowledge-picker__close-icon">close</span>
        </button>
      </div>
    </template>

    <div class="wk-chat-knowledge-picker__body">
      <aside class="wk-chat-knowledge-picker__sidebar">
        <div class="wk-chat-knowledge-picker__sidebar-inner">
          <button
            v-for="cat in categories"
            :key="cat.id"
            type="button"
            class="wk-chat-knowledge-picker__cat"
            :class="{ 'wk-chat-knowledge-picker__cat--active': activeCategory === cat.id }"
            @click="setCategory(cat.id)"
          >
            <span class="material-symbols-outlined wk-chat-knowledge-picker__cat-icon">{{ cat.icon }}</span>
            <span class="wk-chat-knowledge-picker__cat-label">{{ cat.label }}</span>
            <span
              class="material-symbols-outlined wk-chat-knowledge-picker__cat-check"
              :class="{ fill: activeCategory === cat.id }"
              aria-hidden="true"
            >
              {{ activeCategory === cat.id ? 'check_box' : 'check_box_outline_blank' }}
            </span>
          </button>
        </div>
      </aside>

      <div class="wk-chat-knowledge-picker__main">
        <div class="wk-chat-knowledge-picker__search">
          <span class="material-symbols-outlined wk-chat-knowledge-picker__search-icon">search</span>
          <input
            v-model="keywordInput"
            type="search"
            class="wk-chat-knowledge-picker__search-input"
            placeholder="搜索文档..."
            autocomplete="off"
          />
        </div>

        <div v-loading="listLoading" class="wk-chat-knowledge-picker__list-wrap">
          <div v-if="!listLoading && rows.length === 0" class="wk-chat-knowledge-picker__empty">
            暂无文档
          </div>
          <button
            v-for="row in rows"
            :key="row.knowledgeId"
            type="button"
            class="wk-chat-knowledge-picker__row"
            :class="{ 'wk-chat-knowledge-picker__row--selected': selectedIds.has(row.knowledgeId) }"
            @click="toggleRow(row.knowledgeId)"
          >
            <div class="wk-chat-knowledge-picker__row-type-wrap">
              <span class="material-symbols-outlined wk-chat-knowledge-picker__row-type-icon">
                {{ fileTypeIcon(row) }}
              </span>
            </div>
            <div class="min-w-0 flex-1 text-left">
              <div class="wk-chat-knowledge-picker__row-title">{{ row.name }}</div>
              <div class="wk-chat-knowledge-picker__row-meta">{{ knowledgeRowTag(row) }}</div>
            </div>
            <span
              class="material-symbols-outlined wk-chat-knowledge-picker__row-radio"
              :class="selectedIds.has(row.knowledgeId) ? 'wk-chat-knowledge-picker__row-radio--on' : ''"
            >
              {{ selectedIds.has(row.knowledgeId) ? 'check_circle' : 'radio_button_unchecked' }}
            </span>
          </button>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="wk-chat-knowledge-picker__footer">
        <div class="wk-chat-knowledge-picker__count">
          已选择
          <span class="wk-chat-knowledge-picker__count-pill">{{ selectedIds.size }} 项</span>
        </div>
        <div class="wk-chat-knowledge-picker__footer-actions">
          <button type="button" class="wk-chat-knowledge-picker__btn-cancel" @click="dialogVisible = false">
            取消
          </button>
          <button
            type="button"
            class="wk-chat-knowledge-picker__btn-confirm"
            :disabled="selectedIds.size === 0"
            @click="handleConfirm"
          >
            确定
          </button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { queryKnowledgeList } from '@/api/knowledge'
import type { Knowledge, KnowledgeQueryBO } from '@/types/common'
import { MAX_CHAT_ATTACHMENT_COUNT } from '@/utils/chatAttachment'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    /** 聊天框还可附加的条目上限（与已有附件/知识库选择合计 ≤ MAX） */
    remainingSlots?: number
  }>(),
  { remainingSlots: MAX_CHAT_ATTACHMENT_COUNT }
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: [items: Knowledge[]]
}>()

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v)
})

const categories = [
  { id: 'all', label: '全部', icon: 'grid_view' },
  { id: 'document', label: '产品文档', icon: 'description' },
  { id: 'proposal', label: '方案资料', icon: 'lightbulb' },
  { id: 'meeting', label: '会议记录', icon: 'calendar_month' },
  { id: 'contract', label: '合同文件', icon: 'assignment' }
] as const

type CategoryId = (typeof categories)[number]['id']

const activeCategory = ref<CategoryId>('all')
const keywordInput = ref('')
const debouncedKeyword = ref('')
let keywordTimer: ReturnType<typeof setTimeout> | null = null

const rows = ref<Knowledge[]>([])
const listLoading = ref(false)
const selectedIds = ref<Set<string>>(new Set())
const idToRow = ref<Map<string, Knowledge>>(new Map())

const selectCap = computed(() => Math.max(0, props.remainingSlots))

watch(keywordInput, () => {
  if (keywordTimer) clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => {
    keywordTimer = null
    debouncedKeyword.value = keywordInput.value.trim()
  }, 320)
})

watch(debouncedKeyword, () => {
  if (dialogVisible.value) void fetchList()
})

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      keywordInput.value = ''
      debouncedKeyword.value = ''
      activeCategory.value = 'all'
      selectedIds.value = new Set()
      void fetchList()
    }
  }
)

watch(activeCategory, () => {
  if (dialogVisible.value) void fetchList()
})

function onDialogClosed() {
  selectedIds.value = new Set()
  rows.value = []
  idToRow.value = new Map()
}

function setCategory(id: CategoryId) {
  activeCategory.value = id
}

function toggleRow(id: string) {
  const next = new Set(selectedIds.value)
  if (next.has(id)) next.delete(id)
  else {
    if (next.size >= selectCap.value) {
      ElMessage.warning(
        selectCap.value <= 0
          ? '附件数量已达上限'
          : `最多还可选择 ${selectCap.value} 个文件（合计不超过 ${MAX_CHAT_ATTACHMENT_COUNT} 个）`
      )
      return
    }
    next.add(id)
  }
  selectedIds.value = next
}

function typeBadge(raw: string | undefined): string {
  const t = (raw || '').toLowerCase()
  const map: Record<string, string> = {
    document: 'PRODUCT',
    proposal: 'SOLUTION',
    meeting: 'MEETING',
    contract: 'CONTRACT',
    email: 'EMAIL',
    recording: 'RECORDING'
  }
  return map[t] || (t ? t.toUpperCase() : 'DOC')
}

function knowledgeRowTag(row: Knowledge): string {
  return typeBadge(row.type)
}

function fileTypeIcon(row: Knowledge): string {
  const mime = (row.mimeType || '').toLowerCase()
  if (mime.includes('pdf')) return 'picture_as_pdf'
  if (mime.startsWith('video/')) return 'smart_display'
  if (mime.startsWith('image/')) return 'image'
  return 'description'
}

async function fetchList() {
  listLoading.value = true
  try {
    const query = {
      page: 1,
      limit: 80,
      keyword: debouncedKeyword.value || undefined,
      type: activeCategory.value === 'all' ? undefined : activeCategory.value
    } as KnowledgeQueryBO
    const result = await queryKnowledgeList(query)
    const list = result.list || []
    rows.value = list
    const m = new Map<string, Knowledge>()
    for (const k of list) m.set(k.knowledgeId, k)
    for (const id of selectedIds.value) {
      if (!m.has(id)) {
        const prev = idToRow.value.get(id)
        if (prev) m.set(id, prev)
      }
    }
    idToRow.value = m
  } catch {
    rows.value = []
    idToRow.value = new Map()
  } finally {
    listLoading.value = false
  }
}

function handleConfirm() {
  const ids = [...selectedIds.value]
  if (ids.length === 0) return
  if (ids.length > selectCap.value) {
    ElMessage.warning(`还可再选 ${selectCap.value} 个文件（合计不超过 ${MAX_CHAT_ATTACHMENT_COUNT} 个）`)
    return
  }
  const items = ids.map(id => idToRow.value.get(id)).filter((r): r is Knowledge => Boolean(r))
  if (items.length === 0) {
    ElMessage.warning('未能找到所选文档')
    return
  }
  emit('confirm', items)
  dialogVisible.value = false
}
</script>

<style scoped>
.wk-chat-knowledge-picker__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-right: 2px;
}

.wk-chat-knowledge-picker__title {
  margin: 0;
  font-size: 19px;
  font-weight: 700;
  color: var(--wk-text-primary);
  line-height: 1.35;
  letter-spacing: 0;
}

.wk-chat-knowledge-picker__subtitle {
  margin: 8px 0 0;
  font-size: 14px;
  font-weight: 400;
  color: var(--wk-text-muted);
  line-height: 1.5;
}

.wk-chat-knowledge-picker__close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  margin: -4px -6px 0 0;
  padding: 0;
  border: none;
  border-radius: 10px;
  background: transparent;
  cursor: pointer;
  flex-shrink: 0;
}

.wk-chat-knowledge-picker__close-icon {
  font-size: 22px;
  line-height: 1;
  color: var(--wk-text-muted);
  font-variation-settings:
    'FILL' 0,
    'wght' 400,
    'GRAD' 0,
    'opsz' 24;
}

.wk-chat-knowledge-picker__close:hover {
  background: var(--wk-bg-surface-hover);
}

.wk-chat-knowledge-picker__close:hover .wk-chat-knowledge-picker__close-icon {
  color: var(--wk-text-secondary);
}

.wk-chat-knowledge-picker__body {
  display: flex;
  gap: 0;
  min-height: 432px;
  max-height: min(568px, 72vh);
  border-radius: 16px;
  overflow: hidden;
  background: var(--wk-bg-surface);
  border: 0px solid #ebebeb;
}

.wk-chat-knowledge-picker__sidebar {
  width: 28%;
  min-width: 196px;
  max-width: 252px;
  flex-shrink: 0;
  border-right: 1px solid var(--wk-border-subtle);
  background: var(--wk-bg-surface-subtle);
  overflow-y: auto;
}

.wk-chat-knowledge-picker__sidebar-inner {
  padding: 12px 10px 14px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.wk-chat-knowledge-picker__cat {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 11px 12px;
  border: none;
  border-radius: 10px;
  background: transparent;
  cursor: pointer;
  text-align: left;
  font-size: 14px;
  color: var(--wk-text-secondary);
  transition:
    background 0.18s ease,
    color 0.18s ease;
}

.wk-chat-knowledge-picker__cat:hover {
  background: var(--wk-bg-surface-hover);
}

.wk-chat-knowledge-picker__cat--active {
  background: var(--wk-bg-surface-active);
  color: var(--wk-text-primary);
}

.wk-chat-knowledge-picker__cat--active:hover {
  background: var(--wk-bg-surface-active);
}

.wk-chat-knowledge-picker__cat--active .wk-chat-knowledge-picker__cat-label {
  color: var(--wk-text-primary);
  font-weight: 600;
}

.wk-chat-knowledge-picker__cat-icon {
  font-size: 22px;
  line-height: 1;
  color: var(--wk-text-muted);
  flex-shrink: 0;
  font-variation-settings:
    'FILL' 0,
    'wght' 400,
    'GRAD' 0,
    'opsz' 24;
}

.wk-chat-knowledge-picker__cat--active .wk-chat-knowledge-picker__cat-icon {
  color: var(--wk-text-primary);
}

.wk-chat-knowledge-picker__cat-label {
  flex: 1;
  min-width: 0;
  line-height: 1.35;
}

.wk-chat-knowledge-picker__cat-check {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  font-size: 20px;
  line-height: 1;
  flex-shrink: 0;
  color: var(--wk-text-faint);
  font-variation-settings:
    'FILL' 0,
    'wght' 400,
    'GRAD' 0,
    'opsz' 24;
}

.wk-chat-knowledge-picker__cat-check.fill {
  color: var(--wk-text-primary);
}

.wk-chat-knowledge-picker__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--wk-bg-surface);
}

.wk-chat-knowledge-picker__search {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 16px 20px 12px;
  padding: 0 16px;
  height: 44px;
  border-radius: var(--wk-input-radius);
  background: var(--wk-input-bg);
  border: 1px solid var(--wk-input-border);
  box-shadow: var(--wk-input-shadow);
  transition:
    border-color 0.2s ease,
    background 0.2s ease,
    box-shadow 0.2s ease;
}

.wk-chat-knowledge-picker__search-icon {
  font-size: 20px;
  line-height: 1;
  color: var(--wk-text-faint);
  flex-shrink: 0;
  font-variation-settings:
    'FILL' 0,
    'wght' 400,
    'GRAD' 0,
    'opsz' 24;
}

.wk-chat-knowledge-picker__search:hover {
  border-color: var(--wk-input-border-hover);
}

.wk-chat-knowledge-picker__search:focus-within {
  border-color: var(--wk-input-border-focus);
  background: var(--wk-input-bg);
  box-shadow: var(--wk-input-focus-shadow);
}

.wk-chat-knowledge-picker__search-input {
  appearance: none;
  -webkit-appearance: none;
  flex: 1;
  height: 100%;
  min-width: 0;
  border: none;
  background: transparent;
  box-shadow: none;
  font-size: 14px;
  color: var(--wk-text-primary);
  outline: none;
}

input.wk-chat-knowledge-picker__search-input:not(.el-input__inner):not(.el-select__input):not([type="checkbox"]):not([type="radio"]):not([type="range"]):not([type="file"]):not([type="hidden"]),
input.wk-chat-knowledge-picker__search-input:not(.el-input__inner):not(.el-select__input):not([type="checkbox"]):not([type="radio"]):not([type="range"]):not([type="file"]):not([type="hidden"]):hover,
input.wk-chat-knowledge-picker__search-input:not(.el-input__inner):not(.el-select__input):not([type="checkbox"]):not([type="radio"]):not([type="range"]):not([type="file"]):not([type="hidden"]):focus {
  box-shadow: none !important;
}

.wk-chat-knowledge-picker__search-input::placeholder {
  color: var(--wk-text-faint);
}

.wk-chat-knowledge-picker__list-wrap {
  flex: 1;
  overflow-y: auto;
  padding: 4px 16px 18px;
  scrollbar-gutter: stable;
}

.wk-chat-knowledge-picker__empty {
  padding: 52px 16px;
  text-align: center;
  font-size: 14px;
  color: var(--wk-text-muted);
}

.wk-chat-knowledge-picker__row {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  padding: 14px 16px;
  margin-bottom: 10px;
  border: 1px solid var(--wk-border-subtle);
  border-radius: 12px;
  background: var(--wk-bg-surface);
  cursor: pointer;
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    background 0.18s ease;
}

.wk-chat-knowledge-picker__row:last-child {
  margin-bottom: 0;
}

.wk-chat-knowledge-picker__row:hover {
  border-color: var(--wk-border-muted);
  box-shadow: 0 2px 8px rgb(var(--wk-shadow-color) / 0.06);
}

.wk-chat-knowledge-picker__row--selected {
  border-color: var(--wk-border-strong);
  background: var(--wk-bg-surface-hover);
  box-shadow: 0 2px 10px rgb(var(--wk-shadow-color) / 0.08);
}

.wk-chat-knowledge-picker__row-type-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  border-radius: 10px;
  background: var(--wk-bg-surface-subtle);
}

.wk-chat-knowledge-picker__row-type-icon {
  font-size: 26px;
  line-height: 1;
  color: var(--wk-text-muted);
  font-variation-settings:
    'FILL' 0,
    'wght' 400,
    'GRAD' 0,
    'opsz' 24;
}

.wk-chat-knowledge-picker__row-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--wk-text-primary);
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wk-chat-knowledge-picker__row-meta {
  margin-top: 5px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  color: var(--wk-text-faint);
  text-transform: uppercase;
}

.wk-chat-knowledge-picker__row-radio {
  font-size: 24px;
  line-height: 1;
  flex-shrink: 0;
  color: var(--wk-text-faint);
  font-variation-settings:
    'FILL' 0,
    'wght' 400,
    'GRAD' 0,
    'opsz' 24;
}

.wk-chat-knowledge-picker__row-radio--on {
  color: var(--wk-text-primary);
  font-variation-settings:
    'FILL' 1,
    'wght' 400,
    'GRAD' 0,
    'opsz' 24;
}

.wk-chat-knowledge-picker__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
}

.wk-chat-knowledge-picker__count {
  font-size: 14px;
  color: var(--wk-text-secondary);
}

.wk-chat-knowledge-picker__count-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 26px;
  margin-left: 8px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  background: var(--wk-bg-surface-hover);
  color: var(--wk-text-primary);
}

.wk-chat-knowledge-picker__footer-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.wk-chat-knowledge-picker__btn-cancel {
  border: none;
  background: transparent;
  font-size: 14px;
  color: var(--wk-text-muted);
  cursor: pointer;
  padding: 10px 8px;
  border-radius: 8px;
}

.wk-chat-knowledge-picker__btn-cancel:hover {
  color: var(--wk-text-primary);
  background: var(--wk-bg-surface-hover);
}

.wk-chat-knowledge-picker__btn-confirm {
  border: none;
  border-radius: 10px;
  padding: 10px 28px;
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  background: var(--wk-primary);
  cursor: pointer;
  box-shadow: 0 4px 12px rgb(var(--wk-primary-rgb) / 0.24);
  transition:
    background 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;
}

.wk-chat-knowledge-picker__btn-confirm:hover:not(:disabled) {
  background: color-mix(in srgb, var(--wk-primary) 88%, white);
  box-shadow: 0 6px 16px rgb(var(--wk-primary-rgb) / 0.28);
}

.wk-chat-knowledge-picker__btn-confirm:active:not(:disabled) {
  transform: translateY(1px);
}

.wk-chat-knowledge-picker__btn-confirm:disabled {
  opacity: 0.45;
  cursor: not-allowed;
  box-shadow: none;
}

@media (max-width: 767px) {
  .wk-chat-knowledge-picker__header {
    align-items: center;
    gap: 12px;
  }

  .wk-chat-knowledge-picker__title {
    font-size: 17px;
  }

  .wk-chat-knowledge-picker__subtitle {
    display: none;
  }

  .wk-chat-knowledge-picker__close {
    width: 34px;
    height: 34px;
    margin: 0 -4px 0 0;
    border-radius: 9999px;
    background: var(--wk-bg-surface-hover);
  }

  .wk-chat-knowledge-picker__body {
    min-height: 0;
    max-height: min(62dvh, 520px);
    flex-direction: column;
    border-radius: 0;
  }

  .wk-chat-knowledge-picker__sidebar {
    width: 100%;
    min-width: 0;
    max-width: none;
    border-right: 0;
    border-bottom: 1px solid var(--wk-border-subtle);
    overflow-x: auto;
    overflow-y: hidden;
    background: var(--wk-bg-surface);
  }

  .wk-chat-knowledge-picker__sidebar-inner {
    width: max-content;
    min-width: 100%;
    flex-direction: row;
    gap: 8px;
    padding: 10px 12px;
  }

  .wk-chat-knowledge-picker__cat {
    width: auto;
    flex: 0 0 auto;
    gap: 6px;
    border: 1px solid var(--wk-border-subtle);
    border-radius: 9999px;
    padding: 9px 12px;
    white-space: nowrap;
  }

  .wk-chat-knowledge-picker__cat--active {
    border-color: var(--wk-border-strong);
    box-shadow: 0 2px 8px rgb(var(--wk-shadow-color) / 0.08);
  }

  .wk-chat-knowledge-picker__cat-icon {
    font-size: 19px;
  }

  .wk-chat-knowledge-picker__cat-check {
    display: none;
  }

  .wk-chat-knowledge-picker__main {
    min-height: 0;
  }

  .wk-chat-knowledge-picker__search {
    height: 42px;
    margin: 12px 12px 10px;
    padding: 0 12px;
    border-radius: 14px;
    background: transparent;
    box-shadow: none;
  }

  .wk-chat-knowledge-picker__search:focus-within {
    background: transparent;
    box-shadow: none;
  }

  .wk-chat-knowledge-picker__list-wrap {
    padding: 0 12px 12px;
  }

  .wk-chat-knowledge-picker__row {
    gap: 10px;
    margin-bottom: 8px;
    padding: 12px;
    border-radius: 12px;
  }

  .wk-chat-knowledge-picker__row-type-wrap {
    width: 40px;
    height: 40px;
  }

  .wk-chat-knowledge-picker__row-type-icon {
    font-size: 23px;
  }

  .wk-chat-knowledge-picker__row-radio {
    font-size: 22px;
  }

  .wk-chat-knowledge-picker__footer {
    flex-wrap: nowrap;
    gap: 12px;
  }

  .wk-chat-knowledge-picker__count {
    flex: 1;
    min-width: 0;
    white-space: nowrap;
  }

  .wk-chat-knowledge-picker__count-pill {
    margin-left: 4px;
    padding: 0 9px;
  }

  .wk-chat-knowledge-picker__footer-actions {
    flex-shrink: 0;
    gap: 8px;
  }

  .wk-chat-knowledge-picker__btn-cancel {
    padding: 10px 6px;
  }

  .wk-chat-knowledge-picker__btn-confirm {
    padding: 10px 18px;
  }
}
</style>

<style>
.wk-chat-knowledge-picker-dialog.el-dialog {
  padding: 0;
  border-radius: 24px;
  overflow: hidden;
  border: 1px solid var(--wk-border-subtle);
  box-shadow:
    0 24px 48px rgb(var(--wk-shadow-color) / 0.12),
    0 8px 16px rgb(var(--wk-shadow-color) / 0.06);
}

.wk-chat-knowledge-picker-dialog .el-dialog__header {
  padding: 24px 24px 16px;
  margin: 0;
  border-bottom: 1px solid var(--wk-border-subtle);
}

.wk-chat-knowledge-picker-dialog .el-dialog__body {
  padding: 16px 24px 8px;
}

.wk-chat-knowledge-picker-dialog .el-dialog__footer {
  padding: 16px 24px 22px;
  border-top: 1px solid var(--wk-border-subtle);
}

@media (max-width: 767px) {
  .wk-chat-knowledge-picker-dialog.el-dialog {
    width: calc(100vw - 24px) !important;
    max-height: calc(100dvh - 24px);
    margin: 12px auto !important;
    border-radius: 20px;
  }

  .wk-chat-knowledge-picker-dialog .el-dialog__header {
    padding: 18px 16px 12px;
  }

  .wk-chat-knowledge-picker-dialog .el-dialog__body {
    padding: 0;
  }

  .wk-chat-knowledge-picker-dialog .el-dialog__footer {
    padding: 12px 14px max(14px, env(safe-area-inset-bottom));
  }
}
</style>
