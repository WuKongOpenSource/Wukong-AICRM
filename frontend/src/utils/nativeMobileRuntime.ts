export type NativeCapacitorRuntime = {
  isNativePlatform?: () => boolean
  isPluginAvailable?: (pluginName: string) => boolean
  getPlatform?: () => string
  registerPlugin?: <T>(pluginName: string) => T
  platform?: string
  isNative?: boolean
}

declare global {
  interface Window {
    Capacitor?: NativeCapacitorRuntime
  }
}

export function getNativeCapacitor(): NativeCapacitorRuntime | null {
  if (typeof window === 'undefined') return null

  const capacitor = window.Capacitor
  if (!capacitor) return null

  if (typeof capacitor.isNativePlatform === 'function') {
    return capacitor.isNativePlatform() ? capacitor : null
  }

  if (typeof capacitor.getPlatform === 'function') {
    return capacitor.getPlatform() !== 'web' ? capacitor : null
  }

  if (typeof capacitor.platform === 'string') {
    return capacitor.platform !== 'web' ? capacitor : null
  }

  return capacitor.isNative === true ? capacitor : null
}

export function isMobileViewport(): boolean {
  if (typeof window === 'undefined') return false

  return (
    window.innerWidth < 768 ||
    (typeof window.matchMedia === 'function' && window.matchMedia('(pointer: coarse)').matches)
  )
}

export function isNativePlatform(): boolean {
  return Boolean(getNativeCapacitor())
}

export function isNativeMobileRuntime(): boolean {
  return isNativePlatform()
}

function readUserAgent(userAgent?: string): string {
  if (typeof userAgent === 'string') return userAgent
  if (typeof navigator === 'undefined') return ''
  return navigator.userAgent || ''
}

export function resolveAndroidMajorVersion(userAgent?: string): number | null {
  const match = readUserAgent(userAgent).match(/\bAndroid\s+(\d+)/i)
  if (!match) return null

  const version = Number.parseInt(match[1], 10)
  return Number.isFinite(version) ? version : null
}

export function resolveAndroidChromeMajorVersion(userAgent?: string): number | null {
  const ua = readUserAgent(userAgent)
  if (!/\bAndroid\b/i.test(ua)) return null

  const match = ua.match(/\b(?:Chrome|Chromium)\/(\d+)/i)
  if (!match) return null

  const version = Number.parseInt(match[1], 10)
  return Number.isFinite(version) ? version : null
}

export function isLegacyAndroidWebView(userAgent?: string): boolean {
  const ua = readUserAgent(userAgent)
  if (!/\bAndroid\b/i.test(ua)) return false

  const chromeMajor = resolveAndroidChromeMajorVersion(ua)
  if (chromeMajor != null) {
    return chromeMajor < 84
  }

  const androidMajor = resolveAndroidMajorVersion(ua)
  return androidMajor != null && androidMajor <= 8
}

export function isNativeTabletViewport(
  viewportWidth: number,
  viewportHeight: number,
  nativeRuntime: boolean
): boolean {
  if (!nativeRuntime) return false

  const shortSide = Math.min(viewportWidth, viewportHeight)
  const longSide = Math.max(viewportWidth, viewportHeight)

  return shortSide >= 768 && longSide <= 1366
}

export function isNativeTabletRuntime(): boolean {
  if (typeof window === 'undefined') return false

  return isNativeTabletViewport(window.innerWidth, window.innerHeight, isNativePlatform())
}

export function resolveNativeTabletSafeAreaTop(
  nativeTabletRuntime: boolean,
  baseOffsetPx = 12
): string | undefined {
  return nativeTabletRuntime
    ? `calc(${baseOffsetPx}px + var(--safe-area-inset-top))`
    : undefined
}

export function isNativePluginAvailable(pluginName: string): boolean {
  const capacitor = getNativeCapacitor()
  if (!capacitor || typeof capacitor.isPluginAvailable !== 'function') return false

  return capacitor.isPluginAvailable(pluginName)
}

export function getNativePlatform(): string {
  const capacitor = getNativeCapacitor()
  if (!capacitor) return 'web'

  if (typeof capacitor.getPlatform === 'function') {
    return capacitor.getPlatform()
  }

  return capacitor.platform || 'web'
}

export function registerNativePlugin<T>(pluginName: string): T | null {
  const capacitor = getNativeCapacitor()
  if (!capacitor || typeof capacitor.registerPlugin !== 'function') return null

  return capacitor.registerPlugin<T>(pluginName)
}
