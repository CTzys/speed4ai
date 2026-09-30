/** Member sessions are separate from the admin console session. */
export interface MemberSession {
  userId: number
  accessToken: string
  refreshToken: string
  expiresTime: string
}

const MEMBER_SESSION_KEY = 'MEMBER_SESSION'

export function getMemberSession(): MemberSession | null {
  try {
    const value = sessionStorage.getItem(MEMBER_SESSION_KEY)
    return value ? JSON.parse(value) as MemberSession : null
  } catch {
    return null
  }
}

export function setMemberSession(session: MemberSession): void {
  sessionStorage.setItem(MEMBER_SESSION_KEY, JSON.stringify(session))
}

export function clearMemberSession(): void {
  sessionStorage.removeItem(MEMBER_SESSION_KEY)
}

export async function memberApi<T>(path: string, options: RequestInit = {}): Promise<T> {
  const tenantId = import.meta.env.VITE_MEMBER_TENANT_ID
  const token = getMemberSession()?.accessToken
  const response = await fetch(`${import.meta.env.VITE_BASE_URL}/app-api${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(tenantId ? { 'tenant-id': String(tenantId) } : {}),
      terminal: '30',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers
    }
  })
  const result = await response.json() as { code: number; data: T; msg?: string }
  if (!response.ok || result.code !== 0) throw new Error(result.msg || '请求失败，请稍后重试')
  return result.data
}
