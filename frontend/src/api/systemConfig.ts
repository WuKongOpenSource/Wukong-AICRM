import { get, post } from '@/utils/request'
import type {
  AiConfig,
  AiProviderActivateBO,
  AiConfigUpdateBO,
  ExternalAiCompleteMobileParams,
  AiConnectionTestResult,
  EnterpriseConfig,
  EnterpriseConfigUpdateBO,
  ExternalAiCaptchaProxyParams,
  ExternalAiRegisterAndSaveParams,
  ExternalAiRegisterAndSaveResult,
  ExternalAiPurchaseCreateParams,
  ExternalAiPurchaseOptions,
  ExternalAiPurchaseOrder,
  ExternalAiUsage,
  ExternalAiSmsCodeParams,
  MinioConsoleConfig
} from '@/types/systemConfig'

export function getAiConfig(): Promise<AiConfig> {
  return get('/systemConfig/ai')
}

export function getAiConfigDetail(): Promise<AiConfig> {
  return get('/systemConfig/ai/detail')
}

export function updateAiConfig(data: AiConfigUpdateBO): Promise<void> {
  return post('/systemConfig/ai/update', data)
}

export function activateAiProvider(data: AiProviderActivateBO): Promise<void> {
  return post('/systemConfig/ai/activate', data)
}

export function useCustomAiConfig(): Promise<void> {
  return post('/systemConfig/ai/useCustom')
}

export function testAiConnection(data: AiConfigUpdateBO): Promise<AiConnectionTestResult> {
  return post('/systemConfig/ai/test', data)
}

export function externalAiGetCaptcha(data: ExternalAiCaptchaProxyParams): Promise<Record<string, unknown>> {
  return post('/systemConfig/ai/external-api/getCaptcha', data)
}

export function externalAiCheckCaptcha(data: ExternalAiCaptchaProxyParams): Promise<Record<string, unknown>> {
  return post('/systemConfig/ai/external-api/checkCaptcha', data)
}

export function externalAiSendSmsCode(data: ExternalAiSmsCodeParams): Promise<void> {
  return post('/systemConfig/ai/external-api/sms-code', data)
}

export function externalAiRegisterAndSave(
  data: ExternalAiRegisterAndSaveParams
): Promise<ExternalAiRegisterAndSaveResult> {
  return post('/systemConfig/ai/external-api/register-and-save', data)
}

export function externalAiCompleteMobile(
  data: ExternalAiCompleteMobileParams
): Promise<ExternalAiRegisterAndSaveResult> {
  return post('/systemConfig/ai/external-api/complete-mobile', data)
}

export function getExternalAiPurchaseOptions(): Promise<ExternalAiPurchaseOptions> {
  return get('/systemConfig/ai/external-api/purchase/options')
}

export function createExternalAiPurchaseOrder(
  data: ExternalAiPurchaseCreateParams
): Promise<ExternalAiPurchaseOrder> {
  return post('/systemConfig/ai/external-api/purchase/orders', data)
}

export function getExternalAiPurchaseOrder(orderNo: string): Promise<ExternalAiPurchaseOrder> {
  return get(`/systemConfig/ai/external-api/purchase/orders/${orderNo}`)
}

export function listExternalAiPurchaseOrders(limit = 10): Promise<ExternalAiPurchaseOrder[]> {
  return get('/systemConfig/ai/external-api/purchase/orders', { params: { limit } })
}

export function getExternalAiUsage(limit = 20): Promise<ExternalAiUsage> {
  return get('/systemConfig/ai/external-api/usage', { params: { limit } })
}

export function getConfigsByType(type: string): Promise<Record<string, string>> {
  return get(`/systemConfig/byType/${type}`)
}

export function clearConfigCache(): Promise<void> {
  return post('/systemConfig/clearCache')
}

export function getMinioConsoleUrl(): Promise<MinioConsoleConfig> {
  return get('/systemConfig/minio/consoleUrl')
}

export function getMinioSsoUrl(): Promise<{ enabled: boolean; ssoUrl: string | null }> {
  return get('/systemConfig/minio/ssoUrl')
}

export function getEnterpriseConfig(): Promise<EnterpriseConfig> {
  return get('/systemConfig/enterprise')
}

export function updateEnterpriseConfig(data: EnterpriseConfigUpdateBO): Promise<void> {
  return post('/systemConfig/enterprise/update', data)
}
