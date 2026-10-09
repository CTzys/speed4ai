import request from '@/config/axios'
export interface RuleConfiguration {
  userId: number
  custom: boolean
  rules: string[]
}
export interface RuleUser {
  id: number
  nickname: string
  email: string
}
export interface RuleOverride {
  userId: number
  nickname: string
  email: string
}
export const getRules = (userId: number) =>
  request.get<RuleConfiguration>({ url: '/subscription/clash-rule/get', params: { userId } })
export const saveRules = (userId: number, rules: string[]) =>
  request.post({ url: '/subscription/clash-rule/save', data: { userId, rules } })
export const deleteRules = (userId: number) =>
  request.delete({ url: '/subscription/clash-rule/delete', params: { userId } })
export const searchUsers = (keyword: string) =>
  request.get<RuleUser[]>({ url: '/subscription/clash-rule/users', params: { keyword } })
export const listOverrides = () =>
  request.get<RuleOverride[]>({ url: '/subscription/clash-rule/list' })
