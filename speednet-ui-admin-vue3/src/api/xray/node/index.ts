import request from '@/config/axios'
export interface NodeVO {
  id?: number
  name: string
  host: string
  port: number
  authType: number
  username: string
  password?: string
  passwordConfigured?: boolean
  regionId?: number
  region?: string
  cityId?: number
  city?: string
  tags: string
  remark: string
  shelfStatus?: number
  healthStatus?: number
  latencyMs?: number
  lastCheckTime?: string
  lastError?: string
  configVersion?: number
  deployedServerCount?: number
  serverCount?: number
  activeUserCount?: number
  expiredUserCount?: number
}
export interface OperationResult {
  id: number
  success: boolean
  message: string
}
export interface ImportRow {
  line: number
  name?: string
  host?: string
  port?: number
  username?: string
  status: string
  message: string
}
export interface TaskRow {
  id: number
  nodeId: number
  serverId?: number
  action: string
  status: number
  message: string
}
export const getPage = (params: any) => request.get({ url: '/xray/node/page', params })
export const getNode = (id: number): Promise<NodeVO> =>
  request.get({ url: '/xray/node/get', params: { id } })
export const getDetail = (id: number) => request.get({ url: '/xray/node/detail', params: { id } })
export const getStats = () => request.get({ url: '/xray/node/stats' })
export const create = (data: NodeVO) => request.post({ url: '/xray/node/create', data })
export const update = (data: NodeVO) => request.put({ url: '/xray/node/update', data })
export const preview = (text: string): Promise<ImportRow[]> =>
  request.post({ url: '/xray/node/import-preview', data: { text } })
export const importText = (text: string, regionId: number, cityId: number): Promise<ImportRow[]> =>
  request.post({ url: '/xray/node/import', data: { text, regionId, cityId } })
export const shelf = (ids: number[], up: boolean): Promise<OperationResult[]> =>
  request.post({ url: '/xray/node/shelf', data: { ids, up } })
export const submit = (
  ids: number[],
  action: 'check' | 'deploy' | 'verify' | 'remove',
  serverId?: number
): Promise<string> => request.post({ url: '/xray/node/' + action, data: { ids, serverId } })
export const getTask = (batchId: string): Promise<TaskRow[]> =>
  request.get({ url: '/xray/node/task', params: { batchId } })
