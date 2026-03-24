export type AiProvider =
  | 'openai'
  | 'dashscope'
  | 'moonshot'
  | 'ark'
  | 'hunyuan'
  | 'minimax'
  | 'zhipu'
  | 'custom'

export type AiMode = 'gift' | 'custom'

export interface AiModelCapabilities {
  supportsStream: boolean
  supportsToolCall: boolean
  supportsVision: boolean
}

export interface AiProviderPreset {
  label: string
  value: AiProvider
  description?: string
  baseUrl: string
  models: string[]
  modelHint?: string
  extraHeadersHint?: string
  supportsStream: boolean
  supportsToolCall: boolean
  supportsVision: boolean
}

export interface AiConfig {
  provider: AiProvider
  providerLabel?: string
  apiUrl: string
  apiKey: string
  model: string
  temperature: number
  maxTokens: number
  extraHeadersConfigured?: boolean
  extraHeadersJson?: string | null
  capabilities?: AiModelCapabilities
  modelHint?: string
  extraHeadersHint?: string
  availableProviders?: AiProviderPreset[]
  mode?: AiMode
  customConfigSaved?: boolean
  ready?: boolean
  giftTokenTotal?: number
  giftTokenUsed?: number
  giftTokenRemaining?: number
  giftTokenAvailable?: boolean
  updateTime?: string
}

export interface AiConfigUpdateBO {
  provider?: AiProvider
  apiUrl: string
  apiKey: string
  model: string
  temperature?: number
  maxTokens?: number
  extraHeadersJson?: string
}

export interface AiConnectionTestResult {
  success: boolean
  responseTime: number
  message: string
  model?: string
  provider?: AiProvider
}

export interface MinioConsoleConfig {
  enabled: boolean
  consoleUrl: string
}

export interface EnterpriseConfig {
  name: string | null
  logo: string | null
  logoUrl: string | null
  description: string | null
  updateTime?: string
}

export interface EnterpriseConfigUpdateBO {
  name?: string
  logo?: string
  description?: string
}
