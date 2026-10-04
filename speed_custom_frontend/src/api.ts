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
export interface NodeRegion { id: number; name: string; cities: { id: number; name: string }[]; }
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
