import type { Task, Knowledge } from '@/types/common'
import type { ScheduleVO } from '@/api/schedule'

export type EmployeeStatus = 'active' | 'resigned' | 'disabled'

export interface AddressBookEmployee {
  userId: string
  realname?: string
  img?: string
  imgUrl?: string
  deptId?: string
  deptName?: string
  post?: string
  mobile?: string
  email?: string
  employeeStatus?: EmployeeStatus | string
  employeeStatusName?: string
  recentTaskTime?: string
}

export interface AddressBookRecentRecord {
  type: 'task' | 'schedule' | 'attachment' | string
  title?: string
  description?: string
  recordTime?: string
}

export interface AddressBookDetail extends AddressBookEmployee {
  parentId?: string
  parentName?: string
  relatedTasks?: Task[]
  relatedSchedules?: ScheduleVO[]
  relatedAttachments?: Knowledge[]
  recentRecords?: AddressBookRecentRecord[]
}

export interface AddressBookQuery {
  keyword?: string
  deptId?: string | number
  employeeStatus?: EmployeeStatus | string
  page?: number
  limit?: number
}
