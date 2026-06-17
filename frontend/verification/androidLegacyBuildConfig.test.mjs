import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'

const packageJson = JSON.parse(await readFile(path.resolve('package.json'), 'utf8'))
const viteConfigJs = await readFile(path.resolve('vite.config.js'), 'utf8')
const viteConfigTs = await readFile(path.resolve('vite.config.ts'), 'utf8')

function assertAndroidLegacyBuildConfig(source, label) {
  assert.match(
    source,
    /@vitejs\/plugin-legacy/,
    `${label} must import @vitejs/plugin-legacy for old Android WebView builds`
  )
  assert.match(
    source,
    /legacy\s*\(\s*\{/,
    `${label} must enable the Vite legacy plugin`
  )
  assert.match(
    source,
    /Android\s*>=\s*8/,
    `${label} must explicitly target Android 8 WebView devices`
  )
  assert.match(
    source,
    /Chrome\s*>=\s*61/,
    `${label} must include Chrome 61-era WebView compatibility`
  )
  assert.match(
    source,
    /modernPolyfills:\s*true/,
    `${label} must polyfill modern chunks for module-capable old WebViews`
  )
  assert.match(
    source,
    /cssTarget:\s*['"]chrome61['"]/,
    `${label} must avoid CSS minification output newer than Android 8-era WebView`
  )
}

assert.ok(
  packageJson.devDependencies?.['@vitejs/plugin-legacy'],
  'frontend devDependencies must include @vitejs/plugin-legacy'
)
assert.ok(
  packageJson.devDependencies?.terser,
  'frontend devDependencies must include terser because @vitejs/plugin-legacy needs it during production build'
)

assertAndroidLegacyBuildConfig(viteConfigJs, 'vite.config.js')
assertAndroidLegacyBuildConfig(viteConfigTs, 'vite.config.ts')

console.log('android legacy build config tests passed')
