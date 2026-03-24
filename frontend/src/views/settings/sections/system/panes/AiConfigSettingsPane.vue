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

      <div class="mb-6 rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
        <div class="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <div>
            <p class="text-xs font-bold uppercase tracking-widest text-slate-400">当前模式</p>
            <div class="mt-2 flex items-center gap-2">
              <el-tag :type="currentMode === 'gift' ? 'success' : 'primary'">
                {{ currentMode === 'gift' ? '赠送额度模式' : '自定义模型模式' }}
              </el-tag>
              <span class="text-sm text-slate-500">
                剩余 {{ giftTokenRemainingWan }} / {{ giftTokenTotalWan }} 万 token
              </span>
            </div>
            <p class="mt-2 text-xs text-slate-500">
              未配置自定义模型时默认消耗赠送额度；切换到自定义模型后仍会继续统计 token 用量。
            </p>
          </div>

          <div class="flex gap-2">
            <el-button
              :disabled="!canUseGiftMode"
              :loading="switchingAiMode"
              @click="handleUseGiftMode"
            >
              使用赠送额度
            </el-button>
            <el-button
              type="primary"
              plain
              :disabled="!canUseSavedCustomMode"
              :loading="switchingAiMode"
              @click="handleUseSavedCustomMode"
            >
              启用已保存自定义模型
            </el-button>
          </div>
        </div>
      </div>

      <el-form :model="aiConfigForm" label-position="top" class="max-w-3xl">
        <el-form-item label="AI 服务商">
          <el-select v-model="aiConfigForm.provider" class="w-full" @change="handleProviderChange">
            <el-option
              v-for="preset in providerOptions"
              :key="preset.value"
              :label="preset.label"
              :value="preset.value"
            />
          </el-select>
          <div class="mt-1 text-xs text-slate-400">
            {{ currentProviderPreset?.description || '支持 OpenAI 兼容接口的通用模型配置。' }}
          </div>
        </el-form-item>

        <el-form-item label="API 基础地址">
          <el-input v-model="aiConfigForm.apiUrl" placeholder="https://api.openai.com">
            <template #prepend>URL</template>
          </el-input>
          <div class="mt-1 text-xs text-slate-400">
            这里填写兼容 OpenAI Chat Completions 的基础地址，不需要额外填写结尾的 `/v1`。
          </div>
        </el-form-item>

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
            出于安全原因，系统不会回显已保存的 API Key。保存或测试前需要重新输入。
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
          <div v-if="extraHeadersConfigured" class="mt-1 text-xs text-emerald-600">
            当前租户已配置额外请求头，页面中展示的是管理端可编辑的原始配置。
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

        <div class="flex gap-3 border-t border-slate-200 pt-4">
          <el-button type="primary" :loading="savingAiConfig" @click="handleSaveAiConfig">
            <el-icon class="mr-1"><Document /></el-icon>
            保存 AI 配置
          </el-button>
          <el-button @click="loadAiConfig">重置</el-button>
        </div>
      </el-form>
    </el-card>

    <el-card shadow="never" class="!border-slate-200">
      <template #header>
        <span class="font-medium">配置说明</span>
      </template>

      <div class="space-y-3 text-sm text-slate-600">
        <p>当前 CRM 默认要求主对话模型支持工具调用，否则无法正常驱动客户、任务、跟进等内置工具。</p>
        <p>如果你要接入百度千帆等需要额外头信息的服务，可以在“额外请求头 (JSON)”中补充。</p>
        <p>豆包、混元、智谱、Kimi 等厂商建议优先使用官方控制台里支持工具调用的模型或接入点。</p>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Connection, Document, Hide, View } from '@element-plus/icons-vue'
import { getAiConfigDetail, testAiConnection, updateAiConfig, useCustomAiConfig, useGiftAiConfig } from '@/api/systemConfig'
import type {
  AiConfig,
  AiConfigUpdateBO,
  AiConnectionTestResult,
  AiModelCapabilities,
  AiProvider,
  AiProviderPreset
} from '@/types/systemConfig'
import { AI_PROVIDER_PRESETS } from '../constants'

const showApiKey = ref(false)
const savingAiConfig = ref(false)
const testingConnection = ref(false)
const switchingAiMode = ref(false)
const connectionTestResult = ref<AiConnectionTestResult | null>(null)
const providerOptions = ref<AiProviderPreset[]>(AI_PROVIDER_PRESETS)
const loadedConfig = ref<AiConfig | null>(null)
const extraHeadersConfigured = ref(false)

const aiConfigForm = reactive<AiConfigUpdateBO & { updateTime?: string }>({
  provider: 'dashscope',
  apiUrl: 'https://dashscope.aliyuncs.com/compatible-mode',
  apiKey: '',
  model: 'qwen3.5-plus',
  temperature: 0.7,
  maxTokens: 2048,
  extraHeadersJson: '',
  updateTime: undefined
})

const currentProviderPreset = computed(() => {
  return providerOptions.value.find((item) => item.value === aiConfigForm.provider) || null
})

const currentMode = computed(() => loadedConfig.value?.mode || 'gift')
const giftTokenTotal = computed(() => loadedConfig.value?.giftTokenTotal ?? 0)
const giftTokenRemaining = computed(() => loadedConfig.value?.giftTokenRemaining ?? 0)
const giftTokenRemainingWan = computed(() => (giftTokenRemaining.value / 10000).toFixed(1))
const giftTokenTotalWan = computed(() => (giftTokenTotal.value / 10000).toFixed(1))
const canUseGiftMode = computed(() => currentMode.value !== 'gift' && giftTokenRemaining.value > 0)
const canUseSavedCustomMode = computed(() => currentMode.value !== 'custom' && Boolean(loadedConfig.value?.customConfigSaved))

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

onMounted(async () => {
  await loadAiConfig()
})

function handleProviderChange(provider: AiProvider) {
  const preset = providerOptions.value.find((item) => item.value === provider)
  if (!preset) return

  aiConfigForm.apiUrl = preset.baseUrl || ''
  if (preset.models.length > 0) {
    aiConfigForm.model = preset.models[0]
  }
  connectionTestResult.value = null
}

async function loadAiConfig() {
  try {
    const config = await getAiConfigDetail()
    loadedConfig.value = config
    providerOptions.value = config.availableProviders?.length ? config.availableProviders : AI_PROVIDER_PRESETS
    extraHeadersConfigured.value = Boolean(config.extraHeadersConfigured)

    Object.assign(aiConfigForm, {
      provider: (config.provider || 'dashscope') as AiProvider,
      apiUrl: config.apiUrl || '',
      apiKey: '',
      model: config.model || '',
      temperature: config.temperature ?? 0.7,
      maxTokens: config.maxTokens ?? 2048,
      extraHeadersJson: config.extraHeadersJson ?? '',
      updateTime: config.updateTime
    })

    connectionTestResult.value = null
  } catch {
    // Error handled by interceptor
  }
}

async function handleUseGiftMode() {
  switchingAiMode.value = true
  try {
    await useGiftAiConfig()
    ElMessage.success('已切换到赠送额度模式')
    await loadAiConfig()
  } catch {
    // Error handled by interceptor
  } finally {
    switchingAiMode.value = false
  }
}

async function handleUseSavedCustomMode() {
  switchingAiMode.value = true
  try {
    await useCustomAiConfig()
    ElMessage.success('已启用已保存的自定义模型')
    await loadAiConfig()
  } catch {
    // Error handled by interceptor
  } finally {
    switchingAiMode.value = false
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
      provider: aiConfigForm.provider,
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
  if (!aiConfigForm.apiUrl?.trim()) {
    ElMessage.warning('请填写 API 地址')
    return
  }
  if (!aiConfigForm.apiKey?.trim()) {
    ElMessage.warning('请填写 API 密钥')
    return
  }
  if (!aiConfigForm.model?.trim()) {
    ElMessage.warning('请选择或输入模型名称')
    return
  }

  savingAiConfig.value = true
  try {
    await updateAiConfig({
      provider: aiConfigForm.provider,
      apiUrl: aiConfigForm.apiUrl.trim(),
      apiKey: aiConfigForm.apiKey.trim(),
      model: aiConfigForm.model.trim(),
      temperature: aiConfigForm.temperature,
      maxTokens: aiConfigForm.maxTokens,
      extraHeadersJson: aiConfigForm.extraHeadersJson?.trim() || ''
    })
    ElMessage.success('AI 配置保存成功，已立即生效')
    await loadAiConfig()
  } catch {
    // Error handled by interceptor
  } finally {
    savingAiConfig.value = false
  }
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

  if (normalizedProvider === 'hunyuan') {
    supportsToolCall = normalizedModel.includes('functioncall')
    supportsVision = normalizedModel.includes('vision')
  } else if (normalizedProvider === 'moonshot') {
    supportsToolCall = normalizedModel !== '' && !normalizedModel.includes('kimi-thinking-preview')
    supportsVision = normalizedModel.includes('vision') || normalizedModel.includes('vl')
  } else {
    if (['gpt-5', '4o', '4.1', 'vision', 'omni', '3.5-plus', '1-8', '4.6v', '4.5v', '4v', 'vl'].some((keyword) => normalizedModel.includes(keyword))) {
      supportsVision = true
    }
    if (normalizedProvider === 'openai' || normalizedProvider === 'dashscope' || normalizedProvider === 'ark' || normalizedProvider === 'minimax' || normalizedProvider === 'zhipu' || normalizedProvider === 'custom') {
      supportsToolCall = true
    }
  }

  return {
    supportsStream,
    supportsToolCall,
    supportsVision
  }
}
</script>
