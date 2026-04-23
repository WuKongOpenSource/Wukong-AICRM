import { get, post, upload, download } from '@/utils/request'
import type {
  FollowUp,
  FollowUpAddBO,
  FollowUpAttachment,
  FollowUpQueryBO,
  FollowUpUpdateBO
} from '@/types/customer'
import type { PageResult } from '@/types/api'
import type { ChatAttachmentDTO } from '@/types/common'

/**
 * Add follow-up record
 */
export function addFollowUp(data: FollowUpAddBO): Promise<string> {
  return post('/followup/add', data)
}

/**
 * Update follow-up record
 */
export function updateFollowUp(data: FollowUpUpdateBO): Promise<void> {
  return post('/followup/update', data)
}

/**
 * Query follow-ups by customer
 */
export function queryFollowUpsByCustomer(customerId: string): Promise<FollowUp[]> {
  return post('/followup/queryByCustomer', null, { params: { customerId } })
}

/**
 * Delete follow-up record
 */
export function deleteFollowUp(followUpId: string): Promise<void> {
  return post(`/followup/delete/${followUpId}`)
}

/**
 * Query follow-ups with pagination
 */
export function queryFollowUpPageList(query: FollowUpQueryBO): Promise<PageResult<FollowUp>> {
  return post('/followup/queryPageList', query)
}

/**
 * AI parse follow-up content
 */
export interface AiFollowUpParseBO {
  content: string
  customerName?: string
  customerId?: string
  attachments?: ChatAttachmentDTO[]
}

export interface AiFollowUpParseVO {
  summary: string
  sceneType: string
  type: string
  followTime: string
  nextFollowTime: string
  keyPoints: string[]
  todos: string[]
}

export function aiParseFollowUp(data: AiFollowUpParseBO): Promise<AiFollowUpParseVO> {
  return post('/followup/ai-parse', data)
}

export function transcribeFollowUpAudio(file: File): Promise<string> {
  const formData = new FormData()
  formData.append('file', file)
  return upload('/followup/ai-transcribe', formData)
}

export function aiAnalyzeFollowUpAttachment(attachmentId: string): Promise<FollowUpAttachment> {
  return post(`/followup/attachment/${attachmentId}/ai-analyze`)
}

export function getFollowUpAttachmentBlob(attachmentId: string): Promise<Blob> {
  return get(`/followup/attachment/${attachmentId}/download`, { responseType: 'blob' })
}

export function getFollowUpAttachmentPreviewHtml(attachmentId: string): Promise<string> {
  return get(`/followup/attachment/${attachmentId}/preview-html`)
}

export function downloadFollowUpAttachment(attachmentId: string, filename: string): Promise<void> {
  return download(`/followup/attachment/${attachmentId}/download`, filename)
}
