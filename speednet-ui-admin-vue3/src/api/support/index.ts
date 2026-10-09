import request from '@/config/axios'
export const statuses: Record<string, string> = {
  pending: '待处理',
  processing: '处理中',
  waiting: '等待客户',
  resolved: '已解决',
  closed: '已关闭'
}
export const categories: Record<string, string> = {
  connection: '连接异常',
  subscription: '订阅与流量',
  payment: '订单与支付',
  account: '账户问题',
  other: '建议与其他'
}
export const priorities: Record<string, string> = {
  low: '低',
  normal: '普通',
  high: '高',
  urgent: '紧急'
}
export interface Attachment {
  id: number
  name: string
  content_type: string
  size: number
}
export interface Ticket {
  id: number
  number: string
  title: string
  category: string
  priority: string
  status: string
  member_id: number
  assignee_id: number | null
  order_id: number | null
  subscription_id: number | null
  version: number
  unread_count: number
  update_time: string
  close_reason: string
}
export interface Message {
  id: number
  seq: number
  body: string
  visibility: string
  sender_type: string
  sender_id: number
  create_time: string
  attachments: Attachment[]
}
export interface Event {
  id: number
  actor_type: string
  actor_id: number
  action: string
  detail: string
  create_time: string
}
export interface Detail {
  ticket: Ticket
  messages: Message[]
  latestSeq: number
  nextBeforeSeq: number
  member: { id: number; nickname: string; email: string }
  snapshot: Record<string, unknown>
  order: { id: number; number: string; plan_name: string; status: string; amount: number } | null
  subscription: {
    id: number
    number: string
    status: number
    sync_status: number
    expiry_time: string
    total_bytes: number
    used_upload: number
    used_download: number
    last_error: string
  } | null
}
export const support = {
  page: (params: Record<string, unknown>) =>
    request.get<{ list: Ticket[]; total: number }>({ url: '/support/tickets', params }),
  detail: (id: number, beforeSeq = 0) =>
    request.get<Detail>({ url: `/support/tickets/${id}`, params: { beforeSeq } }),
  events: (id: number, beforeId = 0) =>
    request.get<{ list: Event[]; nextBeforeId: number }>({
      url: `/support/tickets/${id}/events`,
      params: { beforeId }
    }),
  staff: () => request.get<{ id: number; nickname: string }[]>({ url: '/support/tickets/staff' }),
  reply: (id: number, data: Record<string, unknown>) =>
    request.post({ url: `/support/tickets/${id}/reply`, data }),
  action: (id: number, data: Record<string, unknown>) =>
    request.post({ url: `/support/tickets/${id}/action`, data }),
  read: (id: number, lastSeq: number) =>
    request.post({ url: `/support/tickets/${id}/read`, data: { lastSeq } }),
  upload: async (file: File): Promise<Attachment> => {
    const data = new FormData()
    data.append('file', file)
    return request.post({
      url: '/support/tickets/attachments',
      data,
      headersType: 'multipart/form-data'
    })
  },
  removeAttachment: (id: number) => request.delete({ url: `/support/tickets/attachments/${id}` }),
  image: (id: number) => request.download<Blob>({ url: `/support/tickets/attachments/${id}` })
}
