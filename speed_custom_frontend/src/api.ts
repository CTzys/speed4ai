import axios, { AxiosError } from "axios";

export interface Member {
  id: number;
  email: string;
  nickname: string;
  avatar: string | null;
}
export interface Session {
  accessToken: string;
  refreshToken: string;
  expiresAt: string;
  member: Member;
}
interface ApiResponse<T> {
  code: number;
  data: T;
  msg: string;
}

const http = axios.create({ baseURL: "/custom-api", timeout: 10000 });
http.interceptors.request.use((config) => {
  const token = localStorage.getItem("custom_access_token");
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

let refreshing: Promise<Session> | null = null;
http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiResponse<unknown>>) => {
    const original = error.config;
    const isAuthPath = original?.url?.startsWith("/auth/");
    if (
      error.response?.status === 401 &&
      original &&
      !isAuthPath &&
      !original.headers["X-Retried"]
    ) {
      const refreshToken = localStorage.getItem("custom_refresh_token");
      if (refreshToken) {
        try {
          refreshing ??= axios
            .post<ApiResponse<Session>>("/custom-api/auth/refresh", {
              refreshToken,
            })
            .then(({ data }) => data.data)
            .finally(() => {
              refreshing = null;
            });
          const session = await refreshing;
          saveSession(session);
          original.headers["X-Retried"] = "1";
          original.headers.Authorization = `Bearer ${session.accessToken}`;
          return http(original);
        } catch {
          clearSession();
          window.location.assign("/login");
        }
      }
    }
    return Promise.reject(
      new Error(error.response?.data?.msg || error.message || "请求失败"),
    );
  },
);

export function saveSession(session: Session) {
  localStorage.setItem("custom_access_token", session.accessToken);
  localStorage.setItem("custom_refresh_token", session.refreshToken);
}
export function clearSession() {
  localStorage.removeItem("custom_access_token");
  localStorage.removeItem("custom_refresh_token");
}
export const hasSession = () =>
  Boolean(
    localStorage.getItem("custom_access_token") ||
    localStorage.getItem("custom_refresh_token"),
  );

async function data<T>(request: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  const response = (await request).data;
  if (response.code !== 0) throw new Error(response.msg || "请求失败");
  return response.data;
}
export const api = {
  sendCode: (email: string) =>
    data(http.post<ApiResponse<string | null>>("/auth/email-code", { email })),
  register: (email: string, code: string, password: string) =>
    data(
      http.post<ApiResponse<Session>>("/auth/register", {
        email,
        code,
        password,
      }),
    ),
  login: (email: string, password: string) =>
    data(http.post<ApiResponse<Session>>("/auth/login", { email, password })),
  me: () => data(http.get<ApiResponse<Member>>("/member/me")),
  logout: () => data(http.post<ApiResponse<boolean>>("/auth/logout")),
};

export interface PackagePrice {
  id: number;
  name: string;
  kind: "period" | "traffic" | "reset";
  months: number;
  price: number;
  bytes: number;
}
export interface PackagePlan {
  id: number;
  name: string;
  description: string;
  level: number;
  total_bytes: number;
  reset_mode: string;
  node_limit: number;
  enabled: boolean;
  renew_enabled: boolean;
  prices: PackagePrice[];
}
export interface CurrentPackage {
  id: number;
  planId: number | null;
  planName: string;
  planLevel: number | null;
  expiryTime: string;
  status: number;
  syncStatus: number;
  lastError: string;
  usedBytes: number;
  totalBytes: number;
  baseBytes: number;
  extraBytes: number;
  nextResetTime: string | null;
  nodeLimit: number;
}
export interface PackageOrder {
  id: number;
  number: string;
  plan_name: string;
  kind: string;
  original_amount: number;
  credit_amount: number;
  amount: number;
  status: string;
  error: string;
  expires_at: string;
  create_time: string;
  service_end: string | null;
}
export interface NodeRegion {
  id: number;
  name: string;
  cities: { id: number; name: string }[];
}
export const packages = {
  regions: () => data(http.get<ApiResponse<NodeRegion[]>>("/packages/regions")),
  plans: () => data(http.get<ApiResponse<PackagePlan[]>>("/packages/plans")),
  current: () =>
    data(http.get<ApiResponse<CurrentPackage | null>>("/packages/current")),
  orders: () => data(http.get<ApiResponse<PackageOrder[]>>("/packages/orders")),
  channels: () => data(http.get<ApiResponse<string[]>>("/packages/channels")),
  checkout: (priceId: number, requestKey: string) =>
    data(
      http.post<ApiResponse<PackageOrder>>("/packages/checkout", {
        priceId,
        requestKey,
      }),
    ),
  refresh: (orderId: number) =>
    data(
      http.post<ApiResponse<PackageOrder>>("/packages/refresh", { orderId }),
    ),
  cancel: (orderId: number) =>
    data(http.post<ApiResponse<boolean>>("/packages/cancel", { orderId })),
  pay: (orderId: number, channelCode: string) =>
    data(
      http.post<
        ApiResponse<{
          status: number;
          displayMode: string;
          displayContent: string;
        }>
      >("/packages/pay", { orderId, channelCode }),
    ),
  link: () => data(http.get<ApiResponse<{ path: string }>>("/packages/link")),
};

export interface TicketAttachment {
  id: number;
  name: string;
  content_type: string;
  size: number;
}
export interface Ticket {
  id: number;
  number: string;
  title: string;
  category: string;
  status: string;
  version: number;
  create_time: string;
  update_time: string;
  closed_at: string | null;
  close_reason: string;
  unread_count?: number;
  order_id: number | null;
  subscription_id: number | null;
}
export interface TicketMessage {
  id: number;
  seq: number;
  sender_type: string;
  body: string;
  visibility: string;
  create_time: string;
  attachments: TicketAttachment[];
}
export interface TicketDetail {
  ticket: Ticket;
  messages: TicketMessage[];
  latestSeq: number;
  nextBeforeSeq: number;
  snapshot: {
    order: { number: string; plan_name: string; status: string } | null;
    subscription: { number: string; status: number } | null;
  };
}
export const ticketStatuses: Record<string, string> = {
  pending: "待处理",
  processing: "处理中",
  waiting: "等待客户",
  resolved: "已解决",
  closed: "已关闭",
};
export const ticketCategories: Record<string, string> = {
  connection: "连接异常",
  subscription: "订阅与流量",
  payment: "订单与支付",
  account: "账户问题",
  other: "建议与其他",
};
export const tickets = {
  unread: () => data(http.get<ApiResponse<number>>("/tickets/unread")),
  page: (params: Record<string, unknown>) =>
    data(
      http.get<ApiResponse<{ list: Ticket[]; total: number }>>("/tickets", {
        params,
      }),
    ),
  detail: (id: number, beforeSeq = 0) =>
    data(
      http.get<ApiResponse<TicketDetail>>(`/tickets/${id}`, {
        params: { beforeSeq },
      }),
    ),
  create: (body: Record<string, unknown>) =>
    data(http.post<ApiResponse<number>>("/tickets", body)),
  reply: (id: number, body: Record<string, unknown>) =>
    data(http.post<ApiResponse<boolean>>(`/tickets/${id}/reply`, body)),
  action: (id: number, body: Record<string, unknown>) =>
    data(http.post<ApiResponse<boolean>>(`/tickets/${id}/action`, body)),
  read: (id: number, lastSeq: number) =>
    data(http.post<ApiResponse<boolean>>(`/tickets/${id}/read`, { lastSeq })),
  upload: (file: File) => {
    const body = new FormData();
    body.append("file", file);
    return data(
      http.post<ApiResponse<TicketAttachment>>("/tickets/attachments", body),
    );
  },
  removeAttachment: (id: number) =>
    data(http.delete<ApiResponse<boolean>>(`/tickets/attachments/${id}`)),
  image: async (id: number) => {
    const response = await http.get<Blob>(`/tickets/attachments/${id}`, {
      responseType: "blob",
    });
    if (!response.data.type.startsWith("image/"))
      throw new Error("截图不存在或无权访问");
    return response.data;
  },
};
