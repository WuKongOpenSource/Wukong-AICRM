import type { RelationType } from '@/types/relation'

export const relationTypeOptions: Array<{ value: RelationType; label: string }> = [
  { value: 'friend', label: '朋友' },
  { value: 'family', label: '家人' },
  { value: 'relative', label: '亲戚' },
  { value: 'partner', label: '合作伙伴' },
  { value: 'customer_contact', label: '客户联系人' },
  { value: 'supplier', label: '供应商' },
  { value: 'investor', label: '投资人' },
  { value: 'other', label: '其他' }
]
