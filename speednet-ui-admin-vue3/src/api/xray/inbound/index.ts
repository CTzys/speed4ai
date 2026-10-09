import request from '@/config/axios'

export interface InboundVO {
  id?: number
  remark: string
  enable: boolean
  listen: string
  port: number
  protocol: string
  total: number
  expiryTime: number
  settings: string | Record<string, unknown>
  streamSettings: string | Record<string, unknown>
  sniffing: string | Record<string, unknown>
  up?: number
  down?: number
  tag?: string
}
export const getInbounds = (serverId: number): Promise<InboundVO[]> =>
  request.get({ url: '/xray/inbound/list', params: { serverId } })
export const getInbound = (serverId: number, id: number): Promise<InboundVO> =>
  request.get({ url: '/xray/inbound/get', params: { serverId, id } })
export const createInbound = (data: { serverId: number; config: InboundVO }) =>
  request.post({ url: '/xray/inbound/create', data })
export const updateInbound = (data: { serverId: number; id: number; config: InboundVO }) =>
  request.put({ url: '/xray/inbound/update', data })
export const deleteInbound = (serverId: number, id: number) =>
  request.delete({ url: '/xray/inbound/delete', params: { serverId, id } })
