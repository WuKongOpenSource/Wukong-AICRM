export type SettingsMainTab = 'team' | 'role' | 'system'

export type SystemSettingsTab =
  | 'profile'
  | 'enterprise'
  | 'api'
  | 'agent'
  | 'storage'
  | 'customField'

export interface SettingsTabItem<T extends string> {
  value: T
  label: string
}
