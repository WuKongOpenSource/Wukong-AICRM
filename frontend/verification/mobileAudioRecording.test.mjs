import assert from 'node:assert/strict'
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { pathToFileURL } from 'node:url'
import ts from 'typescript'

const sourcePath = path.resolve('src/utils/mobileAudioRecording.ts')
const source = await readFile(sourcePath, 'utf8')
const chatViewSource = await readFile(path.resolve('src/views/chat/ChatView.vue'), 'utf8')
const followUpDrawerSource = await readFile(path.resolve('src/components/customer/AiFollowUpDrawer.vue'), 'utf8')
const projectComposerSource = await readFile(path.resolve('src/views/project/components/ProjectChatComposer.vue'), 'utf8')
const transpiled = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.ES2022,
    target: ts.ScriptTarget.ES2020
  }
})

globalThis.window = {}
globalThis.androidBridge = {}

const tempDir = path.resolve('verification/.tmp')
await mkdir(tempDir, { recursive: true })
const tempPath = path.join(tempDir, `mobileAudioRecording-${Date.now()}.mjs`)
await writeFile(tempPath, transpiled.outputText, 'utf8')

const mobileAudioRecording = await import(pathToFileURL(tempPath).href)

function setCapacitorPlatform(platform) {
  globalThis.Capacitor.getPlatform = () => platform
}

assert.equal(
  typeof mobileAudioRecording.shouldUseNativeAndroidVoiceRecorder,
  'function',
  'mobile audio helper must expose a native Android voice recorder switch'
)

assert.equal(
  mobileAudioRecording.shouldUseNativeAndroidVoiceRecorder(),
  true,
  'native Android should be detected through @capacitor/core even when window.Capacitor is absent'
)

setCapacitorPlatform('android')
assert.equal(
  mobileAudioRecording.shouldUseNativeAndroidVoiceRecorder(),
  true,
  'native Android should use capacitor-voice-recorder'
)
assert.equal(
  mobileAudioRecording.shouldPreferMobileAudioFileCapture(),
  false,
  'native Android should no longer prefer the audio file picker capture flow'
)
assert.equal(
  mobileAudioRecording.shouldUseMobileAudioFileCapture({
    useMobileAudioApi: true,
    hasAudioInput: true,
    hasMediaRecorder: true,
    canCaptureAudioFile: true,
    preferFileCapture: mobileAudioRecording.shouldPreferMobileAudioFileCapture()
  }),
  false,
  'native Android with usable recording APIs should not open file storage'
)
assert.equal(
  mobileAudioRecording.shouldUseMobileAudioFileCapture({
    useMobileAudioApi: true,
    hasAudioInput: false,
    hasMediaRecorder: false,
    canCaptureAudioFile: true,
    preferFileCapture: false
  }),
  false,
  'native Android without browser recording APIs should fail closed instead of opening file storage'
)

setCapacitorPlatform('ios')
assert.equal(
  mobileAudioRecording.shouldUseNativeAndroidVoiceRecorder(),
  false,
  'native iOS should keep the existing recording flow'
)

setCapacitorPlatform('web')
assert.equal(
  mobileAudioRecording.shouldUseNativeAndroidVoiceRecorder(),
  false,
  'web runtime should keep the existing browser recording flow'
)
Object.defineProperty(globalThis.navigator, 'userAgent', {
  value: 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36',
  configurable: true
})
assert.equal(
  mobileAudioRecording.shouldUseMobileAudioFileCapture({
    useMobileAudioApi: true,
    hasAudioInput: false,
    hasMediaRecorder: false,
    canCaptureAudioFile: true,
    preferFileCapture: false
  }),
  false,
  'Android WebView-like runtimes should not open file storage even if Capacitor platform reports web'
)

const file = mobileAudioRecording.buildNativeVoiceRecordingFile(
  {
    recordDataBase64: 'aGVsbG8=',
    msDuration: 1000,
    mimeType: 'audio/aac'
  },
  'native-test-recording'
)

assert.ok(file instanceof File)
assert.equal(file.name, 'native-test-recording.aac')
assert.equal(file.type, 'audio/aac')
assert.equal(await file.text(), 'hello')
assert.equal(
  mobileAudioRecording.buildNativeVoiceRecordingFile(
    { recordDataBase64: '', msDuration: 0, mimeType: 'audio/aac' },
    'empty-recording'
  ),
  null,
  'empty plugin recording data should not produce a File'
)

for (const [label, componentSource] of [
  ['ChatView', chatViewSource],
  ['AiFollowUpDrawer', followUpDrawerSource],
  ['ProjectChatComposer', projectComposerSource]
]) {
  assert.match(
    componentSource,
    /shouldUseNativeAndroidVoiceRecorder/,
    `${label} must detect native Android before choosing a recording backend`
  )
  assert.match(
    componentSource,
    /startNativeAndroidVoiceRecording/,
    `${label} must start recordings with capacitor-voice-recorder on native Android`
  )
  assert.match(
    componentSource,
    /stopNativeAndroidVoiceRecording/,
    `${label} must stop native Android recordings through capacitor-voice-recorder`
  )
}

await rm(tempDir, { recursive: true, force: true })
delete globalThis.androidBridge

console.log('mobile audio recording tests passed')
