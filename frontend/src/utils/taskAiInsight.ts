import type { Task } from '@/types/common'

/** 任务卡片 / 详情中展示的 AI 话术（无后端字段时的回退文案） */
export function getTaskAiInsightText(task: Task): string {
  if (task.valuePriorityReason) return task.valuePriorityReason
  if (task.description) return task.description
  if (task.priority === 'HIGH') return '此任务优先级较高，建议尽快处理以推进业务进展。'
  if (task.priority === 'MEDIUM') return '常规跟进任务，按计划执行即可。'
  return '低优先级任务，可在空闲时间处理。'
}
