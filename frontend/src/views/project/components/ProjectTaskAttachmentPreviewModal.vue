<template>
  <Teleport to="body">
    <Transition name="fade">
      <div
        v-if="modelValue"
        class="fixed inset-0 z-[3600] bg-slate-900/60 backdrop-blur-sm"
        @click="close"
      />
    </Transition>

    <Transition name="scale-fade">
      <div
        v-if="modelValue && attachment"
        class="fixed inset-0 z-[3601] flex items-center justify-center p-4"
      >
        <div
          :class="[
            'flex w-full overflow-hidden bg-white shadow-2xl',
            isMobile ? 'h-full rounded-none' : 'h-[90vh] max-w-5xl rounded-[2.5rem]'
          ]"
          @click.stop
        >
          <div class="flex min-w-0 flex-1 flex-col overflow-hidden">
            <div class="flex shrink-0 items-center justify-between border-b border-slate-100 p-6 pb-4">
              <div class="flex min-w-0 items-center gap-3">
                <div class="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
                  <span class="material-symbols-outlined text-lg">{{ attachmentIcon }}</span>
                </div>
                <div class="min-w-0">
                  <h3 class="truncate text-base font-bold text-slate-900">
                    {{ attachment.name || '附件预览' }}
                  </h3>
                  <p class="text-xs font-medium tracking-wide text-slate-400">
                    {{ attachment.createTime ? `上传于 ${formatDateTime(attachment.createTime)}` : '附件预览' }}
                    <span v-if="attachment.fileSize" class="ml-2">{{ formatFileSize(attachment.fileSize) }}</span>
                    <span v-if="previewStatusLabel" class="ml-2">{{ previewStatusLabel }}</span>
                  </p>
                </div>
              </div>

              <div class="flex shrink-0 items-center gap-2">
                <button
                  class="flex size-8 items-center justify-center rounded-full text-slate-400 transition-colors hover:bg-slate-100"
                  title="下载文件"
                  @click="handleDownload"
                >
                  <span class="material-symbols-outlined text-lg">download</span>
                </button>
                <button
                  class="flex size-8 items-center justify-center rounded-full text-slate-400 transition-colors hover:bg-slate-100"
                  title="关闭"
                  @click="close"
                >
                  <span class="material-symbols-outlined text-lg">close</span>
                </button>
              </div>
            </div>

            <div class="relative flex-1 overflow-hidden">
              <div
                v-if="loadingPreview"
                class="absolute inset-0 z-10 flex items-center justify-center bg-white"
              >
                <span class="material-symbols-outlined animate-spin text-3xl text-slate-300">
                  progress_activity
                </span>
              </div>

              <div
                v-else-if="showImagePreview"
                class="media-preview-shell media-preview-shell--image h-full overflow-auto bg-slate-950"
              >
                <img
                  :src="mediaPreviewUrl || undefined"
                  :alt="attachment.name"
                  class="media-preview-image"
                >
              </div>

              <div
                v-else-if="showAudioPreview"
                class="media-preview-shell flex h-full items-center justify-center bg-slate-950 p-6"
              >
                <audio
                  :src="mediaPreviewUrl || undefined"
                  controls
                  preload="metadata"
                  class="w-full max-w-3xl"
                  @error="handleMediaPreviewError"
                />
              </div>

              <div
                v-else-if="showVideoPreview"
                class="media-preview-shell flex h-full items-center justify-center bg-slate-950 p-6"
              >
                <video
                  :src="mediaPreviewUrl || undefined"
                  controls
                  preload="metadata"
                  class="media-preview-video"
                  @error="handleMediaPreviewError"
                />
              </div>

              <div
                v-else-if="showPdfPreview"
                class="pdf-preview-shell h-full overflow-auto bg-white"
              >
                <KnowledgePdfPreview
                  :blob="previewBlob"
                  class="pdf-preview-viewer min-h-full"
                  @rendered="handlePreviewRendered"
                  @error="handlePreviewError"
                />
              </div>

              <div
                v-else-if="showOfficePreview"
                :class="[
                  'office-preview-shell h-full overflow-auto bg-slate-100',
                  previewKind === 'pptx' ? 'office-preview-shell--pptx' : ''
                ]"
              >
                <component
                  :is="activePreviewComponent"
                  :src="previewSource"
                  :class="[
                    'office-preview-viewer min-h-full',
                    previewKind === 'pptx' ? 'office-preview-viewer--pptx' : ''
                  ]"
                  @rendered="handlePreviewRendered"
                  @error="handlePreviewError"
                />
              </div>

              <div
                v-else-if="showDocHtmlPreview"
                class="doc-html-preview-shell h-full overflow-auto bg-white"
              >
                <iframe
                  ref="docIframeRef"
                  sandbox="allow-same-origin"
                  class="h-full w-full border-0"
                  title="Document preview"
                />
              </div>

              <div v-else class="h-full overflow-y-auto p-6">
                <div
                  v-if="previewNotice || previewFailed"
                  class="mb-4 flex items-center gap-2 rounded-xl border border-amber-200 bg-amber-50 p-3"
                >
                  <span class="material-symbols-outlined text-sm text-amber-500">info</span>
                  <span class="text-xs text-amber-700">
                    {{ previewNotice || '预览不可用，已显示可读取的文本内容。' }}
                  </span>
                </div>

                <div
                  v-if="displayedText"
                  class="whitespace-pre-wrap text-sm leading-relaxed text-slate-700"
                >
                  {{ displayedText }}
                </div>

                <div
                  v-else
                  class="flex h-full flex-col items-center justify-center text-slate-400"
                >
                  <span class="material-symbols-outlined mb-2 text-4xl">description</span>
                  <p class="text-sm">当前文件暂无可直接展示的内容</p>
                  <button
                    class="mt-3 rounded-xl bg-primary px-4 py-2 text-xs text-white transition-colors hover:bg-primary/90"
                    @click="handleDownload"
                  >
                    下载文件查看
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import DOMPurify from 'dompurify'
import { useResponsive } from '@/composables/useResponsive'
import {
  downloadProjectTaskAttachment,
  getProjectTaskAttachmentBlob,
  getProjectTaskAttachmentPreviewHtml
} from '@/api/project'
import { formatFileSize } from '@/utils/formatFileSize'
import { formatDateTime } from '@/utils/project'
import type { ProjectTaskAttachment } from '@/types/project'

type PreviewKind =
  | 'image'
  | 'audio'
  | 'video'
  | 'doc'
  | 'docx'
  | 'excel'
  | 'pdf'
  | 'pptx'
  | 'text'
  | 'unsupported'
  | 'none'

const VueOfficeDocx = defineAsyncComponent(async () => {
  await import('@vue-office/docx/lib/v3/index.css')
  const module = await import('@vue-office/docx/lib/v3/index.js')
  return module.default
})

const VueOfficeExcel = defineAsyncComponent(async () => {
  await import('@vue-office/excel/lib/v3/index.css')
  const module = await import('@vue-office/excel/lib/v3/index.js')
  return module.default
})

const VueOfficePptx = defineAsyncComponent(async () => {
  const module = await import('@vue-office/pptx/lib/v3/index.js')
  return module.default
})

const KnowledgePdfPreview = defineAsyncComponent(async () => {
  const module = await import('@/components/knowledge/KnowledgePdfPreview.vue')
  return module.default
})

const props = defineProps<{
  modelValue: boolean
  attachment: ProjectTaskAttachment | null
  projectId: string
  taskId: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const { isMobile } = useResponsive()

const previewBlob = ref<Blob | null>(null)
const officePreviewSource = ref<Blob | ArrayBuffer | null>(null)
const mediaPreviewUrl = ref('')
const previewKind = ref<PreviewKind>('none')
const previewNotice = ref('')
const previewText = ref('')
const previewFailed = ref(false)
const loadingPreview = ref(false)
const docHtmlContent = ref('')
const docIframeRef = ref<HTMLIFrameElement | null>(null)

let previewLoadToken = 0

const activePreviewComponent = computed(() => {
  switch (previewKind.value) {
    case 'docx':
      return VueOfficeDocx
    case 'excel':
      return VueOfficeExcel
    case 'pptx':
      return VueOfficePptx
    default:
      return null
  }
})

const previewSource = computed(() => officePreviewSource.value ?? undefined)
const showImagePreview = computed(() => previewKind.value === 'image' && Boolean(mediaPreviewUrl.value) && !previewFailed.value)
const showAudioPreview = computed(() => previewKind.value === 'audio' && Boolean(mediaPreviewUrl.value) && !previewFailed.value)
const showVideoPreview = computed(() => previewKind.value === 'video' && Boolean(mediaPreviewUrl.value) && !previewFailed.value)
const showPdfPreview = computed(() => previewKind.value === 'pdf' && Boolean(previewBlob.value) && !previewFailed.value)
const showOfficePreview = computed(() => Boolean(activePreviewComponent.value && previewSource.value && !previewFailed.value))
const showDocHtmlPreview = computed(() => previewKind.value === 'doc' && Boolean(docHtmlContent.value) && !previewFailed.value)
const displayedText = computed(() => previewText.value)

const attachmentIcon = computed(() => getAttachmentIcon(props.attachment))

const previewStatusLabel = computed(() => {
  switch (previewKind.value) {
    case 'image':
      return '图片预览'
    case 'audio':
      return '音频预览'
    case 'video':
      return '视频预览'
    case 'pdf':
      return 'PDF 预览'
    case 'doc':
    case 'docx':
      return '文档预览'
    case 'excel':
      return '表格预览'
    case 'pptx':
      return '演示预览'
    case 'text':
      return '文本预览'
    case 'unsupported':
      return '暂不支持预览'
    default:
      return ''
  }
})

function resetPreviewState() {
  previewBlob.value = null
  officePreviewSource.value = null
  previewKind.value = 'none'
  previewNotice.value = ''
  previewText.value = ''
  previewFailed.value = false
  docHtmlContent.value = ''

  if (mediaPreviewUrl.value) {
    URL.revokeObjectURL(mediaPreviewUrl.value)
    mediaPreviewUrl.value = ''
  }
}

function close() {
  emit('update:modelValue', false)
}

function getFileExtension(filename?: string): string {
  const normalized = filename?.trim().toLowerCase() || ''
  const lastDotIndex = normalized.lastIndexOf('.')
  return lastDotIndex >= 0 ? normalized.slice(lastDotIndex + 1) : ''
}

function normalizeContentType(contentType?: string): string {
  return (contentType || '').split(';', 1)[0]?.trim().toLowerCase() || ''
}

function isImageType(extension: string, mimeType: string): boolean {
  return mimeType.startsWith('image/') || ['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp', 'svg', 'avif'].includes(extension)
}

function isAudioType(extension: string, mimeType: string): boolean {
  return mimeType.startsWith('audio/') || ['mp3', 'wav', 'm4a', 'aac', 'ogg', 'oga', 'opus', 'flac', 'weba'].includes(extension)
}

function isVideoType(extension: string, mimeType: string): boolean {
  return mimeType.startsWith('video/') || ['mp4', 'webm', 'mov', 'm4v', 'avi', 'mkv', 'ogv', '3gp'].includes(extension)
}

function resolvePreviewKind(filename?: string, mimeType?: string): PreviewKind {
  const extension = getFileExtension(filename)
  const normalizedType = normalizeContentType(mimeType)

  if (isImageType(extension, normalizedType)) return 'image'
  if (isAudioType(extension, normalizedType)) return 'audio'
  if (isVideoType(extension, normalizedType)) return 'video'
  if (extension === 'pdf' || normalizedType === 'application/pdf') return 'pdf'
  if (
    extension === 'docx' ||
    normalizedType === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
  ) {
    return 'docx'
  }
  if (
    extension === 'xlsx' ||
    extension === 'xls' ||
    normalizedType === 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' ||
    normalizedType === 'application/vnd.ms-excel'
  ) {
    return 'excel'
  }
  if (
    extension === 'pptx' ||
    normalizedType === 'application/vnd.openxmlformats-officedocument.presentationml.presentation'
  ) {
    return 'pptx'
  }
  if (
    ['txt', 'md', 'markdown', 'json', 'xml', 'html', 'htm', 'csv'].includes(extension) ||
    normalizedType.startsWith('text/') ||
    normalizedType === 'text/csv'
  ) {
    return 'text'
  }
  if (extension === 'doc' || normalizedType === 'application/msword') {
    return 'doc'
  }
  return 'unsupported'
}

async function tryReadJsonBlob(blob: Blob): Promise<{ msg?: string } | null> {
  const contentType = normalizeContentType(blob.type)
  if (contentType !== 'application/json') return null

  try {
    return JSON.parse(await blob.text()) as { msg?: string }
  } catch (error) {
    console.warn('Failed to parse JSON blob response:', error)
    return null
  }
}

function getUnsupportedNotice(filename?: string): string {
  const extension = getFileExtension(filename)
  if (extension === 'ppt') {
    return '当前旧版 Office 格式暂不支持纯前端预览，请下载后查看。'
  }
  if (extension === 'xls') {
    return '当前 Excel 文件渲染兼容性有限，如预览异常请下载后查看。'
  }
  return '当前文件类型暂不支持纯前端预览，请下载后查看。'
}

function handlePreviewRendered() {
  previewFailed.value = false
}

function handlePreviewError(error: unknown) {
  console.error('Project task attachment preview render failed:', error)
  previewBlob.value = null
  officePreviewSource.value = null
  previewFailed.value = true
  previewNotice.value = previewNotice.value || '文件渲染失败，已切换为文本内容。'
}

function handleMediaPreviewError(error: Event) {
  console.error('Project task attachment media preview failed:', error)
  previewFailed.value = true
  previewNotice.value = '浏览器无法播放该音视频格式，请下载后查看。'
}

async function loadPreview(attachment: ProjectTaskAttachment) {
  const currentToken = ++previewLoadToken
  loadingPreview.value = true
  resetPreviewState()

  try {
    if (!props.projectId || !props.taskId || !attachment.attachmentId) {
      previewFailed.value = true
      previewNotice.value = '附件信息不完整，无法加载预览。'
      return
    }

    const kind = resolvePreviewKind(attachment.name, attachment.mimeType)
    previewKind.value = kind

    if (kind === 'unsupported') {
      previewFailed.value = true
      previewNotice.value = getUnsupportedNotice(attachment.name)
      return
    }

    if (kind === 'doc') {
      try {
        const html = await getProjectTaskAttachmentPreviewHtml(props.projectId, props.taskId, attachment.attachmentId)
        if (currentToken !== previewLoadToken) return
        docHtmlContent.value = html
      } catch (error) {
        console.error('Load project task attachment HTML preview failed:', error)
        if (currentToken !== previewLoadToken) return
        previewFailed.value = true
        previewNotice.value = '文档转换失败，已显示可读取的文本内容。'
      }
      return
    }

    const fileBlob = await getProjectTaskAttachmentBlob(props.projectId, props.taskId, attachment.attachmentId)
    if (currentToken !== previewLoadToken) return

    const errorPayload = await tryReadJsonBlob(fileBlob)
    if (currentToken !== previewLoadToken) return
    if (errorPayload) {
      previewFailed.value = true
      previewNotice.value = errorPayload.msg || '文件内容读取失败，无法加载预览。'
      return
    }

    if (kind === 'text') {
      previewText.value = await fileBlob.text()
      if (currentToken !== previewLoadToken) return
      if (!previewText.value) {
        previewFailed.value = true
        previewNotice.value = '当前文本文件暂无可展示的内容。'
      }
      return
    }

    if (kind === 'image' || kind === 'audio' || kind === 'video') {
      mediaPreviewUrl.value = URL.createObjectURL(fileBlob)
      return
    }

    previewBlob.value = kind === 'pdf' ? fileBlob : null
    officePreviewSource.value = kind === 'pdf'
      ? null
      : kind === 'pptx'
        ? await fileBlob.arrayBuffer()
        : fileBlob
  } catch (error) {
    console.error('Failed to load project task attachment preview:', error)
    if (currentToken !== previewLoadToken) return
    previewFailed.value = true
    previewNotice.value = '文件预览加载失败，请稍后重试。'
  } finally {
    if (currentToken === previewLoadToken) {
      loadingPreview.value = false
    }
  }
}

watch(
  () => [props.modelValue, props.attachment?.attachmentId, props.projectId, props.taskId],
  async ([visible, attachmentId]) => {
    if (visible && attachmentId && props.attachment) {
      await loadPreview(props.attachment)
    } else if (!visible) {
      previewLoadToken += 1
      resetPreviewState()
    }
  },
  { immediate: true }
)

watch(
  [showDocHtmlPreview, docHtmlContent],
  ([show, html]) => {
    if (show && html) {
      nextTick(() => {
        const iframe = docIframeRef.value
        if (!iframe) return

        const sanitized = DOMPurify.sanitize(html, {
          WHOLE_DOCUMENT: true,
          ADD_TAGS: ['style', 'link'],
          ADD_ATTR: ['style', 'class', 'color', 'face', 'size'],
          ALLOW_DATA_ATTR: false
        })

        const doc = iframe.contentDocument
        if (doc) {
          doc.open()
          doc.write(sanitized)
          doc.close()

          const baseStyle = doc.createElement('style')
          baseStyle.textContent =
            'body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; ' +
            'padding: 24px 32px; line-height: 1.6; max-width: 100%; overflow-wrap: break-word; }' +
            'img { max-width: 100%; height: auto; }' +
            'table { border-collapse: collapse; width: 100%; margin: 1em 0; }' +
            'td, th { border: 1px solid #e2e8f0; padding: 6px 10px; }'

          if (doc.head) {
            doc.head.insertBefore(baseStyle, doc.head.firstChild)
          } else {
            doc.documentElement?.prepend(baseStyle)
          }
        }
      })
    }
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  previewLoadToken += 1
  resetPreviewState()
})

async function handleDownload() {
  if (!props.attachment?.attachmentId || !props.projectId || !props.taskId) return
  await downloadProjectTaskAttachment(
    props.projectId,
    props.taskId,
    props.attachment.attachmentId,
    props.attachment.name || '附件'
  )
}

function getAttachmentIcon(attachment: ProjectTaskAttachment | null): string {
  const extension = getFileExtension(attachment?.name)
  const mimeType = normalizeContentType(attachment?.mimeType)

  if (isImageType(extension, mimeType)) return 'image'
  if (isAudioType(extension, mimeType)) return 'music_note'
  if (isVideoType(extension, mimeType)) return 'videocam'
  if (mimeType.includes('pdf') || extension === 'pdf') return 'picture_as_pdf'
  if (mimeType.includes('word') || ['doc', 'docx'].includes(extension)) return 'description'
  if (mimeType.includes('excel') || ['xls', 'xlsx', 'csv'].includes(extension)) return 'table_chart'
  if (mimeType.includes('presentation') || ['ppt', 'pptx'].includes(extension)) return 'slideshow'
  return 'attach_file'
}
</script>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.scale-fade-enter-active {
  transition: all 0.3s ease-out;
}

.scale-fade-leave-active {
  transition: all 0.2s ease-in;
}

.scale-fade-enter-from,
.scale-fade-leave-to {
  opacity: 0;
  transform: scale(0.95);
}

.media-preview-shell {
  overscroll-behavior: contain;
}

.media-preview-shell--image {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.media-preview-image {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  border-radius: 24px;
}

.media-preview-video {
  max-width: 100%;
  max-height: 100%;
  border-radius: 16px;
  background: #020617;
}

.office-preview-shell {
  overscroll-behavior: contain;
}

:deep(.office-preview-viewer) {
  min-height: 100%;
  max-width: 100%;
}

.office-preview-shell :deep(.vue-office-docx) {
  max-width: 100%;
  overflow-x: hidden;
}

.office-preview-shell :deep(.vue-office-docx-main),
.office-preview-shell :deep(.docx-wrapper) {
  max-width: 100%;
  min-width: 0;
  overflow-x: hidden;
  box-sizing: border-box;
}

.office-preview-shell :deep(.docx-wrapper > section.docx) {
  width: 100% !important;
  max-width: 100% !important;
  min-width: 0 !important;
  overflow: hidden !important;
  overflow-wrap: anywhere;
  box-sizing: border-box !important;
}

.office-preview-shell :deep(.vue-office-docx table) {
  max-width: 100% !important;
  table-layout: fixed;
}

.office-preview-shell :deep(.vue-office-docx td),
.office-preview-shell :deep(.vue-office-docx th),
.office-preview-shell :deep(.vue-office-docx p) {
  overflow-wrap: anywhere;
}

.office-preview-shell :deep(.vue-office-docx img),
.office-preview-shell :deep(.vue-office-docx svg),
.office-preview-shell :deep(.vue-office-docx canvas),
.office-preview-shell :deep(.vue-office-docx video) {
  max-width: 100% !important;
  height: auto !important;
  object-fit: contain;
}

.office-preview-shell :deep(.vue-office-docx [style*="inline-block"]) {
  max-width: 100% !important;
}

.office-preview-shell--pptx {
  background: #edf3fb;
}

.office-preview-shell--pptx :deep(.office-preview-viewer--pptx) {
  min-height: auto;
}

.office-preview-shell--pptx :deep(.vue-office-pptx) {
  min-height: 100%;
  padding: 12px 0 16px;
}

.office-preview-shell--pptx :deep(.vue-office-pptx-main) {
  height: auto !important;
  min-height: 100%;
}

.office-preview-shell--pptx :deep(.pptx-preview-wrapper) {
  background: transparent !important;
  width: 100% !important;
  height: auto !important;
  overflow: visible !important;
  padding: 0 12px 16px;
  box-sizing: border-box;
}

.office-preview-shell--pptx :deep(.pptx-preview-slide-wrapper) {
  margin: 0 auto 16px !important;
  max-width: 100%;
  border-radius: 12px;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.08);
}

.office-preview-shell--pptx :deep(.pptx-preview-slide-wrapper:last-child) {
  margin-bottom: 0 !important;
}

.pdf-preview-shell,
.doc-html-preview-shell {
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}
</style>
