import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'

const sourceEntries = [
  ['main.ts', await readFile(path.resolve('src/main.ts'), 'utf8')],
  ['MainLayout.vue', await readFile(path.resolve('src/layouts/MainLayout.vue'), 'utf8')],
  ['ChatView.vue', await readFile(path.resolve('src/views/chat/ChatView.vue'), 'utf8')],
  ['MobileChatTopHeader.vue', await readFile(path.resolve('src/views/chat/components/MobileChatTopHeader.vue'), 'utf8')],
  ['LoginView.vue', await readFile(path.resolve('src/views/login/LoginView.vue'), 'utf8')],
  ['CustomerListView.vue', await readFile(path.resolve('src/views/customer/CustomerListView.vue'), 'utf8')],
  [
    'CustomerInsightSidebar.vue',
    await readFile(path.resolve('src/views/customer/components/CustomerInsightSidebar.vue'), 'utf8')
  ],
  ['main.css', await readFile(path.resolve('src/styles/main.css'), 'utf8')],
  ['nativeMobileRuntime.ts', await readFile(path.resolve('src/utils/nativeMobileRuntime.ts'), 'utf8')],
  ['nativePrivacyConsent.ts', await readFile(path.resolve('src/utils/nativePrivacyConsent.ts'), 'utf8')]
]

const sources = Object.fromEntries(sourceEntries)

function assertNoBareQueueMicrotask(label, source) {
  assert.doesNotMatch(
    source,
    /(?<!\.)\bqueueMicrotask\s*\(/,
    `${label} must use a guarded queueMicrotask fallback for Android 8 WebView`
  )
}

function assertResizeObserverIsGuarded(label, source) {
  const lines = source.split(/\r?\n/)
  lines.forEach((line, index) => {
    if (!line.includes('new ResizeObserver')) return

    const nearbySource = lines.slice(Math.max(0, index - 4), index + 1).join('\n')
    assert.match(
      nearbySource,
      /typeof ResizeObserver (?:!== ['"]undefined['"]|=== ['"]undefined['"]\) return)/,
      `${label}:${index + 1} must guard ResizeObserver before constructing it`
    )
  })
}

for (const [label, source] of sourceEntries) {
  assertNoBareQueueMicrotask(label, source)
  assertResizeObserverIsGuarded(label, source)
}

assert.match(
  sources['nativeMobileRuntime.ts'],
  /export function isLegacyAndroidWebView/,
  'native mobile runtime must expose an Android 8 / old WebView detector'
)
assert.match(
  sources['main.ts'],
  /isLegacyAndroidWebView/,
  'bootstrap must add a legacy Android WebView class before mounting'
)
assert.match(
  sources['main.css'],
  /html\.wk-android-legacy-webview[\s\S]*\.flex\.gap-4\s*>\s*\*\s*\+\s*\*/,
  'Android 8 WebView needs flex gap fallbacks for mobile menu rows'
)
assert.match(
  sources['main.css'],
  /html\.wk-android-legacy-webview[\s\S]*\.flex\.flex-col\.gap-2\s*>\s*\*\s*\+\s*\*/,
  'Android 8 WebView needs column flex gap fallbacks for mobile composer stacks'
)

assert.match(
  sources['LoginView.vue'],
  /registerNativeKeyboardInsetListeners/,
  'LoginView must listen for native keyboard events so the mobile agreement bar does not collide with fields'
)
assert.match(
  sources['LoginView.vue'],
  /auth-page--keyboard-open/,
  'LoginView must expose a keyboard-open class for mobile layout rules'
)
assert.match(
  sources['LoginView.vue'],
  /\.auth-page--keyboard-open\s+\.mobile-agreement-consent[\s\S]*display:\s*none/,
  'LoginView must hide the fixed mobile agreement bar while the keyboard is open'
)

const loginStyle = sources['LoginView.vue'].match(/<style scoped>([\s\S]*)<\/style>/)?.[1] || ''
assert.doesNotMatch(
  loginStyle,
  /(?:width|padding|max-height):\s*(?:min|max|clamp)\(/,
  'LoginView critical mobile CSS must include Android 8-safe dimensions instead of CSS min/max/clamp functions'
)

assert.match(
  sources['ChatView.vue'],
  /'--wk-mobile-keyboard-inset'/,
  'Chat composer must expose keyboard height as a CSS variable'
)
assert.doesNotMatch(
  sources['ChatView.vue'],
  /transform:\s*`translate3d\(0,\s*-\$\{effectiveMobileKeyboardInset\.value\}px,\s*0\)`/,
  'Chat composer must avoid transform-only keyboard lifting on Android WebView'
)
assert.match(
  sources['ChatView.vue'],
  /function applyNativeKeyboardInset[\s\S]*mobileKeyboardOpeningActive\.value/,
  'Chat native keyboard inset must tolerate Android keyboard/focus event ordering'
)

assert.match(
  sources['MainLayout.vue'],
  /item\.materialIcon\s*&&\s*!useLegacyAndroidIconFallback[\s\S]{0,220}item\.materialIcon/,
  'Mobile primary menu should keep Material Symbols when the WebView supports them'
)
assert.match(
  sources['MainLayout.vue'],
  /useLegacyAndroidIconFallback[\s\S]{0,180}isLegacyAndroidWebView/,
  'Mobile primary menu must fall back to WkIcon only on legacy Android WebView'
)
assert.match(
  sources['ChatView.vue'],
  /wk-mobile-menu-glyph/,
  'Chat mobile floating menu must not depend on Material Symbols ligatures'
)
assert.match(
  sources['MobileChatTopHeader.vue'],
  /wk-mobile-menu-glyph/,
  'Chat context mobile header menu must not depend on Material Symbols ligatures'
)

console.log('android WebView runtime compatibility tests passed')
