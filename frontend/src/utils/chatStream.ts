export const CHAT_STREAM_QUOTA_EVENT = 'quota_exhausted'
export const CHAT_STREAM_QUOTA_CODE = 'insufficient_quota'
const DEFAULT_QUOTA_MESSAGE = '额度不足，请补充额度后继续使用。'

export interface ParsedChatSSEEvent {
  event: string
  data: string
}

export interface ChatQuotaExhaustedPayload {
  code: string
  message: string
  type?: string
  raw?: unknown
}

export class ChatQuotaExhaustedError extends Error {
  code = CHAT_STREAM_QUOTA_CODE
  type = CHAT_STREAM_QUOTA_CODE
  payload: ChatQuotaExhaustedPayload

  constructor(payload?: Partial<ChatQuotaExhaustedPayload> | null) {
    const resolvedPayload: ChatQuotaExhaustedPayload = {
      code: payload?.code || CHAT_STREAM_QUOTA_CODE,
      type: payload?.type || CHAT_STREAM_QUOTA_CODE,
      message: payload?.message || DEFAULT_QUOTA_MESSAGE,
      raw: payload?.raw
    }
    super(resolvedPayload.message)
    this.name = 'ChatQuotaExhaustedError'
    this.payload = resolvedPayload
    this.code = resolvedPayload.code
    this.type = resolvedPayload.type || CHAT_STREAM_QUOTA_CODE
  }
}

export function parseChatSSEEvent(event: string): ParsedChatSSEEvent | null {
  const lines = event.split(/\r?\n/)
  const dataLines: string[] = []
  let eventName = 'message'

  for (const line of lines) {
    if (line.startsWith(':')) continue
    if (line.startsWith('event:')) {
      const value = stripSSELeadingSpace(line.slice(6)).trim()
      if (value) eventName = value
      continue
    }
    if (line.startsWith('data:')) {
      dataLines.push(stripSSELeadingSpace(line.slice(5)))
    }
  }

  if (dataLines.length === 0) return null
  return {
    event: eventName,
    data: dataLines.join('\n')
  }
}

export function createQuotaExhaustedError(payload?: Partial<ChatQuotaExhaustedPayload> | null) {
  return new ChatQuotaExhaustedError(payload)
}

export function isChatQuotaExhaustedError(error: unknown): error is ChatQuotaExhaustedError {
  return Boolean(
    error
    && typeof error === 'object'
    && (
      (error as { name?: string }).name === 'ChatQuotaExhaustedError'
      || (error as { code?: string }).code === CHAT_STREAM_QUOTA_CODE
      || (error as { type?: string }).type === CHAT_STREAM_QUOTA_CODE
    )
  )
}

export function parseQuotaPayloadFromSSE(event: ParsedChatSSEEvent): ChatQuotaExhaustedPayload | null {
  if (event.event !== CHAT_STREAM_QUOTA_EVENT) return null
  return normalizeQuotaPayload(parseMaybeJson(event.data) ?? event.data) || {
    code: CHAT_STREAM_QUOTA_CODE,
    type: CHAT_STREAM_QUOTA_CODE,
    message: event.data || DEFAULT_QUOTA_MESSAGE,
    raw: event.data
  }
}

export function parseHttpQuotaErrorPayload(value: unknown): ChatQuotaExhaustedPayload | null {
  return normalizeQuotaPayload(value)
}

export function parseMaybeJson(text: string): unknown | null {
  const trimmed = text.trim()
  if (!trimmed) return null
  try {
    return JSON.parse(trimmed) as unknown
  } catch {
    return null
  }
}

function normalizeQuotaPayload(value: unknown): ChatQuotaExhaustedPayload | null {
  if (typeof value === 'string') {
    const parsed = parseMaybeJson(value)
    if (parsed) return normalizeQuotaPayload(parsed)
    return null
  }

  if (!isPlainRecord(value)) return null

  const error = isPlainRecord(value.error) ? value.error : value
  const code = readString(error, 'code') || readString(value, 'code')
  const type = readString(error, 'type') || readString(value, 'type')
  if (code !== CHAT_STREAM_QUOTA_CODE && type !== CHAT_STREAM_QUOTA_CODE) {
    return null
  }

  return {
    code: code || CHAT_STREAM_QUOTA_CODE,
    type: type || CHAT_STREAM_QUOTA_CODE,
    message:
      readString(error, 'message')
      || readString(error, 'msg')
      || readString(value, 'message')
      || readString(value, 'msg')
      || DEFAULT_QUOTA_MESSAGE,
    raw: value
  }
}

function stripSSELeadingSpace(value: string): string {
  return value.startsWith(' ') ? value.slice(1) : value
}

function isPlainRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function readString(data: Record<string, unknown>, key: string): string {
  const value = data[key]
  return typeof value === 'string' ? value : ''
}
