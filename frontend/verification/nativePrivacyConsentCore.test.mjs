import assert from 'node:assert/strict'
import { readFile, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { pathToFileURL } from 'node:url'
import ts from 'typescript'

const sourcePath = path.resolve('src/utils/nativePrivacyConsentCore.ts')
const source = await readFile(sourcePath, 'utf8')
const consentSourcePath = path.resolve('src/utils/nativePrivacyConsent.ts')
const consentSource = await readFile(consentSourcePath, 'utf8')
const transpiled = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.ES2022,
    target: ts.ScriptTarget.ES2020
  }
})

const tempPath = path.join(tmpdir(), `nativePrivacyConsentCore-${Date.now()}.mjs`)
await writeFile(tempPath, transpiled.outputText, 'utf8')

const {
  NATIVE_PRIVACY_CONSENT_ACCEPTED_VALUE,
  NATIVE_PRIVACY_CONSENT_STORAGE_KEY,
  hasStoredNativePrivacyConsent,
  resolveNativePrivacyBootstrapState,
  shouldRequireNativePrivacyConsent,
  storeNativePrivacyConsent
} = await import(pathToFileURL(tempPath).href)

function createStorage(initialValue) {
  const values = new Map()
  if (initialValue !== undefined) {
    values.set(NATIVE_PRIVACY_CONSENT_STORAGE_KEY, initialValue)
  }

  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => {
      values.set(key, value)
    }
  }
}

assert.equal(
  shouldRequireNativePrivacyConsent({ nativeRuntime: true, hasConsent: false }),
  true,
  'native apps without stored consent must show the privacy gate'
)
assert.equal(
  shouldRequireNativePrivacyConsent({ nativeRuntime: true, hasConsent: true }),
  false,
  'native apps with stored consent can continue bootstrapping'
)
assert.equal(
  shouldRequireNativePrivacyConsent({ nativeRuntime: false, hasConsent: false }),
  false,
  'web runtime should not be blocked by the native app privacy gate'
)

assert.equal(
  resolveNativePrivacyBootstrapState({ nativeRuntime: true, hasConsent: false }),
  'needs-consent'
)
assert.equal(
  resolveNativePrivacyBootstrapState({ nativeRuntime: true, hasConsent: true }),
  'ready'
)

const storage = createStorage()
assert.equal(hasStoredNativePrivacyConsent(storage), false)
storeNativePrivacyConsent(storage)
assert.equal(
  storage.getItem(NATIVE_PRIVACY_CONSENT_STORAGE_KEY),
  NATIVE_PRIVACY_CONSENT_ACCEPTED_VALUE
)
assert.equal(hasStoredNativePrivacyConsent(storage), true)

const brokenStorage = {
  getItem: () => {
    throw new Error('storage unavailable')
  },
  setItem: () => {
    throw new Error('storage unavailable')
  }
}

assert.equal(hasStoredNativePrivacyConsent(brokenStorage), false)
assert.equal(storeNativePrivacyConsent(brokenStorage), false)

assert.match(
  consentSource,
  /import\s*\{\s*App\s*\}\s*from\s*['"]@capacitor\/app['"]/,
  'native app exit must import the official Capacitor App plugin'
)
assert.match(
  consentSource,
  /await\s+App\.exitApp\(\)/,
  'native app exit must call App.exitApp()'
)
assert.doesNotMatch(
  consentSource,
  /registerNativePlugin<NativeAppExitPlugin>\('App'\)/,
  'native app exit should not use the manual registerPlugin fallback'
)
assert.doesNotMatch(
  consentSource,
  /openLegalDocumentWithInAppBrowser/,
  'native privacy dialog legal links should not use the InAppBrowser opener'
)
assert.match(
  consentSource,
  /window\.open\(url,\s*['_"]_blank['_"],\s*['_"]noopener,noreferrer['_"]\)/,
  'native privacy dialog legal links must open documents with window.open'
)
assert.doesNotMatch(
  consentSource,
  /不会读取\s*Android ID|设备标识|MAC 地址|剪贴板/,
  'native privacy dialog should not show the personal-information-reading copy'
)
assert.doesNotMatch(
  consentSource,
  /\.replaceChildren\(/,
  'native privacy dialog must not depend on Element.replaceChildren because Android 8 WebView may not support it'
)
assert.doesNotMatch(
  consentSource,
  /inset:\s*0/,
  'native privacy dialog must not depend on the CSS inset shorthand in Android 8 WebView'
)
assert.doesNotMatch(
  consentSource,
  /\bmin\(/,
  'native privacy dialog must not depend on CSS min() in Android 8 WebView'
)

console.log('native privacy consent core tests passed')
