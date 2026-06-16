import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'

const source = await readFile(path.resolve('src/layouts/MainLayout.vue'), 'utf8')

assert.match(
  source,
  /import\s*\{\s*App\s*\}\s*from\s*['"]@capacitor\/app['"]/,
  'mobile account cancellation must use the Capacitor App plugin to close the native app'
)

assert.match(
  source,
  /注销后您的个人数据将会被永久删除，您真的要注销当前登录账号吗？/,
  'account cancellation must show the destructive confirmation copy'
)

assert.match(
  source,
  /您的账户正在申请注销，30天内请勿登录/,
  'account cancellation must show the 30-day notice after confirmation'
)

assert.match(
  source,
  /confirmButtonText:\s*['"]确认['"][\s\S]*?cancelButtonText:\s*['"]取消['"]/,
  'the first account cancellation dialog must have explicit confirm and cancel buttons'
)

assert.match(
  source,
  /confirmButtonText:\s*['"]好的，我知道了['"]/,
  'the second account cancellation dialog must only expose the acknowledgement button'
)

assert.match(
  source,
  /async function handleAccountCancellationRequest\(\)[\s\S]*?await userStore\.logout\(\)[\s\S]*?await App\.exitApp\(\)/,
  'acknowledging the account cancellation notice must log out and close the app'
)

assert.equal(
  (source.match(/>注销账户</g) || []).length,
  2,
  'both mobile settings popovers must include the account cancellation action'
)

assert.doesNotMatch(
  source,
  /text-red-|bg-red-/,
  'account cancellation actions should use light gray styling instead of red warning styling'
)

assert.ok(
  (source.match(/text-\[#8a8a92\]/g) || []).length >= 2,
  'account cancellation actions should use the light gray text color'
)

console.log('mobile account cancellation tests passed')
