<template>
  <el-dialog
    :model-value="modelValue"
    width="620px"
    class="external-ai-mobile-completion-dialog"
    align-center
    :close-on-click-modal="!submitting && !captchaChecking && !smsSending"
    @update:model-value="handleVisibleChange"
  >
    <template #header>
      <div class="flex items-start gap-4 pr-8">
        <span class="flex size-14 shrink-0 items-center justify-center rounded-full bg-blue-50 text-blue-700">
          <span class="material-symbols-outlined text-[32px]">redeem</span>
        </span>
        <div class="min-w-0">
          <p class="text-xl font-semibold leading-7 text-slate-950">完善手机号，领取 250 积分</p>
          <p class="mt-1 text-sm leading-6 text-slate-500">
            完成手机验证码验证后，积分将自动发放至当前账号。
          </p>
        </div>
      </div>
    </template>

    <div class="space-y-5">
      <el-form label-position="top" class="space-y-4">
        <el-form-item label="手机号" class="!mb-0">
          <el-input v-model="mobile" placeholder="请输入手机号">
            <template #prepend>+86</template>
          </el-input>
        </el-form-item>

        <section class="rounded-lg border border-slate-200 bg-slate-50 p-4">
          <div class="mb-3 flex items-center justify-between gap-3">
            <div>
              <p class="text-sm font-semibold text-slate-900">图形验证</p>
              <p class="mt-1 text-xs leading-5 text-slate-500">拖动滑块完成校验后，才能发送短信验证码。</p>
            </div>
            <el-button
              plain
              :loading="captchaLoading"
              :disabled="captchaChecking"
              @click="refreshCaptcha"
            >
              {{ hasCaptcha ? '换一张' : '获取验证码' }}
            </el-button>
          </div>

          <div v-if="hasCaptcha" class="space-y-3">
            <div class="relative overflow-hidden rounded-lg border border-slate-200 bg-white shadow-inner">
              <img
                ref="backgroundRef"
                :src="backgroundImage"
                alt="验证码背景"
                class="block w-full select-none pointer-events-none"
                @load="measureCaptchaImages"
              />
              <img
                v-if="puzzleImage"
                ref="puzzleRef"
                :src="puzzleImage"
                alt="滑块拼图"
                class="absolute inset-y-0 left-0 h-full max-w-none select-none pointer-events-none drop-shadow-md"
                :style="{ transform: `translateX(${puzzleLeft}px)` }"
                @load="measureCaptchaImages"
              />
              <div
                v-if="captchaLoading || captchaChecking"
                class="absolute inset-0 flex items-center justify-center bg-white/75 text-sm font-medium text-slate-600"
              >
                {{ captchaLoading ? '正在加载验证码...' : '正在校验，请稍候...' }}
              </div>
            </div>

            <div
              ref="trackRef"
              class="relative h-12 overflow-hidden rounded-full border border-slate-200 bg-white select-none"
            >
              <div
                class="absolute inset-y-0 left-0 rounded-full bg-blue-100 transition-all"
                :style="{ width: `${progressWidth}px` }"
              />
              <div class="absolute inset-0 flex items-center justify-center px-16 text-sm text-slate-500">
                {{ sliderTip }}
              </div>
              <button
                type="button"
                class="absolute left-0 top-1/2 flex h-12 w-[52px] -translate-y-1/2 items-center justify-center rounded-full bg-blue-600 text-white shadow-lg shadow-blue-600/20 transition-transform active:scale-95 disabled:cursor-not-allowed disabled:bg-slate-300 disabled:shadow-none"
                :style="{ transform: `translate(${handleLeft}px, -50%)` }"
                :disabled="captchaLoading || captchaChecking || captchaVerification !== ''"
                @pointerdown.prevent="startCaptchaDrag"
              >
                <span class="material-symbols-outlined text-xl">chevron_right</span>
              </button>
            </div>

            <p
              v-if="captchaVerification"
              class="rounded-lg bg-emerald-50 px-3 py-2 text-xs font-medium text-emerald-700"
            >
              图形验证码已通过，可以发送短信验证码。
            </p>
          </div>

          <button
            v-else
            type="button"
            class="flex min-h-[156px] w-full flex-col items-center justify-center rounded-lg border border-dashed border-slate-300 bg-white px-4 text-center transition-colors hover:border-blue-300 hover:bg-blue-50"
            :disabled="captchaLoading"
            @click="refreshCaptcha"
          >
            <span class="material-symbols-outlined text-3xl text-slate-300">verified_user</span>
            <span class="mt-3 text-sm font-semibold text-slate-900">获取图形验证码</span>
            <span class="mt-1 text-xs text-slate-500">加载后拖动滑块完成验证</span>
          </button>
        </section>

        <el-form-item label="验证码" class="!mb-0">
          <div class="grid w-full gap-3 sm:grid-cols-[1fr_auto]">
            <el-input v-model="verificationCode" placeholder="请输入短信验证码" />
            <el-button
              class="sm:w-32"
              :loading="smsSending"
              :disabled="!captchaVerification"
              @click="sendSmsCode"
            >
              获取验证码
            </el-button>
          </div>
        </el-form-item>
      </el-form>

      <div class="rounded-lg border border-slate-200 bg-white px-4 py-3 text-sm leading-6 text-slate-600">
        <p class="font-semibold text-slate-900">活动规则与说明</p>
        <ul class="mt-2 list-disc space-y-1 pl-5">
          <li>验证成功后自动到账 250 积分</li>
          <li>仅限未绑定手机号账号领取一次</li>
          <li>手机号仅用于账号安全验证与消息通知</li>
        </ul>
      </div>

      <div class="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <el-button @click="handleVisibleChange(false)">暂不领取</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="completeMobile"
        >
          验证并领取 250 积分
        </el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  externalAiCheckCaptcha,
  externalAiCompleteMobile,
  externalAiGetCaptcha,
  externalAiSendSmsCode
} from '@/api/systemConfig'
import type { ExternalAiRegisterAndSaveResult } from '@/types/systemConfig'

type ExternalAiCaptchaData = {
  originalImageBase64: string
  jigsawImageBase64: string
  token: string
  secretKey?: string
  captchaType?: string
}

const props = defineProps<{
  modelValue: boolean
  apiUrl?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'completed', value: ExternalAiRegisterAndSaveResult): void
}>()

const HANDLE_WIDTH = 52
const DEFAULT_POINT_Y = 5

const backgroundRef = ref<HTMLImageElement>()
const puzzleRef = ref<HTMLImageElement>()
const trackRef = ref<HTMLDivElement>()
const captchaLoading = ref(false)
const captchaChecking = ref(false)
const dragging = ref(false)
const handleLeft = ref(0)
const dragStartX = ref(0)
const dragStartLeft = ref(0)
const backgroundWidth = ref(0)
const puzzleWidth = ref(0)
const captchaVerification = ref('')
const smsSending = ref(false)
const submitting = ref(false)
const mobile = ref('')
const verificationCode = ref('')
const captcha = reactive<ExternalAiCaptchaData>({
  originalImageBase64: '',
  jigsawImageBase64: '',
  token: '',
  secretKey: '',
  captchaType: 'blockPuzzle'
})

const hasCaptcha = computed(() => Boolean(
  captcha.token &&
  captcha.originalImageBase64 &&
  captcha.jigsawImageBase64
))
const backgroundImage = computed(() => toBase64Image(captcha.originalImageBase64))
const puzzleImage = computed(() => toBase64Image(captcha.jigsawImageBase64))
const trackMax = computed(() => Math.max((trackRef.value?.clientWidth ?? 0) - HANDLE_WIDTH, 0))
const puzzleMax = computed(() => Math.max(backgroundWidth.value - puzzleWidth.value, 0))
const puzzleLeft = computed(() => {
  if (!trackMax.value || !puzzleMax.value) return 0
  return (handleLeft.value / trackMax.value) * puzzleMax.value
})
const progressWidth = computed(() => Math.min(
  handleLeft.value + HANDLE_WIDTH / 2,
  trackRef.value?.clientWidth ?? 0
))
const sliderTip = computed(() => {
  if (captchaLoading.value) return '正在加载验证码'
  if (captchaChecking.value) return '正在校验，请稍候'
  if (captchaVerification.value) return '验证已通过'
  if (!hasCaptcha.value) return '点击右侧按钮加载验证码'
  if (dragging.value || handleLeft.value > 0) return '松开滑块开始校验'
  return '按住滑块并拖动到缺口位置'
})

watch(
  () => props.modelValue,
  async (visible) => {
    if (!visible) {
      removePointerListeners()
      return
    }
    if (!hasCaptcha.value && !captchaLoading.value) {
      await nextTick()
      await refreshCaptcha()
    }
  }
)

onBeforeUnmount(() => {
  removePointerListeners()
})

function handleVisibleChange(visible: boolean) {
  emit('update:modelValue', visible)
}

function resetCaptchaState() {
  captcha.originalImageBase64 = ''
  captcha.jigsawImageBase64 = ''
  captcha.token = ''
  captcha.secretKey = ''
  captcha.captchaType = 'blockPuzzle'
  captchaVerification.value = ''
  handleLeft.value = 0
  dragging.value = false
  removePointerListeners()
}

function requireApiUrl(): string | null {
  const apiUrl = props.apiUrl?.trim()
  if (!apiUrl) {
    ElMessage.warning('远端服务地址暂不可用，请稍后重试')
    return null
  }
  return apiUrl
}

function unwrapExternalAiPayload(data: Record<string, unknown> | null | undefined): Record<string, unknown> {
  if (!data) return {}
  if (isPlainRecord(data.repData)) return data.repData
  if (isPlainRecord(data.data)) return data.data
  return data
}

function isPlainRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function readStringValue(data: Record<string, unknown>, key: string): string {
  const value = data[key]
  return typeof value === 'string' ? value : ''
}

function toBase64Image(value: string | undefined): string {
  if (!value) return ''
  if (value.startsWith('data:')) return value
  return `data:image/png;base64,${value}`
}

function measureCaptchaImages() {
  backgroundWidth.value = backgroundRef.value?.clientWidth ?? 0
  puzzleWidth.value = puzzleRef.value?.clientWidth ?? 0
}

async function refreshCaptcha() {
  const apiUrl = requireApiUrl()
  if (!apiUrl) return

  resetCaptchaState()
  captchaLoading.value = true
  try {
    const data = unwrapExternalAiPayload(await externalAiGetCaptcha({
      apiUrl,
      payload: { captchaType: 'blockPuzzle' }
    }))
    captcha.originalImageBase64 = readStringValue(data, 'originalImageBase64')
    captcha.jigsawImageBase64 = readStringValue(data, 'jigsawImageBase64')
    captcha.token = readStringValue(data, 'token')
    captcha.secretKey = readStringValue(data, 'secretKey')
    captcha.captchaType = readStringValue(data, 'captchaType') || 'blockPuzzle'
    await nextTick()
    measureCaptchaImages()
  } catch {
    // Error handled by request interceptor.
  } finally {
    captchaLoading.value = false
  }
}

function startCaptchaDrag(event: PointerEvent) {
  if (!hasCaptcha.value || captchaLoading.value || captchaChecking.value) return
  dragging.value = true
  dragStartX.value = event.clientX
  dragStartLeft.value = handleLeft.value
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp)
}

function onPointerMove(event: PointerEvent) {
  if (!dragging.value) return
  const deltaX = event.clientX - dragStartX.value
  const nextLeft = dragStartLeft.value + deltaX
  handleLeft.value = Math.min(Math.max(nextLeft, 0), trackMax.value)
}

async function onPointerUp() {
  if (!dragging.value) return
  dragging.value = false
  removePointerListeners()

  if (!handleLeft.value) return
  await verifyCaptcha()
}

function removePointerListeners() {
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
}

async function verifyCaptcha() {
  const apiUrl = requireApiUrl()
  if (!apiUrl || !captcha.token || captchaChecking.value) return

  captchaChecking.value = true
  try {
    const result = unwrapExternalAiPayload(await externalAiCheckCaptcha({
      apiUrl,
      payload: {
        token: captcha.token,
        pointX: Math.round(puzzleLeft.value),
        pointY: DEFAULT_POINT_Y,
        secretKey: captcha.secretKey || '',
        captchaType: captcha.captchaType || 'blockPuzzle'
      }
    }))
    captchaVerification.value = readStringValue(result, 'captchaVerification')
    if (captchaVerification.value) {
      ElMessage.success('图形验证码已通过')
    } else {
      ElMessage.warning('未获取到图形验证码校验串，请重新验证')
      await refreshCaptcha()
    }
  } catch {
    await refreshCaptcha()
  } finally {
    captchaChecking.value = false
  }
}

async function sendSmsCode() {
  const apiUrl = requireApiUrl()
  if (!apiUrl) return
  if (!mobile.value.trim()) {
    ElMessage.warning('请填写手机号')
    return
  }
  if (!captchaVerification.value) {
    ElMessage.warning('请先完成图形验证码')
    return
  }

  smsSending.value = true
  try {
    await externalAiSendSmsCode({
      apiUrl,
      mobile: mobile.value.trim(),
      captchaVerification: captchaVerification.value
    })
    ElMessage.success('短信验证码已发送')
  } catch {
    // Error handled by request interceptor.
  } finally {
    smsSending.value = false
  }
}

async function completeMobile() {
  const apiUrl = requireApiUrl()
  if (!apiUrl) return
  const normalizedMobile = mobile.value.trim()
  const code = verificationCode.value.trim()
  if (!normalizedMobile) {
    ElMessage.warning('请填写手机号')
    return
  }
  if (!code) {
    ElMessage.warning('请填写短信验证码')
    return
  }

  submitting.value = true
  try {
    const result = await externalAiCompleteMobile({
      apiUrl,
      mobile: normalizedMobile,
      verificationCode: code
    })
    verificationCode.value = ''
    resetCaptchaState()
    ElMessage.success('手机号已完善，积分已发放')
    emit('completed', result)
    emit('update:modelValue', false)
  } catch {
    // Error handled by request interceptor.
  } finally {
    submitting.value = false
  }
}
</script>
