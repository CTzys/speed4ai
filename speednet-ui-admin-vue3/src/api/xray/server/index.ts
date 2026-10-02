import request from '@/config/axios'
export interface XrayServerVO {
  id?: number
  name: string
  host: string
  sshPort: number
  sshUsername: string
  sshAuthType: number
  sshPassword?: string
  sshPrivateKey?: string
  sshKeyPassphrase?: string
  sshCredentialConfigured?: boolean
  panelScheme?: string
  panelPort?: number
  panelPath?: string
  panelUsername?: string
  panelPassword?: string
  panelToken?: string
  panelTokenConfigured?: boolean
  panelCredentialConfigured?: boolean
  panelVersion?: string
  xrayVersion?: string
  installStatus?: number
  healthStatus?: number
  lastCheckTime?: Date
  lastError?: string
  remark?: string
  createTime?: Date
}
export const getServerPage = (params: any) => request.get({ url: '/xray/server/page', params })
export const getServer = (id: number) => request.get({ url: '/xray/server/get?id=' + id })
export const createServer = (data: XrayServerVO) =>
  request.post({ url: '/xray/server/create', data })
export const updateServer = (data: XrayServerVO) =>
  request.put({ url: '/xray/server/update', data })
export const deleteServer = (id: number) => request.delete({ url: '/xray/server/delete?id=' + id })
export const testSsh = (id: number) => request.post({ url: '/xray/server/test-ssh?id=' + id })
export const checkHealth = (id: number) =>
  request.post({ url: '/xray/server/check-health?id=' + id })

export const syncPanelConfig = (id: number) =>
  request.post({ url: '/xray/server/sync-panel-config', params: { id } })

export const startService = (id: number) =>
  request.post({ url: '/xray/server/start', params: { id } })
export const stopService = (id: number) =>
  request.post({ url: '/xray/server/stop', params: { id } })
export const testPanel = (id: number) =>
  request.post({ url: '/xray/server/test-panel', params: { id } })

export const getPanelCredentials = (id: number) =>
  request.get({ url: '/xray/server/panel-credentials', params: { id } })
