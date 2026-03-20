export const wkIconNames = [
  'ai',
  'stage',
  'profile',
  'customer',
  'meetingRecord',
  'add',
  'task',
  'knowledge',
  'contract',
  'settings',
  'import',
  'export',
] as const

export type WkIconName = (typeof wkIconNames)[number]

export const wkIconClassMap = {
  ai: 'AI',
  stage: 'stage',
  profile: 'profile',
  customer: 'customer',
  meetingRecord: 'meeting-record',
  add: 'add',
  task: 'task',
  knowledge: 'knowledge',
  contract: 'contract',
  settings: 'settings',
  import: 'import',
  export: 'export',
} as const satisfies Record<WkIconName, string>
