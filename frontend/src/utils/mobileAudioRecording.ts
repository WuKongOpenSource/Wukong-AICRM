import { Capacitor } from '@capacitor/core'

type LegacyGetUserMedia = (
  constraints: MediaStreamConstraints,
  onSuccess: (stream: MediaStream) => void,
  onError: (error: DOMException) => void
) => void

type LegacyMediaNavigator = Navigator & {
  getUserMedia?: LegacyGetUserMedia
  webkitGetUserMedia?: LegacyGetUserMedia
  mozGetUserMedia?: LegacyGetUserMedia
  msGetUserMedia?: LegacyGetUserMedia
}

type NativeVoiceRecorderRecording = {
  recordDataBase64?: string
  msDuration: number
  mimeType: string
  path?: string
}

export interface MobileAudioFileCaptureOptions {
  useMobileAudioApi: boolean
  hasAudioInput: boolean
  hasMediaRecorder: boolean
  canCaptureAudioFile: boolean
  preferFileCapture?: boolean
}

function getLegacyGetUserMedia(): LegacyGetUserMedia | undefined {
  if (typeof navigator === 'undefined') return undefined
  const nav = navigator as LegacyMediaNavigator
  return nav.getUserMedia
    || nav.webkitGetUserMedia
    || nav.mozGetUserMedia
    || nav.msGetUserMedia
}

export function hasMobileAudioInputSupport(): boolean {
  if (typeof navigator === 'undefined') return false
  return Boolean(navigator.mediaDevices?.getUserMedia || getLegacyGetUserMedia())
}

export function getCapacitorPlatform(): string {
  return Capacitor.getPlatform()
}

export function isNativeCapacitorAndroid(): boolean {
  return getCapacitorPlatform() === 'android'
}

function isAndroidUserAgent(): boolean {
  if (typeof navigator === 'undefined') return false
  return /\bAndroid\b/i.test(navigator.userAgent || '')
}

function shouldBlockMobileAudioFileCapture(): boolean {
  return isNativeCapacitorAndroid() || isAndroidUserAgent()
}

export function shouldUseNativeAndroidVoiceRecorder(): boolean {
  return isNativeCapacitorAndroid()
}

export function shouldPreferMobileAudioFileCapture(): boolean {
  return false
}

export function shouldUseMobileAudioFileCapture(options: MobileAudioFileCaptureOptions): boolean {
  return !shouldBlockMobileAudioFileCapture()
    && options.useMobileAudioApi
    && options.canCaptureAudioFile
    && (
      Boolean(options.preferFileCapture)
      || !options.hasAudioInput
      || !options.hasMediaRecorder
    )
}

export async function requestMobileAudioStream(
  constraints: MediaStreamConstraints = { audio: true }
): Promise<MediaStream> {
  if (navigator.mediaDevices?.getUserMedia) {
    return navigator.mediaDevices.getUserMedia(constraints)
  }

  const legacyGetUserMedia = getLegacyGetUserMedia()
  if (!legacyGetUserMedia) {
    throw new DOMException('Audio input is not supported', 'NotSupportedError')
  }

  return new Promise((resolve, reject) => {
    legacyGetUserMedia.call(navigator, constraints, resolve, reject)
  })
}

export function canCaptureMobileAudioFile(): boolean {
  return typeof document !== 'undefined' && typeof File !== 'undefined'
}

function resolveNativeVoiceRecordingExtension(mimeType: string): string {
  if (mimeType.includes('aac')) return 'aac'
  if (mimeType.includes('mp4')) return 'm4a'
  if (mimeType.includes('mpeg')) return 'mp3'
  if (mimeType.includes('wav')) return 'wav'
  if (mimeType.includes('ogg')) return 'ogg'
  return 'webm'
}

function normalizeBase64AudioData(value: string): string {
  const [, dataUrlBase64] = value.split('base64,')
  return (dataUrlBase64 || value).trim()
}

function base64ToUint8Array(base64: string): Uint8Array {
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index)
  }
  return bytes
}

export function buildNativeVoiceRecordingFile(
  recording: NativeVoiceRecorderRecording,
  fileBaseName = 'voice-recording'
): File | null {
  const base64 = recording.recordDataBase64
  if (!base64) return null

  const mimeType = recording.mimeType || 'audio/aac'
  const bytes = base64ToUint8Array(normalizeBase64AudioData(base64))
  if (bytes.length === 0) return null
  const audioBuffer = new ArrayBuffer(bytes.byteLength)
  new Uint8Array(audioBuffer).set(bytes)

  return new File(
    [audioBuffer],
    `${fileBaseName}.${resolveNativeVoiceRecordingExtension(mimeType)}`,
    { type: mimeType }
  )
}

async function loadNativeVoiceRecorder() {
  const { VoiceRecorder } = await import('capacitor-voice-recorder')
  return VoiceRecorder
}

export async function startNativeAndroidVoiceRecording(): Promise<boolean> {
  const voiceRecorder = await loadNativeVoiceRecorder()
  const canRecord = await voiceRecorder.canDeviceVoiceRecord().catch(() => ({ value: false }))
  if (!canRecord.value) return false

  const hasPermission = await voiceRecorder.hasAudioRecordingPermission().catch(() => ({ value: false }))
  const permission = hasPermission.value
    ? hasPermission
    : await voiceRecorder.requestAudioRecordingPermission().catch(() => ({ value: false }))

  if (!permission.value) return false

  const started = await voiceRecorder.startRecording()
  return Boolean(started.value)
}

export async function stopNativeAndroidVoiceRecording(fileBaseName = 'voice-recording'): Promise<File | null> {
  const voiceRecorder = await loadNativeVoiceRecorder()
  const recording = await voiceRecorder.stopRecording()
  return buildNativeVoiceRecordingFile(recording.value, fileBaseName)
}

export async function discardNativeAndroidVoiceRecording(): Promise<void> {
  try {
    await stopNativeAndroidVoiceRecording()
  } catch {
    // Best effort: closing a drawer or leaving the page should not surface stale stop errors.
  }
}

export function captureMobileAudioFile(): Promise<File | null> {
  if (shouldBlockMobileAudioFileCapture() || !canCaptureMobileAudioFile()) {
    return Promise.resolve(null)
  }

  return new Promise((resolve) => {
    const input = document.createElement('input')
    let settled = false
    let focusTimer: number | undefined

    const cleanup = () => {
      if (focusTimer) {
        window.clearTimeout(focusTimer)
      }
      input.removeEventListener('change', handleChange)
      window.removeEventListener('focus', handleWindowFocus)
      input.remove()
    }

    const settle = (file: File | null) => {
      if (settled) return
      settled = true
      cleanup()
      resolve(file)
    }

    const handleChange = () => {
      settle(input.files?.[0] ?? null)
    }

    const handleWindowFocus = () => {
      if (focusTimer) {
        window.clearTimeout(focusTimer)
      }
      focusTimer = window.setTimeout(() => {
        if (!input.files || input.files.length === 0) {
          settle(null)
        }
      }, 800)
    }

    input.type = 'file'
    input.accept = 'audio/*'
    input.setAttribute('capture', 'microphone')
    input.style.position = 'fixed'
    input.style.left = '-9999px'
    input.style.top = '0'
    input.style.opacity = '0'
    input.addEventListener('change', handleChange)
    window.addEventListener('focus', handleWindowFocus)
    document.body.appendChild(input)
    input.click()
  })
}
