import request from '@/config/axios'
export interface CityVO {
  id?: number
  regionId: number
  name: string
  sort: number
  status: number
}
export const getList = (regionId?: number): Promise<CityVO[]> =>
  request.get({ url: '/xray/city/list', params: { regionId } })
export const create = (data: CityVO) => request.post({ url: '/xray/city/create', data })
export const update = (data: CityVO) => request.put({ url: '/xray/city/update', data })
