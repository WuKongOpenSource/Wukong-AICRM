import type { CustomerListVO, CustomerStage } from '@/types/customer'

/** 客户列表阶段看板列（与 CustomerDetailView 阶段枚举一致） */
export const KANBAN_STAGE_COLUMNS: {
  id: CustomerStage
  label: string
  headerClass: string
  dotClass: string
}[] = [
  { id: 'lead', label: '线索', headerClass: 'bg-slate-50 border-slate-200', dotClass: 'bg-slate-400' },
  { id: 'qualified', label: '资格审查', headerClass: 'bg-blue-50 border-blue-100', dotClass: 'bg-blue-500' },
  { id: 'proposal', label: '方案报价', headerClass: 'bg-indigo-50 border-indigo-100', dotClass: 'bg-indigo-500' },
  { id: 'negotiation', label: '谈判中', headerClass: 'bg-amber-50 border-amber-100', dotClass: 'bg-amber-500' },
  { id: 'closed', label: '已成交', headerClass: 'bg-emerald-50 border-emerald-100', dotClass: 'bg-emerald-500' },
  { id: 'lost', label: '已流失', headerClass: 'bg-red-50 border-red-100', dotClass: 'bg-red-500' }
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
