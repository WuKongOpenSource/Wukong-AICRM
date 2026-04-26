import type { CustomerListVO, CustomerStage } from '@/types/customer'

/** 看板列投放区视觉：与阶段视图模板一致，已成交/已流失使用绿/红强调 */
export type KanbanDropTone = 'neutral' | 'success' | 'danger'

/** 客户列表阶段看板列（与 CustomerDetailView 阶段枚举一致） */
export const KANBAN_STAGE_COLUMNS: {
  id: CustomerStage
  label: string
  laneClass: string
  titleClass: string
  countBadgeClass: string
  dropTone: KanbanDropTone
}[] = [
  {
    id: 'lead',
    label: '线索',
    laneClass: 'bg-[#EBECF0]/50',
    titleClass: 'text-slate-500',
    countBadgeClass: 'bg-slate-200 text-slate-700',
    dropTone: 'neutral'
  },
  {
    id: 'qualified',
    label: '资格审查',
    laneClass: 'bg-[#E2E8F0]/40',
    titleClass: 'text-[#137FEC]',
    countBadgeClass: 'bg-blue-100 text-[#137FEC]',
    dropTone: 'neutral'
  },
  {
    id: 'proposal',
    label: '方案报价',
    laneClass: 'bg-[#E9EDFF]/40',
    titleClass: 'text-primary',
    countBadgeClass: 'bg-primary text-white',
    dropTone: 'neutral'
  },
  {
    id: 'negotiation',
    label: '谈判中',
    laneClass: 'bg-indigo-50/50',
    titleClass: 'text-slate-600',
    countBadgeClass: 'bg-slate-200 text-slate-700',
    dropTone: 'neutral'
  },
  {
    id: 'closed',
    label: '已成交',
    laneClass: 'bg-green-50/50',
    titleClass: 'text-green-600',
    countBadgeClass: 'bg-green-100 text-green-700',
    dropTone: 'success'
  },
  {
    id: 'lost',
    label: '已流失',
    laneClass: 'bg-red-50/50',
    titleClass: 'text-red-600',
    countBadgeClass: 'bg-red-100 text-red-700',
    dropTone: 'danger'
  }
]

export function normalizeListStage(stage: string | undefined | null): CustomerStage {
  const allowed: CustomerStage[] = ['lead', 'qualified', 'proposal', 'negotiation', 'closed', 'lost']
  const s = (stage || 'lead') as CustomerStage
  return allowed.includes(s) ? s : 'lead'
}

export function formatCardQuotation(q: number | undefined | null): string {
  if (q === null || q === undefined) return '-'
  const n = Number(q)
  if (!Number.isFinite(n)) return '-'
  return `¥${n.toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`
}

export function formatLastContactDate(v: string | undefined | null): string {
  if (!v) return '未跟进'
  return new Date(v).toLocaleDateString('zh-CN')
}

export function lastFollowUpHighlightClass(v: string | undefined | null): string {
  if (!v) return 'text-slate-400'
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return 'text-slate-600'
  const stale = d.getTime() < Date.now() - 30 * 24 * 60 * 60 * 1000
  return stale ? 'rounded bg-red-50 px-1.5 py-0.5 font-medium text-red-600' : 'text-slate-600'
}

export function customersInStage(customers: CustomerListVO[], stage: CustomerStage): CustomerListVO[] {
  return customers.filter(r => normalizeListStage(r.stage) === stage)
}
