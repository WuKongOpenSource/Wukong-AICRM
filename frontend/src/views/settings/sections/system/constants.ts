import type { AiProviderPreset } from '@/types/systemConfig'
import type { FieldType } from '@/types/customField'

export const AI_PROVIDER_PRESETS: AiProviderPreset[] = [
  {
    label: 'OpenAI',
    value: 'openai',
    baseUrl: 'https://api.openai.com/v1',
    models: ['gpt-4o', 'gpt-4o-mini', 'gpt-4-turbo', 'gpt-4', 'gpt-3.5-turbo']
  },
  {
    label: '阿里云 DashScope (通义千问)',
    value: 'dashscope',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/',
    models: ['qwen-max', 'qwen-plus', 'qwen-turbo', 'qwen-long']
  },
  {
    label: '自定义 OpenAI 兼容服务',
    value: 'custom',
    baseUrl: '',
    models: []
  }
]

export const SYSTEM_FIELD_TYPE_LABELS: Record<FieldType, string> = {
  text: '单行文本',
  textarea: '多行文本',
  number: '数字',
  date: '日期',
  datetime: '日期时间',
  select: '单选下拉',
  multiselect: '多选下拉',
  checkbox: '开关'
}
