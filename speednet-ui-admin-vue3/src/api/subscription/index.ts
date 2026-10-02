import request from '@/config/axios'
import dayjs from 'dayjs'

export interface SubscriptionVO {
  id: number
  number: string
  userId: number
  userName: string
  userEmail?: string
  orderNo?: string
  source: string
  startTime: string | number
  expiryTime: string | number
  endedTime?: string | number
  status: number
  syncStatus: number
  lastError: string
  unlimited: boolean
  totalBytes: number
  usedBytes: number
  usedUpload: number
  usedDownload: number
  remainingBytes?: number
  lifetimeUpload: number
  lifetimeDownload: number
  trafficMode: string
  resetMode: string
  resetIntervalDays: number
  nextResetTime?: string | number
  nodeLimit: number
  clientCount: number
  regionId?: number
  cityId?: number
  remark?: string
  lastTrafficTime?: string | number
  lastSyncTime?: string | number
}
export interface Assignment {
  nodeId?: number
  serverId?: number
  inboundId?: number
  publicHost: string
}
export interface CreateReq {
  userId?: number
  orderNo?: string
  startTime: string | number
  expiryTime: string | number
  unlimited: boolean
  totalBytes: number
  trafficMode: string
  resetMode: string
  resetIntervalDays: number
  nodeLimit: number
  regionId?: number
  cityId?: number
  remark?: string
  assignments: Assignment[]
}
export interface ClientVO {
  id: number
  nodeId: number
  nodeName: string
  serverId: number
  inboundId: number
  protocol: string
  email: string
  publicHost: string
  released: boolean
  syncStatus: number
  lastError: string
  usedUpload: number
  usedDownload: number
  assignedTime: string | number
  lastTrafficTime?: string | number
  releasedTime?: string | number
}
export interface LogVO {
  id: number
  action: string
  message: string
  creator: string
  createTime: string | number
  success: number
  uploadBytes: number
  downloadBytes: number
}
export interface Detail {
  subscription: SubscriptionVO
  clients: ClientVO[]
  orders: { id: number; orderNo: string; purpose: string; createTime: string | number }[]
}
export interface Options {
  nodes: { id: number; name: string; regionId?: number; cityId?: number; healthStatus: number }[]
  servers: { id: number; name: string; host: string }[]
  regions: { id: number; name: string; status: number }[]
  cities: { id: number; name: string; regionId: number; status: number }[]
}
export interface ActionReq {
  id: number
  action: string
  days?: number
  expiryTime?: string | number
  bytes?: number
  remark?: string
  orderNo?: string
}
export const getPage = (params): Promise<{ list: SubscriptionVO[]; total: number }> =>
  request.get({ url: '/subscription/page', params })
export const getDetail = (id: number): Promise<Detail> =>
  request.get({ url: '/subscription/detail', params: { id } })
export const getLogPage = (params: {
  subscriptionId: number
  pageNo: number
  pageSize: number
}): Promise<{ list: LogVO[]; total: number }> =>
  request.get({ url: '/subscription/log-page', params })
export const create = (data: CreateReq): Promise<number> =>
  request.post({
    url: '/subscription/create',
    data: {
      ...data,
      startTime: dayjs(data.startTime).valueOf(),
      expiryTime: dayjs(data.expiryTime).valueOf()
    }
  })
export const action = (data: ActionReq) =>
  request.post({
    url: '/subscription/action',
    data: { ...data, expiryTime: data.expiryTime ? dayjs(data.expiryTime).valueOf() : undefined }
  })
export const sync = (id: number): Promise<boolean> =>
  request.post({ url: '/subscription/sync', params: { id } })
export const link = (id: number): Promise<{ path: string }> =>
  request.get({ url: '/subscription/link', params: { id } })
export const getOptions = (): Promise<Options> => request.get({ url: '/subscription/options' })
export const getUsers = (
  keyword: string
): Promise<{ id: number; nickname: string; email?: string }[]> =>
  request.get({ url: '/subscription/users', params: { keyword } })
export const getInbounds = (
  serverId: number
): Promise<{ id: number; remark: string; protocol: string; port: number }[]> =>
  request.get({ url: '/subscription/inbounds', params: { serverId } })
export const assign = (id: number, assignment: Assignment) =>
  request.post({ url: '/subscription/assign', data: { id, assignment } })
export const release = (id: number, clientId: number) =>
  request.post({ url: '/subscription/release', params: { id, clientId } })
export const statuses = ['待生效', '生效中', '暂停', '流量耗尽', '已到期', '已结束']
export const syncStatuses = ['待同步', '同步中', '已核对', '失败']
export const bytes = (value?: number) => {
  if (value === undefined || value === null) return '—'
  if (value < 1024) return `${value} B`
  const units = ['KiB', 'MiB', 'GiB', 'TiB']
  let n = value / 1024,
    index = 0
  while (n >= 1024 && index < units.length - 1) {
    n /= 1024
    index++
  }
  return `${n.toFixed(2)} ${units[index]}`
}
export const time = (value?: string | number) =>
  value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '—'
export const GiB = 1024 ** 3
export const credentials = (
  id: number,
  clientId: number
): Promise<{ protocol: string; credential: string; connectionUri: string }> =>
  request.get({ url: '/subscription/client-credentials', params: { id, clientId } })
export const resetClient = (id: number, clientId: number) =>
  request.post({ url: '/subscription/reset-client', params: { id, clientId } })
