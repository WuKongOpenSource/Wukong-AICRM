<template>
  <section v-if="visible" :class="sectionClasses">
    <div class="mb-4 flex items-center justify-between">
      <h4 class="flex items-center gap-2 text-sm font-bold text-slate-900">
        <span :class="sectionIconBoxClass" :style="{ backgroundColor: '#1f1e1c' }">
          <WkIcon name="knowledge" :size="14" />
        </span>
        文档
        <button
          v-if="showToggle"
          type="button"
          class="group/module-action relative inline-flex size-7 shrink-0 items-center justify-center rounded-lg bg-white text-slate-500 transition-[background-color,color,border-color] hover:bg-[#efefef] hover:text-[#0d0d0d]"
          :aria-expanded="expanded"
          :aria-label="expanded ? '收起文档' : '展开文档'"
          @click="emit('update:expanded', !expanded)"
        >
          <span class="material-symbols-outlined text-[16px] leading-none">
            {{ expanded ? 'keyboard_arrow_down' : 'keyboard_arrow_right' }}
          </span>
          <span
            class="pointer-events-none absolute left-1/2 top-full z-[200] mt-2 -translate-x-1/2 whitespace-nowrap rounded-lg bg-black px-3 py-1.5 text-[13px] font-medium text-white opacity-0 shadow-md transition-opacity duration-150 group-hover/module-action:opacity-100"
            role="tooltip"
          >
            {{ expanded ? '收起文档' : '展开文档' }}
          </span>
        </button>
      </h4>
      <div class="flex shrink-0 items-center gap-2">
        <button
          v-if="canUpload"
          type="button"
          class="group/module-action relative flex size-7 items-center justify-center rounded-lg border border-slate-200 bg-white text-slate-400 transition-all hover:border-primary/30 hover:bg-[#efefef] hover:text-primary"
          aria-label="上传文档"
          @click="emit('upload')"
        >
          <span class="material-symbols-outlined text-[18px] leading-none">add</span>
          <span
            class="pointer-events-none absolute right-full top-1/2 z-[200] mr-2 -translate-y-1/2 whitespace-nowrap rounded-lg bg-black px-3 py-1.5 text-[13px] font-medium text-white opacity-0 shadow-md transition-opacity duration-150 group-hover/module-action:opacity-100"
            role="tooltip"
          >
            上传文档
          </span>
        </button>
      </div>
    </div>

    <div v-if="isModuleVisible">
      <div v-if="loading" class="space-y-2">
        <div
          v-for="index in 3"
          :key="`document-skeleton-${index}`"
          class="rounded-xl border border-slate-200 bg-white p-3"
        >
          <div class="flex items-start gap-2.5">
            <div class="size-8 shrink-0 animate-pulse rounded-lg bg-slate-100" />
            <div class="min-w-0 flex-1 space-y-2">
              <div class="h-4 w-3/4 animate-pulse rounded-full bg-slate-100" />
              <div class="flex items-center gap-2">
                <div class="h-3 w-12 animate-pulse rounded-full bg-slate-100" />
                <div class="h-3 w-16 animate-pulse rounded-full bg-slate-100" />
                <div class="h-3 w-20 animate-pulse rounded-full bg-slate-100" />
              </div>
            </div>
            <div class="size-7 shrink-0 animate-pulse rounded-full bg-slate-100" />
          </div>
        </div>
      </div>
      <RelatedEmptyState v-else-if="documents.length === 0" icon="folder_off" :text="emptyText" />
      <div v-else class="space-y-2">
        <div
          v-for="item in documents"
          :key="item.knowledgeId"
          class="group rounded-xl border border-slate-200 bg-white p-3 transition-all duration-200 hover:border-primary/30 hover:shadow-md"
          :class="clickable ? 'cursor-pointer' : ''"
          @click="emit('open', item)"
        >
          <div class="flex items-start gap-2.5">
            <FileTypeIcon :file-name="item.name" :mime-type="item.mimeType" :knowledge-type="item.type" size="sm" />
            <div class="min-w-0 flex-1">
              <div class="flex items-start justify-between gap-3">
                <div class="min-w-0 flex-1">
                  <h5 class="truncate text-sm font-bold leading-5 text-slate-900 transition-colors group-hover:text-primary" :title="item.name">
                    {{ item.name || '-' }}
                  </h5>
                  <div class="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-[11px] font-medium leading-4 text-slate-400">
                    <span>{{ getKnowledgeTypeLabel(item.type) }}</span>
                    <span aria-hidden="true">·</span>
                    <span>{{ getKnowledgeFileSizeText(item) }}</span>
                    <span v-if="item.createTime" aria-hidden="true">·</span>
                    <span v-if="item.createTime">上传 {{ formatDateTime(item.createTime) }}</span>
                  </div>
                </div>
                <button
                  type="button"
                  class="group/module-action relative flex size-7 shrink-0 items-center justify-center rounded-full text-slate-400 transition-all hover:bg-primary/10 hover:text-primary"
                  aria-label="下载文档"
                  @click.stop="handleDownload(item)"
                >
                  <span class="material-symbols-outlined text-sm">download</span>
                  <span
                    class="pointer-events-none absolute right-full top-1/2 z-[200] mr-2 -translate-y-1/2 whitespace-nowrap rounded-lg bg-black px-3 py-1.5 text-[13px] font-medium text-white opacity-0 shadow-md transition-opacity duration-150 group-hover/module-action:opacity-100"
                    role="tooltip"
                  >
                    下载文档
                  </span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import FileTypeIcon from '@/components/common/FileTypeIcon.vue'
import { downloadKnowledge } from '@/api/knowledge'
import type { Knowledge } from '@/types/common'
import { formatFileSize, resolveKnowledgeFileSizeBytes } from '@/utils/formatFileSize'
import RelatedEmptyState from './RelatedEmptyState.vue'

const props = withDefaults(defineProps<{
  documents?: Knowledge[]
  loading?: boolean
  visible?: boolean
  embeddedLayout?: boolean
  expanded?: boolean
  canUpload?: boolean
  clickable?: boolean
  emptyText?: string
}>(), {
  documents: () => [],
  loading: false,
  visible: true,
  embeddedLayout: true,
  expanded: true,
  canUpload: true,
  clickable: false,
  emptyText: '暂无文档'
})

const emit = defineEmits<{
  (e: 'update:expanded', value: boolean): void
  (e: 'upload'): void
  (e: 'open', item: Knowledge): void
}>()

const sectionIconBoxClass = 'inline-flex size-7 shrink-0 items-center justify-center rounded-lg text-white shadow-sm'
const showToggle = computed(() => props.loading || props.documents.length > 0)
const isModuleVisible = computed(() => props.expanded || !showToggle.value)
const sectionClasses = computed(() => [
  'group/documents-module',
  props.embeddedLayout
    ? 'mt-5 border-t border-slate-100 pt-5'
    : 'rounded-2xl border border-slate-200 bg-white p-4 shadow-sm'
])

function getKnowledgeTypeLabel(type?: string) {
  const labels: Record<string, string> = {
    meeting: '会议纪要',
    email: '邮件',
    recording: '录音',
    document: '文档',
    proposal: '方案',
    contract: '合同'
  }
  return labels[String(type || '').toLowerCase()] || '文档'
}

function getKnowledgeFileSizeText(item: Knowledge) {
  const formatted = item.fileSizeFormatted?.trim()
  if (formatted) return formatted
  return resolveKnowledgeFileSizeBytes(item.fileSize) > 0 ? formatFileSize(item.fileSize) : '未知大小'
}

function formatDateTime(dateStr?: string) {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  if (Number.isNaN(date.getTime())) return dateStr
  return date.toLocaleString('zh-CN')
}

async function handleDownload(item: Knowledge) {
  try {
    await downloadKnowledge(item.knowledgeId, item.name)
  } catch (error) {
    console.error('Failed to download knowledge:', error)
  }
}
</script>
