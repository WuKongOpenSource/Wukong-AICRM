import type { Task, Knowledge } from '@/types/common'
import type { FollowUpVO } from '@/types/customer'
import type { ScheduleVO } from '@/api/schedule'

export type RelationType =
  | 'friend'
  | 'family'
  | 'relative'
  | 'partner'
  | 'customer_contact'
  | 'supplier'
  | 'investor'
  | 'other'

export type RelationSource = 'manual' | 'customer_contact' | string

export interface RelationVO {
  relationId: string
  name: string
  avatar?: string
  avatarUrl?: string
  phone?: string
  wechat?: string
  email?: string
  relationType?: RelationType | string
  relationTypeName?: string
  company?: string
  remark?: string
  source?: RelationSource
  sourceName?: string
  sourceCustomerId?: string
  sourceCustomerName?: string
  sourceContactId?: string
  createUserId?: string
  createTime?: string
  updateTime?: string
  customFields?: Record<string, unknown>
}

export interface RelationDetailVO {
  relation: RelationVO
  tasks?: Task[]
  schedules?: ScheduleVO[]
  attachments?: Knowledge[]
  histories?: FollowUpVO[]
}

export interface RelationAddBO {
  name: string
  avatar?: string
  phone?: string
  wechat?: string
  email?: string
  relationType?: RelationType | string
  company?: string
  remark?: string
  customFields?: Record<string, unknown>
}

export interface RelationUpdateBO extends RelationAddBO {
  relationId: string
}

export interface RelationQueryBO {
  keyword?: string
  relationType?: RelationType | string
  source?: RelationSource
  sourceCustomerId?: string
  sourceContactId?: string
  page?: number
  limit?: number
}
