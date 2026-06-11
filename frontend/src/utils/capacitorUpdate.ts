import { Capacitor } from '@capacitor/core'
import type { DownloadBundleOptions, LiveUpdatePlugin } from '@capawesome/capacitor-live-update'
import { ElMessageBox } from 'element-plus'
import { h } from 'vue'
import {
  HOT_UPDATE_STATUS,
  type AppUpdateInfo,
  type SupportedPlatform,
  getPlatformUpdateInfo,
  shouldApplyUpdate
} from '@/utils/capacitorUpdateCore'

export {
  DOWNLOAD_UPDATE_STATUS,
  HOT_UPDATE_STATUS,
  compareVersion,
  getPlatformUpdateInfo,
  normalizeUpdateInfo,
  shouldApplyUpdate
} from '@/utils/capacitorUpdateCore'
export type { AppUpdateInfo, SupportedPlatform } from '@/utils/capacitorUpdateCore'

export const DEFAULT_UPDATE_CHECK_DELAY_MS = 2000
export const DEFAULT_UPDATE_CHECK_RETRY_DELAY_MS = 5000
export const DEFAULT_UPDATE_CHECK_MAX_ATTEMPTS = 2
export const DEFAULT_CURRENT_VERSION = '1.0.0'
export const DEFAULT_UPDATE_VERSION_URL = 'https://www.72crm.ai/version.json'

type Fetcher = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>

type BrowserPlugin = {
  open(options: { url: string }): Promise<void>
}

type LiveUpdateModule = {
  LiveUpdate: LiveUpdatePlugin
}

export type CheckForUpdatesOptions = {
  manifestUrl?: string
  fetcher?: Fetcher
}

export type CheckForUpdatesResult = 'updated' | 'none' | 'unsupported' | 'failed'

export type ScheduleCapacitorUpdateCheckOptions = {
  delayMs?: number
  retryDelayMs?: number
  maxAttempts?: number
}

let browserPlugin: BrowserPlugin | null = null
let isDownloadUpdateDialogVisible = false

function getBrowserPlugin(): BrowserPlugin {
  if (!browserPlugin) {
    browserPlugin = Capacitor.registerPlugin<BrowserPlugin>('Browser')
  }
  return browserPlugin
}

function isSupportedPlatform(platform: string): platform is SupportedPlatform {
  return platform === 'android' || platform === 'ios'
}

function getUpdateManifestUrl(): string {
  const raw = import.meta.env.VITE_UPDATE_VERSION_URL
  const configuredUrl = typeof raw === 'string' ? raw.trim() : ''
  return configuredUrl || DEFAULT_UPDATE_VERSION_URL
}

function getConfiguredAppVersion(): string {
  const raw = import.meta.env.VITE_APP_VERSION
  const configuredVersion = typeof raw === 'string' ? raw.trim() : ''
  return configuredVersion || DEFAULT_CURRENT_VERSION
}

function loadLiveUpdateModule(): Promise<LiveUpdateModule> {
  return import('@capawesome/capacitor-live-update')
}

export async function getCurrentVersion(): Promise<string> {
  return getConfiguredAppVersion()
}

export async function markLiveUpdateReady(): Promise<void> {
  if (!Capacitor.isNativePlatform()) return

  try {
    const { LiveUpdate } = await loadLiveUpdateModule()
    await LiveUpdate.ready()
  } catch (error) {
    console.warn('热更新 ready 失败:', error)
  }
}

export async function performHotUpdate(updateInfo: AppUpdateInfo): Promise<void> {
  const { LiveUpdate } = await loadLiveUpdateModule()
  const bundleId = updateInfo.bundleId || updateInfo.version
  const downloadOptions: DownloadBundleOptions = {
    bundleId,
    url: updateInfo.url
  }

  if (updateInfo.artifactType) {
    downloadOptions.artifactType = updateInfo.artifactType
  }
  if (updateInfo.checksum) {
    downloadOptions.checksum = updateInfo.checksum
  }
  if (updateInfo.signature) {
    downloadOptions.signature = updateInfo.signature
  }

  await LiveUpdate.downloadBundle(downloadOptions)
  await LiveUpdate.setNextBundle({ bundleId })
  await LiveUpdate.reload()
}

export async function performDownloadUpdate(updateUrl: string): Promise<void> {
  if (Capacitor.isPluginAvailable('Browser')) {
    await getBrowserPlugin().open({ url: updateUrl })
    return
  }

  if (typeof window !== 'undefined') {
    window.location.assign(updateUrl)
  }
}

export async function showDownloadUpdateDialog(updateInfo: AppUpdateInfo, currentVersion: string): Promise<void> {
  if (isDownloadUpdateDialogVisible) return

  isDownloadUpdateDialogVisible = true

  const note = updateInfo.note || '暂无更新说明'
  const noteLines = note.split(/\r?\n/).filter((line) => line.trim())

  try {
    await ElMessageBox.confirm(
      h('div', { class: 'space-y-3 text-left' }, [
        h('p', { class: 'text-sm text-slate-600' }, `检测到新版本 ${updateInfo.version},当前版本为 ${currentVersion}`),
        h('div', { class: 'rounded bg-slate-50 p-3 text-sm leading-6 text-slate-700' }, [
          h('div', { class: 'mb-1 font-medium text-slate-900 w-full' }, '更新内容'),
          noteLines.length
            ? h(
                'div',
                { class: 'space-y-1 whitespace-pre-wrap break-words' },
                noteLines.map((line) => h('p', line))
              )
            : h('p', '暂无更新说明')
        ])
      ]),
      '发现新版本',
      {
        confirmButtonText: '立即更新',
        cancelButtonText: '稍后',
        closeOnClickModal: false,
        closeOnPressEscape: false,
        distinguishCancelAndClose: true,
        type: 'info'
      }
    )
    await performDownloadUpdate(updateInfo.url)
  } catch {
    // 用户选择稍后更新时不需要提示。
  } finally {
    isDownloadUpdateDialogVisible = false
  }
}

export async function checkForUpdates(options: CheckForUpdatesOptions = {}): Promise<CheckForUpdatesResult> {
  if (!Capacitor.isNativePlatform()) return 'unsupported'

  const manifestUrl = options.manifestUrl || getUpdateManifestUrl()
  if (!manifestUrl) return 'unsupported'

  try {
    const currentVersion = await getCurrentVersion()
    const fetcher = options.fetcher || fetch
    const response = await fetcher(manifestUrl, { cache: 'no-store' })

    if (!response.ok) {
      throw new Error(`版本检查接口请求失败: ${response.status}`)
    }

    const platform = Capacitor.getPlatform()
    if (!isSupportedPlatform(platform)) return 'unsupported'

    const payload = await response.json()
    const updateInfo = getPlatformUpdateInfo(payload, platform)

    if (!shouldApplyUpdate(updateInfo, currentVersion) || !updateInfo) return 'none'

    if (updateInfo.status === HOT_UPDATE_STATUS) {
      await performHotUpdate(updateInfo)
      return 'updated'
    }

    await showDownloadUpdateDialog(updateInfo, currentVersion)
    return 'updated'
  } catch (error) {
    console.error('检查更新失败:', error)
    return 'failed'
  }
}

export function scheduleCapacitorUpdateCheck(
  options: number | ScheduleCapacitorUpdateCheckOptions = DEFAULT_UPDATE_CHECK_DELAY_MS
): void {
  if (!Capacitor.isNativePlatform() || typeof window === 'undefined') return

  const delayMs = typeof options === 'number'
    ? options
    : options.delayMs ?? DEFAULT_UPDATE_CHECK_DELAY_MS
  const retryDelayMs = typeof options === 'number'
    ? DEFAULT_UPDATE_CHECK_RETRY_DELAY_MS
    : options.retryDelayMs ?? DEFAULT_UPDATE_CHECK_RETRY_DELAY_MS
  const configuredMaxAttempts = typeof options === 'number'
    ? DEFAULT_UPDATE_CHECK_MAX_ATTEMPTS
    : options.maxAttempts ?? DEFAULT_UPDATE_CHECK_MAX_ATTEMPTS
  const maxAttempts = Math.max(
    1,
    Math.floor(configuredMaxAttempts)
  )

  const runCheck = async (attempt: number): Promise<void> => {
    await markLiveUpdateReady()
    const result = await checkForUpdates()

    if (result === 'failed' && attempt < maxAttempts) {
      window.setTimeout(() => {
        void runCheck(attempt + 1)
      }, retryDelayMs)
    }
  }

  window.setTimeout(() => {
    void runCheck(1)
  }, delayMs)
}
