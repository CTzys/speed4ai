import request from '@/config/axios'
export interface RegionVO {
  id?: number
  name: string
  sort: number
  status: number
}
export const getList = (): Promise<RegionVO[]> => request.get({ url: '/xray/region/list' })
export const create = (data: RegionVO) => request.post({ url: '/xray/region/create', data })
export const update = (data: RegionVO) => request.put({ url: '/xray/region/update', data })
