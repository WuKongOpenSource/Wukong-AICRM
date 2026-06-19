<template>
  <div class="space-y-6">
    <el-card shadow="never" class="!border-slate-200">
      <template #header>
        <div class="flex items-center justify-between gap-4">
          <span class="font-medium">AI 大模型配置</span>
          <el-tag v-if="aiConfigForm.updateTime" size="small" type="info">
            最后更新 {{ formatTime(aiConfigForm.updateTime) }}
          </el-tag>
        </div>
      </template>

      <div class="mb-6 rounded-2xl border border-slate-200 bg-white px-4 py-4">
        <div class="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <div>
            <p class="text-xs font-bold uppercase tracking-widest text-slate-400">已保存服务商</p>
            <p class="mt-2 text-sm text-slate-600">
              悟空云 AI 由系统自动维护密钥；也可以添加其它 OpenAI 兼容服务商并切换生效。
            </p>
          </div>
          <el-tag type="primary" effect="plain">
            多服务商配置池
          </el-tag>
        </div>

        <div v-if="configuredProviders.length" class="mt-4 flex flex-wrap gap-2">
          <button
            v-for="provider in configuredProviders"
            :key="provider.value"
            type="button"
            class="inline-flex items-center gap-2 rounded-full border px-3 py-2 text-sm font-medium transition-all"
            :class="provider.value === aiConfigForm.provider
              ? 'border-primary bg-primary/5 text-primary'
              : 'border-slate-200 bg-slate-50 text-slate-600 hover:border-primary/40 hover:text-primary'"
            @click="handleProviderChange(provider.value)"
          >
            <span>{{ provider.label }}</span>
            <span
              class="rounded-full px-2 py-0.5 text-[11px]"
              :class="provider.active ? 'bg-primary text-white' : 'bg-slate-200 text-slate-600'"
            >
              {{ provider.active ? '当前生效' : '已保存' }}
            </span>
          </button>
        </div>
        <p v-else class="mt-4 text-sm text-slate-400">
          还没有已保存的自定义服务商配置，保存后会自动加入配置池；悟空云 AI 首次登录后会自动开通。
        </p>
      </div>

      <el-form :model="aiConfigForm" label-position="top" class="max-w-3xl">
        <el-form-item label="AI 服务商">
          <div class="flex w-full flex-col gap-3">
            <div class="flex w-full flex-col gap-3 md:flex-row md:items-center">
              <el-select :model-value="aiConfigForm.provider" class="min-w-0 flex-1" @change="handleProviderChange">
                <el-option
                  v-for="preset in providerOptions"
                  :key="preset.value"
                  :label="preset.label"
                  :value="preset.value"
                />
              </el-select>
              <el-button
                v-if="isWukongExternalProvider"
                class="!h-10 !rounded-xl !border-slate-300 !bg-white !px-4 !font-medium !text-slate-700 shadow-sm transition-all hover:!border-primary hover:!bg-primary/5 hover:!text-primary"
                plain
                @click="handleSelectCustomProvider"
              >
                <el-icon class="mr-1"><Plus /></el-icon>
                添加自定义服务商
              </el-button>
            </div>
            <div class="flex flex-wrap items-center gap-2 text-xs text-slate-400">
              <span>{{ providerDescription }}</span>
              <el-tag v-if="currentProviderPreset?.configured" size="small" type="success" effect="plain">
                {{ isWukongExternalProvider ? '已开通' : '已保存该服务商配置' }}
              </el-tag>
              <el-tag v-if="currentProviderPreset?.active" size="small" type="primary" effect="plain">
                当前默认生效
              </el-tag>
              <el-tag v-if="isWukongMobileCompleted" size="small" type="success" effect="plain">
                手机号已完善
              </el-tag>
            </div>
          </div>
        </el-form-item>

        <el-form-item v-if="!isWukongExternalProvider" label="API 基础地址">
          <el-input v-model="aiConfigForm.apiUrl" placeholder="https://api.openai.com">
            <template #prepend>URL</template>
          </el-input>
          <div class="mt-1 text-xs text-slate-400">
            这里填写兼容 OpenAI Chat Completions 的基础地址，不需要额外填写结尾的 `/v1`。
          </div>
        </el-form-item>

        <div
          v-if="isWukongExternalProvider"
          class="mb-6 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm"
        >
          <div class="flex flex-col gap-4 border-b border-slate-100 bg-slate-50/70 px-5 py-4 md:flex-row md:items-center md:justify-between">
            <div class="flex min-w-0 items-center gap-3">
              <span class="flex size-11 shrink-0 items-center justify-center overflow-hidden rounded-2xl bg-white shadow-sm ring-1 ring-slate-100">
                <img src="/logo.png" alt="" class="size-full object-cover" />
              </span>
              <div class="min-w-0">
                <p class="truncate text-base font-semibold text-slate-950">悟空云 AI 额度</p>
                <p class="mt-1 text-sm leading-5 text-slate-500">
                  绑定手机号可领取更多额度，也可以直接购买额度。
                </p>
              </div>
            </div>
            <div class="flex shrink-0 flex-wrap gap-2">
              <el-tag type="primary" effect="plain">系统托管</el-tag>
              <el-tag type="success" effect="plain">无需 API Key</el-tag>
              <el-button size="small" type="primary" plain :loading="externalAiUsageLoading" @click="openExternalAiPurchaseDialog">
                购买额度
              </el-button>
            </div>
          </div>

          <div class="grid gap-3 border-b border-slate-100 bg-white px-5 py-4 sm:grid-cols-3">
            <div class="rounded-lg border border-slate-200 bg-slate-50 px-4 py-3">
              <p class="text-xs font-medium text-slate-500">可用额度</p>
              <p class="mt-2 text-lg font-semibold tabular-nums text-slate-950">{{ formatCreditValue(externalAiUsage?.creditRemaining ?? 0) }}</p>
            </div>
            <div class="rounded-lg border border-slate-200 bg-white px-4 py-3">
              <p class="text-xs font-medium text-slate-500">购买剩余</p>
              <p class="mt-2 text-lg font-semibold tabular-nums text-slate-950">{{ formatCreditValue(externalAiUsage?.purchasedCreditRemaining ?? 0) }}</p>
            </div>
            <div class="rounded-lg border border-slate-200 bg-white px-4 py-3">
              <p class="text-xs font-medium text-slate-500">已使用</p>
              <p class="mt-2 text-lg font-semibold tabular-nums text-slate-950">{{ formatCreditValue(externalAiUsage?.creditUsed ?? 0) }}</p>
            </div>
          </div>

          <div v-if="isWukongMobileCompleted" class="p-5">
            <div class="flex flex-col gap-4 rounded-2xl border border-slate-200 bg-white px-5 py-5 shadow-sm md:flex-row md:items-center md:justify-between">
              <div class="flex items-start gap-3">
                <span class="material-symbols-outlined mt-0.5 rounded-full bg-emerald-50 p-2 text-xl text-emerald-700 ring-1 ring-emerald-100">
                  check
                </span>
                <div>
                  <p class="text-base font-semibold text-slate-950">手机号已完善，额度已发放</p>
                  <p class="mt-1 max-w-2xl text-sm leading-6 text-slate-600">
                    悟空云 AI 已完成手机号验证，当前无需继续配置密钥；需要更多额度时可直接购买。
                  </p>
                </div>
              </div>
              <div class="flex shrink-0 flex-wrap gap-2">
                <el-tag type="success" effect="dark">已领取额度</el-tag>
                <el-tag type="primary" effect="plain">可购买额度</el-tag>
              </div>
            </div>
          </div>

          <div v-else class="grid gap-5 p-5 lg:grid-cols-[minmax(0,360px)_minmax(0,1fr)]">
            <div class="space-y-4">
              <div class="rounded-xl border border-slate-200 bg-white p-4">
                <div class="mb-3 flex items-center gap-2">
                  <span class="flex size-6 items-center justify-center rounded-full bg-primary text-xs font-bold text-white">1</span>
                  <span class="text-sm font-semibold text-slate-900">填写手机号</span>
                </div>
                <el-form-item label="手机号" class="!mb-0">
                  <el-input v-model="externalAiForm.mobile" placeholder="请输入手机号" />
                </el-form-item>
              </div>

              <div class="rounded-xl border border-slate-200 bg-white p-4">
                <div class="mb-3 flex items-center gap-2">
                  <span class="flex size-6 items-center justify-center rounded-full bg-primary text-xs font-bold text-white">3</span>
                  <span class="text-sm font-semibold text-slate-900">完成短信验证</span>
                </div>
                <div class="grid gap-3 sm:grid-cols-[1fr_auto]">
                  <el-form-item label="短信验证码" class="!mb-0">
                    <el-input v-model="externalAiForm.verificationCode" placeholder="请输入短信验证码" />
                  </el-form-item>
                  <el-button
                    class="self-end"
                    :loading="externalAiSmsSending"
                    :disabled="!externalAiCaptchaVerification"
                    @click="handleSendExternalAiSms"
                  >
                    发送验证码
                  </el-button>
                </div>
              </div>

              <div class="flex flex-wrap items-center gap-3">
                <el-button
                  type="primary"
                  size="large"
                  :loading="externalAiRegistering"
                  @click="handleExternalAiCompleteMobile"
                >
                  完善手机号并领取额度
                </el-button>
                <span v-if="externalAiSavedKeyPrefix" class="text-xs font-medium text-emerald-600">
                  手机号已完善，更多额度已发放。
                </span>
              </div>
            </div>

            <div class="rounded-xl border border-slate-200 bg-slate-50 p-4">
              <div class="mb-4 flex items-center justify-between gap-3">
                <div class="flex items-center gap-2">
                  <span class="flex size-6 items-center justify-center rounded-full bg-primary text-xs font-bold text-white">2</span>
                  <div>
                    <p class="text-sm font-semibold text-slate-900">图形验证</p>
                    <p class="mt-0.5 text-xs text-slate-500">拖动滑块完成校验后才能发送短信。</p>
                  </div>
                </div>
                <el-button
                  plain
                  :loading="externalAiCaptchaLoading"
                  :disabled="externalAiCaptchaChecking"
                  @click="refreshExternalAiCaptcha"
                >
                  {{ externalAiHasCaptcha ? '换一张' : '获取验证码' }}
                </el-button>
              </div>

              <div v-if="externalAiHasCaptcha" class="space-y-3">
                <div class="relative overflow-hidden rounded-xl border border-slate-200 bg-white shadow-inner">
                  <img
                    ref="externalAiBackgroundRef"
                    :src="externalAiBackgroundImage"
                    alt="验证码背景"
                    class="block w-full select-none pointer-events-none"
                    @load="measureExternalAiCaptchaImages"
                  />
                  <img
                    v-if="externalAiPuzzleImage"
                    ref="externalAiPuzzleRef"
                    :src="externalAiPuzzleImage"
                    alt="滑块拼图"
                    class="absolute inset-y-0 left-0 h-full max-w-none select-none pointer-events-none drop-shadow-md"
                    :style="{ transform: `translateX(${externalAiPuzzleLeft}px)` }"
                    @load="measureExternalAiCaptchaImages"
                  />
                  <div
                    v-if="externalAiCaptchaLoading || externalAiCaptchaChecking"
                    class="absolute inset-0 flex items-center justify-center bg-white/75 text-sm font-medium text-slate-600"
                  >
                    {{ externalAiCaptchaLoading ? '正在加载验证码...' : '正在校验，请稍候...' }}
                  </div>
                </div>

                <div
                  ref="externalAiTrackRef"
                  class="relative h-12 overflow-hidden rounded-full border border-slate-200 bg-white select-none"
                >
                  <div
                    class="absolute inset-y-0 left-0 rounded-full bg-primary/15 transition-all"
                    :style="{ width: `${externalAiProgressWidth}px` }"
                  />
                  <div class="absolute inset-0 flex items-center justify-center px-16 text-sm text-slate-500">
                    {{ externalAiSliderTip }}
                  </div>
                  <button
                    type="button"
                    class="absolute left-0 top-1/2 flex h-12 w-[52px] -translate-y-1/2 items-center justify-center rounded-full bg-primary text-white shadow-lg shadow-primary/20 transition-transform active:scale-95 disabled:cursor-not-allowed disabled:bg-slate-300 disabled:shadow-none"
                    :style="{ transform: `translate(${externalAiHandleLeft}px, -50%)` }"
                    :disabled="externalAiCaptchaLoading || externalAiCaptchaChecking || externalAiCaptchaVerification !== ''"
                    @pointerdown.prevent="startExternalAiCaptchaDrag"
                  >
                    <span class="material-symbols-outlined text-xl">chevron_right</span>
                  </button>
                </div>

                <p
                  v-if="externalAiCaptchaVerification"
                  class="rounded-lg bg-emerald-50 px-3 py-2 text-xs font-medium text-emerald-700"
                >
                  图形验证码已通过，可以发送短信验证码。
                </p>
              </div>

              <button
                v-else
                type="button"
                class="flex min-h-[180px] w-full flex-col items-center justify-center rounded-xl border border-dashed border-slate-300 bg-white px-4 text-center transition-colors hover:border-primary/40 hover:bg-primary/5"
                :disabled="externalAiCaptchaLoading"
                @click="refreshExternalAiCaptcha"
              >
                <span class="material-symbols-outlined text-3xl text-slate-300">verified_user</span>
                <span class="mt-3 text-sm font-semibold text-slate-900">获取图形验证码</span>
                <span class="mt-1 text-xs text-slate-500">加载后拖动滑块完成验证</span>
              </button>
            </div>
          </div>
        </div>

        <template v-if="!isWukongExternalProvider">
        <el-form-item label="API 密钥">
          <div class="flex w-full gap-2">
            <el-input
              v-model="aiConfigForm.apiKey"
              :type="showApiKey ? 'text' : 'password'"
              placeholder="sk-xxxxxx"
              class="flex-1"
            >
              <template #prepend>Key</template>
              <template #suffix>
                <el-icon class="cursor-pointer" @click="showApiKey = !showApiKey">
                  <View v-if="showApiKey" />
                  <Hide v-else />
                </el-icon>
              </template>
            </el-input>
            <el-button :loading="testingConnection" @click="handleTestConnection">
              <el-icon class="mr-1"><Connection /></el-icon>
              测试连接
            </el-button>
          </div>
          <div class="mt-1 text-xs text-slate-400">
            <template v-if="currentProviderPreset?.apiKeyConfigured">
              当前服务商已保存 API Key，不重新输入时保存会沿用已有 Key；如需覆盖或测试请重新输入。
            </template>
            <template v-else>
              出于安全原因，系统不会回显已保存的 API Key。保存或测试前需要重新输入。
            </template>
          </div>
          <div v-if="connectionTestResult" class="mt-2">
            <el-alert :type="connectionTestResult.success ? 'success' : 'error'" :closable="false" show-icon>
              <template #title>
                {{ connectionTestResult.success ? '连接成功' : '连接失败' }}
                <span class="ml-2 text-slate-500">({{ connectionTestResult.responseTime }}ms)</span>
              </template>
              <template #default>
                {{ connectionTestResult.message }}
              </template>
            </el-alert>
          </div>
        </el-form-item>

        <el-form-item label="模型">
          <el-select
            v-model="aiConfigForm.model"
            class="w-full"
            filterable
            allow-create
            default-first-option
            placeholder="请选择或输入模型名称"
          >
            <el-option
              v-for="model in currentProviderModels"
              :key="model"
              :label="model"
              :value="model"
            />
          </el-select>
          <div class="mt-1 text-xs text-slate-400">
            {{ currentModelHint }}
          </div>
        </el-form-item>

        <el-form-item label="模型能力">
          <div class="flex flex-wrap gap-2">
            <el-tag :type="currentCapabilities.supportsStream ? 'success' : 'info'">
              {{ currentCapabilities.supportsStream ? '支持流式输出' : '不支持流式输出' }}
            </el-tag>
            <el-tag :type="currentCapabilities.supportsToolCall ? 'success' : 'warning'">
              {{ currentCapabilities.supportsToolCall ? '支持工具调用' : '不支持工具调用' }}
            </el-tag>
            <el-tag :type="currentCapabilities.supportsVision ? 'success' : 'info'">
              {{ currentCapabilities.supportsVision ? '支持视觉输入' : '文本模型' }}
            </el-tag>
            <el-tag :type="currentCapabilities.supportsAudioTranscription ? 'success' : 'info'">
              {{ currentCapabilities.supportsAudioTranscription ? '支持语音转写' : '不支持语音转写' }}
            </el-tag>
          </div>
          <div class="mt-1 text-xs text-slate-400">
            能力展示优先使用当前已保存模型的后端判定；修改模型后，最终以“测试连接”和“保存配置”的校验结果为准。
          </div>
        </el-form-item>

        <el-form-item label="额外请求头 (JSON)">
          <el-input
            v-model="aiConfigForm.extraHeadersJson"
            type="textarea"
            :rows="4"
            placeholder="{&quot;appid&quot;:&quot;your-app-id&quot;}"
          />
          <div class="mt-1 text-xs text-slate-400">
            用于兼容某些服务商的附加请求头。{{ currentExtraHeadersHint || '如无需额外请求头可留空。' }}
          </div>
          <div v-if="currentProviderPreset?.savedExtraHeadersConfigured" class="mt-1 text-xs text-emerald-600">
            当前服务商已保存额外请求头，页面中展示的是管理端可编辑的原始配置。
          </div>
        </el-form-item>

        <el-form-item label="Temperature">
          <div class="flex w-full items-center gap-4">
            <el-slider
              v-model="aiConfigForm.temperature"
              :min="0"
              :max="2"
              :step="0.1"
              :format-tooltip="(value: number) => value.toFixed(1)"
              class="flex-1"
            />
            <el-input-number
              v-model="aiConfigForm.temperature"
              :min="0"
              :max="2"
              :step="0.1"
              :precision="1"
              class="w-24"
            />
          </div>
        </el-form-item>

        <el-form-item label="最大 Token 数">
          <el-input-number
            v-model="aiConfigForm.maxTokens"
            :min="100"
            :max="128000"
            :step="100"
            class="w-full"
          />
        </el-form-item>

        <div class="flex flex-wrap gap-3 border-t border-slate-200 pt-4">
          <el-button type="primary" :loading="savingAiConfig" @click="handleSaveAiConfig">
            <el-icon class="mr-1"><Document /></el-icon>
            保存当前服务商配置
          </el-button>
          <el-button
            plain
            :disabled="!currentProviderPreset?.configured || currentProviderPreset?.active"
            :loading="activatingProvider"
            @click="handleActivateProvider"
          >
            启用当前服务商
          </el-button>
          <el-button @click="loadAiConfig">重置</el-button>
        </div>
        </template>
      </el-form>
    </el-card>

    <el-card shadow="never" class="!border-slate-200">
      <template #header>
        <span class="font-medium">配置说明</span>
      </template>

      <div class="space-y-3 text-sm text-slate-600">
        <p>当前 CRM 默认要求主对话模型支持工具调用，否则无法正常驱动客户、任务、跟进等内置工具。</p>
        <p>悟空云 AI 的密钥由系统托管，不支持手动填写；其它服务商仍可按 OpenAI 兼容协议配置。</p>
        <p>悟空云 AI 当前可通过手机号验证领取更多额度，也可通过额度购买补充使用量。</p>
      </div>
    </el-card>

    <ExternalAiPurchaseDialog
      v-model="externalAiPurchaseDialogVisible"
      @paid="handleExternalAiPurchasePaid"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Connection, Document, Hide, Plus, View } from '@element-plus/icons-vue'
import {
  activateAiProvider,
  externalAiCheckCaptcha,
  externalAiCompleteMobile,
  externalAiGetCaptcha,
  externalAiSendSmsCode,
  getExternalAiUsage,
  getAiConfigDetail,
  testAiConnection,
  updateAiConfig
} from '@/api/systemConfig'
import type {
  AiConfig,
  AiConfigUpdateBO,
  AiConnectionTestResult,
  AiModelCapabilities,
  AiProvider,
  AiProviderPreset,
  ExternalAiUsage
} from '@/types/systemConfig'
import ExternalAiPurchaseDialog from '@/components/common/ExternalAiPurchaseDialog.vue'
import { AI_PROVIDER_PRESETS } from '../constants'

type AiConfigFormState = AiConfigUpdateBO & {
  updateTime?: string
  configured?: boolean
  apiKeyConfigured?: boolean
}

type ProviderDraftMap = Partial<Record<AiProvider, AiConfigFormState>>

type ExternalAiCaptchaData = {
  originalImageBase64: string
  jigsawImageBase64: string
  token: string
  secretKey?: string
  captchaType?: string
}

const WUKONG_EXTERNAL_PROVIDER: AiProvider = 'wukong_external'
const DEFAULT_PROVIDER: AiProvider = WUKONG_EXTERNAL_PROVIDER
const EXTERNAL_AI_HANDLE_WIDTH = 52
const EXTERNAL_AI_DEFAULT_POINT_Y = 5
const WUKONG_EXTERNAL_PROVIDER_PRESET = AI_PROVIDER_PRESETS.find((item) => item.value === WUKONG_EXTERNAL_PROVIDER)

const showApiKey = ref(false)
const savingAiConfig = ref(false)
const testingConnection = ref(false)
const activatingProvider = ref(false)
const connectionTestResult = ref<AiConnectionTestResult | null>(null)
const providerOptions = ref<AiProviderPreset[]>(withWukongProviderLabel(AI_PROVIDER_PRESETS))
const providerDrafts = ref<ProviderDraftMap>({})
const loadedConfig = ref<AiConfig | null>(null)

const externalAiBackgroundRef = ref<HTMLImageElement>()
const externalAiPuzzleRef = ref<HTMLImageElement>()
const externalAiTrackRef = ref<HTMLDivElement>()
const externalAiCaptchaLoading = ref(false)
const externalAiCaptchaChecking = ref(false)
const externalAiDragging = ref(false)
const externalAiHandleLeft = ref(0)
const externalAiDragStartX = ref(0)
const externalAiDragStartLeft = ref(0)
const externalAiBackgroundWidth = ref(0)
const externalAiPuzzleWidth = ref(0)
const externalAiCaptchaVerification = ref('')
const externalAiSmsSending = ref(false)
const externalAiRegistering = ref(false)
const externalAiSavedKeyPrefix = ref('')
const externalAiMobileCompleted = ref(false)
const externalAiUsageLoading = ref(false)
const externalAiPurchaseDialogVisible = ref(false)
const externalAiUsage = ref<ExternalAiUsage | null>(null)

const externalAiForm = reactive({
  mobile: '',
  verificationCode: ''
})

const externalAiCaptcha = reactive<ExternalAiCaptchaData>({
  originalImageBase64: '',
  jigsawImageBase64: '',
  token: '',
  secretKey: '',
  captchaType: 'blockPuzzle'
})

const aiConfigForm = reactive<AiConfigFormState>({
  provider: DEFAULT_PROVIDER,
  apiUrl: WUKONG_EXTERNAL_PROVIDER_PRESET?.baseUrl || 'https://www.72crm.com/crmapi/',
  apiKey: '',
  model: 'qwen3.6-plus',
  temperature: 0.7,
  maxTokens: 2048,
  extraHeadersJson: '',
  updateTime: undefined,
  configured: false,
  apiKeyConfigured: false
})

const currentProviderPreset = computed(() => {
  return providerOptions.value.find((item) => item.value === aiConfigForm.provider) || null
})

const configuredProviders = computed(() => providerOptions.value.filter((item) => item.configured))
const currentProviderModels = computed(() => currentProviderPreset.value?.models || [])

const currentCapabilities = computed<AiModelCapabilities>(() => {
  if (
    loadedConfig.value &&
    loadedConfig.value.provider === aiConfigForm.provider &&
    loadedConfig.value.model === aiConfigForm.model &&
    loadedConfig.value.capabilities
  ) {
    return loadedConfig.value.capabilities
  }

  return inferCapabilities(aiConfigForm.provider, aiConfigForm.model)
})

const currentModelHint = computed(() => {
  if (
    loadedConfig.value &&
    loadedConfig.value.provider === aiConfigForm.provider &&
    loadedConfig.value.model === aiConfigForm.model &&
    loadedConfig.value.modelHint
  ) {
    return loadedConfig.value.modelHint
  }
  return currentProviderPreset.value?.modelHint || '请选择或输入可用模型名称。'
})

const currentExtraHeadersHint = computed(() => {
  if (
    loadedConfig.value &&
    loadedConfig.value.provider === aiConfigForm.provider &&
    loadedConfig.value.extraHeadersHint
  ) {
    return loadedConfig.value.extraHeadersHint
  }
  return currentProviderPreset.value?.extraHeadersHint || ''
})

const isWukongExternalProvider = computed(() => aiConfigForm.provider === 'wukong_external')
const isWukongMobileCompleted = computed(() => Boolean(
  isWukongExternalProvider.value &&
  (
    externalAiMobileCompleted.value ||
    currentProviderPreset.value?.mobileCompleted ||
    loadedConfig.value?.wukongExternalMobileCompleted
  )
))
const providerDescription = computed(() => {
  if (isWukongExternalProvider.value) {
    return '系统自动注册与维护，不支持手动配置 API Key；完善手机号后可获取更多额度。'
  }
  return currentProviderPreset.value?.description || '支持 OpenAI 兼容接口的通用模型配置。'
})
const externalAiHasCaptcha = computed(() => Boolean(
  externalAiCaptcha.token &&
  externalAiCaptcha.originalImageBase64 &&
  externalAiCaptcha.jigsawImageBase64
))
const externalAiBackgroundImage = computed(() => toBase64Image(externalAiCaptcha.originalImageBase64))
const externalAiPuzzleImage = computed(() => toBase64Image(externalAiCaptcha.jigsawImageBase64))
const externalAiTrackMax = computed(() => Math.max((externalAiTrackRef.value?.clientWidth ?? 0) - EXTERNAL_AI_HANDLE_WIDTH, 0))
const externalAiPuzzleMax = computed(() => Math.max(externalAiBackgroundWidth.value - externalAiPuzzleWidth.value, 0))
const externalAiPuzzleLeft = computed(() => {
  if (!externalAiTrackMax.value || !externalAiPuzzleMax.value) return 0
  return (externalAiHandleLeft.value / externalAiTrackMax.value) * externalAiPuzzleMax.value
})
const externalAiProgressWidth = computed(() => Math.min(
  externalAiHandleLeft.value + EXTERNAL_AI_HANDLE_WIDTH / 2,
  externalAiTrackRef.value?.clientWidth ?? 0
))
const externalAiSliderTip = computed(() => {
  if (externalAiCaptchaLoading.value) return '正在加载验证码'
  if (externalAiCaptchaChecking.value) return '正在校验，请稍候'
  if (externalAiCaptchaVerification.value) return '验证已通过'
  if (!externalAiHasCaptcha.value) return '点击右侧按钮加载验证码'
  if (externalAiDragging.value || externalAiHandleLeft.value > 0) return '松开滑块开始校验'
  return '按住滑块并拖动到缺口位置'
})

onMounted(async () => {
  await loadAiConfig()
})

onBeforeUnmount(() => {
  removeExternalAiPointerListeners()
})

function createProviderDraft(preset?: AiProviderPreset | null): AiConfigFormState {
  const provider = (preset?.value || DEFAULT_PROVIDER) as AiProvider
  return {
    provider,
    apiUrl: preset?.savedApiUrl || preset?.baseUrl || '',
    apiKey: '',
    model: preset?.savedModel || preset?.models?.[0] || '',
    temperature: preset?.savedTemperature ?? 0.7,
    maxTokens: preset?.savedMaxTokens ?? 2048,
    extraHeadersJson: preset?.savedExtraHeadersJson ?? '',
    updateTime: undefined,
    configured: Boolean(preset?.configured),
    apiKeyConfigured: Boolean(preset?.apiKeyConfigured)
  }
}

function getCurrentProvider(): AiProvider {
  return aiConfigForm.provider ?? DEFAULT_PROVIDER
}

function syncCurrentFormToDraft() {
  const provider = getCurrentProvider()
  providerDrafts.value[provider] = {
    provider,
    apiUrl: aiConfigForm.apiUrl,
    apiKey: aiConfigForm.apiKey,
    model: aiConfigForm.model,
    temperature: aiConfigForm.temperature,
    maxTokens: aiConfigForm.maxTokens,
    extraHeadersJson: aiConfigForm.extraHeadersJson || '',
    updateTime: aiConfigForm.updateTime,
    configured: aiConfigForm.configured,
    apiKeyConfigured: aiConfigForm.apiKeyConfigured
  }
}

function applyProviderDraft(provider: AiProvider) {
  const preset = providerOptions.value.find((item) => item.value === provider) || null
  const draft = providerDrafts.value[provider] || createProviderDraft(preset)
  providerDrafts.value[provider] = draft

  Object.assign(aiConfigForm, {
    provider,
    apiUrl: draft.apiUrl,
    apiKey: draft.apiKey,
    model: draft.model,
    temperature: draft.temperature ?? 0.7,
    maxTokens: draft.maxTokens ?? 2048,
    extraHeadersJson: draft.extraHeadersJson || '',
    updateTime: draft.updateTime,
    configured: draft.configured ?? false,
    apiKeyConfigured: draft.apiKeyConfigured ?? false
  })

  showApiKey.value = false
  connectionTestResult.value = null
}

function resetExternalAiCaptchaState() {
  externalAiCaptcha.originalImageBase64 = ''
  externalAiCaptcha.jigsawImageBase64 = ''
  externalAiCaptcha.token = ''
  externalAiCaptcha.secretKey = ''
  externalAiCaptcha.captchaType = 'blockPuzzle'
  externalAiCaptchaVerification.value = ''
  externalAiHandleLeft.value = 0
  externalAiDragging.value = false
  removeExternalAiPointerListeners()
}

function handleProviderChange(provider: AiProvider) {
  syncCurrentFormToDraft()
  applyProviderDraft(provider)
  if (provider !== 'wukong_external') {
    resetExternalAiCaptchaState()
    externalAiUsage.value = null
  } else {
    loadExternalAiUsage()
  }
}

function handleSelectCustomProvider() {
  handleProviderChange('custom')
}

function withWukongProviderLabel(options?: AiProviderPreset[] | null): AiProviderPreset[] {
  const sourceOptions = options?.length ? options : AI_PROVIDER_PRESETS
  const hasWukongPreset = sourceOptions.some((item) => item.value === WUKONG_EXTERNAL_PROVIDER)
  const fallbackPreset: AiProviderPreset = WUKONG_EXTERNAL_PROVIDER_PRESET || {
    label: '悟空云 AI',
    value: WUKONG_EXTERNAL_PROVIDER,
    description: '系统自动注册并维护悟空云 AI。',
    baseUrl: 'https://www.72crm.com/crmapi/',
    models: ['qwen3.6-plus'],
    supportsStream: true,
    supportsToolCall: true,
    supportsVision: false,
    supportsAudioTranscription: false
  }

  const normalizedOptions = sourceOptions.map((item) => {
    if (item.value !== WUKONG_EXTERNAL_PROVIDER) return item
    return {
      ...item,
      label: '悟空云 AI',
      description: '系统自动注册与维护，不支持手动配置 API Key；完善手机号后可获取更多额度。'
    }
  })

  if (hasWukongPreset) return normalizedOptions

  return [
    ...normalizedOptions,
    fallbackPreset
  ]
}

async function loadAiConfig() {
  try {
    const config = await getAiConfigDetail()
    loadedConfig.value = config
    providerOptions.value = withWukongProviderLabel(config.availableProviders)
    externalAiMobileCompleted.value = Boolean(
      config.wukongExternalMobileCompleted ||
      providerOptions.value.find((item) => item.value === WUKONG_EXTERNAL_PROVIDER)?.mobileCompleted
    )

    const nextDrafts: ProviderDraftMap = {}
    providerOptions.value.forEach((preset) => {
      nextDrafts[preset.value] = createProviderDraft(preset)
    })

    const currentProvider = (config.provider
      || providerOptions.value.find((item) => item.active)?.value
      || WUKONG_EXTERNAL_PROVIDER) as AiProvider

    const currentDraft = nextDrafts[currentProvider]
    if (currentDraft) {
      currentDraft.updateTime = config.updateTime
    }

    providerDrafts.value = nextDrafts
    applyProviderDraft(currentProvider)
    if (currentProvider === WUKONG_EXTERNAL_PROVIDER) {
      await loadExternalAiUsage()
    } else {
      externalAiUsage.value = null
    }
  } catch {
    // Error handled by interceptor
  }
}

async function handleActivateProvider() {
  const provider = getCurrentProvider()

  activatingProvider.value = true
  try {
    await activateAiProvider({ provider })
    ElMessage.success(`已切换到 ${currentProviderPreset.value?.label || provider} 配置`)
    await loadAiConfig()
  } catch {
    // Error handled by interceptor
  } finally {
    activatingProvider.value = false
  }
}

async function handleTestConnection() {
  if (!aiConfigForm.apiUrl?.trim() || !aiConfigForm.apiKey?.trim()) {
    ElMessage.warning('请先填写 API 地址和 API 密钥')
    return
  }
  if (!aiConfigForm.model?.trim()) {
    ElMessage.warning('请先填写模型名称')
    return
  }

  testingConnection.value = true
  connectionTestResult.value = null

  try {
    const result = await testAiConnection({
      provider: getCurrentProvider(),
      apiUrl: aiConfigForm.apiUrl.trim(),
      apiKey: aiConfigForm.apiKey.trim(),
      model: aiConfigForm.model.trim(),
      temperature: aiConfigForm.temperature,
      maxTokens: aiConfigForm.maxTokens,
      extraHeadersJson: aiConfigForm.extraHeadersJson?.trim() || ''
    })
    connectionTestResult.value = result
  } catch (error: any) {
    connectionTestResult.value = {
      success: false,
      responseTime: 0,
      message: error.message || '连接测试失败'
    }
  } finally {
    testingConnection.value = false
  }
}

async function handleSaveAiConfig() {
  const trimmedApiKey = aiConfigForm.apiKey?.trim() || ''
  if (!aiConfigForm.apiUrl?.trim()) {
    ElMessage.warning('请填写 API 地址')
    return
  }
  if (!trimmedApiKey && !currentProviderPreset.value?.apiKeyConfigured) {
    ElMessage.warning('请填写 API 密钥，或先保存当前服务商的 API Key')
    return
  }
  if (!aiConfigForm.model?.trim()) {
    ElMessage.warning('请选择或输入模型名称')
    return
  }

  savingAiConfig.value = true
  try {
    await updateAiConfig({
      provider: getCurrentProvider(),
      apiUrl: aiConfigForm.apiUrl.trim(),
      apiKey: trimmedApiKey,
      model: aiConfigForm.model.trim(),
      temperature: aiConfigForm.temperature,
      maxTokens: aiConfigForm.maxTokens,
      extraHeadersJson: aiConfigForm.extraHeadersJson?.trim() || ''
    })
    ElMessage.success('当前服务商配置已保存，并已设为默认生效')
    await loadAiConfig()
  } catch {
    // Error handled by interceptor
  } finally {
    savingAiConfig.value = false
  }
}

function requireExternalAiApiUrl(): string | null {
  const apiUrl = aiConfigForm.apiUrl?.trim()
  if (!apiUrl) {
    ElMessage.warning('请先填写远端服务地址')
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

function measureExternalAiCaptchaImages() {
  externalAiBackgroundWidth.value = externalAiBackgroundRef.value?.clientWidth ?? 0
  externalAiPuzzleWidth.value = externalAiPuzzleRef.value?.clientWidth ?? 0
}

async function refreshExternalAiCaptcha() {
  const apiUrl = requireExternalAiApiUrl()
  if (!apiUrl) return

  resetExternalAiCaptchaState()
  externalAiCaptchaLoading.value = true
  try {
    const data = unwrapExternalAiPayload(await externalAiGetCaptcha({
      apiUrl,
      payload: { captchaType: 'blockPuzzle' }
    }))
    externalAiCaptcha.originalImageBase64 = readStringValue(data, 'originalImageBase64')
    externalAiCaptcha.jigsawImageBase64 = readStringValue(data, 'jigsawImageBase64')
    externalAiCaptcha.token = readStringValue(data, 'token')
    externalAiCaptcha.secretKey = readStringValue(data, 'secretKey')
    externalAiCaptcha.captchaType = readStringValue(data, 'captchaType') || 'blockPuzzle'
    await nextTick()
    measureExternalAiCaptchaImages()
  } catch {
    // Error handled by interceptor
  } finally {
    externalAiCaptchaLoading.value = false
  }
}

function startExternalAiCaptchaDrag(event: PointerEvent) {
  if (!externalAiHasCaptcha.value || externalAiCaptchaLoading.value || externalAiCaptchaChecking.value) return
  externalAiDragging.value = true
  externalAiDragStartX.value = event.clientX
  externalAiDragStartLeft.value = externalAiHandleLeft.value
  window.addEventListener('pointermove', onExternalAiPointerMove)
  window.addEventListener('pointerup', onExternalAiPointerUp)
}

function onExternalAiPointerMove(event: PointerEvent) {
  if (!externalAiDragging.value) return
  const deltaX = event.clientX - externalAiDragStartX.value
  const nextLeft = externalAiDragStartLeft.value + deltaX
  externalAiHandleLeft.value = Math.min(Math.max(nextLeft, 0), externalAiTrackMax.value)
}

async function onExternalAiPointerUp() {
  if (!externalAiDragging.value) return
  externalAiDragging.value = false
  removeExternalAiPointerListeners()

  if (!externalAiHandleLeft.value) {
    return
  }
  await verifyExternalAiCaptcha()
}

function removeExternalAiPointerListeners() {
  window.removeEventListener('pointermove', onExternalAiPointerMove)
  window.removeEventListener('pointerup', onExternalAiPointerUp)
}

async function verifyExternalAiCaptcha() {
  const apiUrl = requireExternalAiApiUrl()
  if (!apiUrl || !externalAiCaptcha.token || externalAiCaptchaChecking.value) return

  externalAiCaptchaChecking.value = true
  try {
    const result = unwrapExternalAiPayload(await externalAiCheckCaptcha({
      apiUrl,
      payload: {
        token: externalAiCaptcha.token,
        pointX: Math.round(externalAiPuzzleLeft.value),
        pointY: EXTERNAL_AI_DEFAULT_POINT_Y,
        secretKey: externalAiCaptcha.secretKey || '',
        captchaType: externalAiCaptcha.captchaType || 'blockPuzzle'
      }
    }))
    externalAiCaptchaVerification.value = readStringValue(result, 'captchaVerification')
    if (externalAiCaptchaVerification.value) {
      ElMessage.success('图形验证码已通过')
    } else {
      ElMessage.warning('未获取到图形验证码校验串，请重新验证')
      await refreshExternalAiCaptcha()
    }
  } catch {
    await refreshExternalAiCaptcha()
  } finally {
    externalAiCaptchaChecking.value = false
  }
}

async function handleSendExternalAiSms() {
  const apiUrl = requireExternalAiApiUrl()
  if (!apiUrl) return
  if (!externalAiForm.mobile.trim()) {
    ElMessage.warning('请填写手机号')
    return
  }
  if (!externalAiCaptchaVerification.value) {
    ElMessage.warning('请先完成图形验证码')
    return
  }

  externalAiSmsSending.value = true
  try {
    await externalAiSendSmsCode({
      apiUrl,
      mobile: externalAiForm.mobile.trim(),
      captchaVerification: externalAiCaptchaVerification.value
    })
    ElMessage.success('短信验证码已发送')
  } catch {
    // Error handled by interceptor
  } finally {
    externalAiSmsSending.value = false
  }
}

async function handleExternalAiCompleteMobile() {
  const apiUrl = requireExternalAiApiUrl()
  if (!apiUrl) return
  const mobile = externalAiForm.mobile.trim()
  const verificationCode = externalAiForm.verificationCode.trim()
  if (!mobile) {
    ElMessage.warning('请填写手机号')
    return
  }
  if (!verificationCode) {
    ElMessage.warning('请填写短信验证码')
    return
  }

  externalAiRegistering.value = true
  try {
    const result = await externalAiCompleteMobile({
      apiUrl,
      mobile,
      verificationCode
    })
    externalAiSavedKeyPrefix.value = result.keyPrefix || ''
    externalAiMobileCompleted.value = true
    externalAiForm.verificationCode = ''
    aiConfigForm.apiKeyConfigured = result.apiKeyConfigured
    resetExternalAiCaptchaState()
    ElMessage.success('手机号已完善，更多额度已发放')
    await loadAiConfig()
    await loadExternalAiUsage()
  } catch {
    // Error handled by interceptor
  } finally {
    externalAiRegistering.value = false
  }
}

async function loadExternalAiUsage() {
  externalAiUsageLoading.value = true
  try {
    externalAiUsage.value = await getExternalAiUsage(20)
  } catch {
    // Error handled by interceptor
  } finally {
    externalAiUsageLoading.value = false
  }
}

function openExternalAiPurchaseDialog() {
  externalAiPurchaseDialogVisible.value = true
}

async function handleExternalAiPurchasePaid() {
  await loadExternalAiUsage()
  await loadAiConfig()
}

function formatCreditValue(value: number | null | undefined): string {
  const safeValue = Math.max(Number(value || 0), 0)
  if (safeValue >= 10000) {
    return `${(safeValue / 10000).toFixed(safeValue % 10000 === 0 ? 0 : 1)}万`
  }
  return `${safeValue}`
}

function formatTime(time: string | undefined): string {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

function inferCapabilities(provider: AiProvider | undefined, model: string | undefined): AiModelCapabilities {
  const normalizedProvider = (provider || '').toLowerCase()
  const normalizedModel = (model || '').trim().toLowerCase()

  let supportsStream = currentProviderPreset.value?.supportsStream ?? true
  let supportsToolCall = currentProviderPreset.value?.supportsToolCall ?? true
  let supportsVision = currentProviderPreset.value?.supportsVision ?? false
  const supportsAudioTranscription = currentProviderPreset.value?.supportsAudioTranscription ?? false

  if (normalizedProvider === 'hunyuan') {
    supportsToolCall = normalizedModel.includes('functioncall')
    supportsVision = normalizedModel.includes('vision')
  } else if (normalizedProvider === 'moonshot') {
    supportsToolCall = normalizedModel !== '' && !normalizedModel.includes('kimi-thinking-preview')
    supportsVision = ['kimi-k2.5', 'kimi-k2.6', 'kimi-k2-5', 'kimi-k2-6', 'vision', 'vl']
      .some((keyword) => normalizedModel.includes(keyword))
  } else {
    if ([
      'gpt-5',
      '4o',
      '4.1',
      'vision',
      'omni',
      'qwen3.6-plus',
      'doubao-seed-2-0',
      'doubao-seed-2.0',
      '1-8',
      '4.6v',
      '4.5v',
      '4v',
      'vl'
    ].some((keyword) => normalizedModel.includes(keyword))) {
      supportsVision = true
    }
    if (['openai', 'dashscope', 'deepseek', 'ark', 'minimax', 'zhipu', 'wukong_external', 'custom'].includes(normalizedProvider)) {
      supportsToolCall = true
    }
  }

  return {
    supportsStream,
    supportsToolCall,
    supportsVision,
    supportsAudioTranscription
  }
}
</script>
