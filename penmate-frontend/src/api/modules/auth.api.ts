import request from '@/utils/request'

export interface LoginPayload {
  email: string
  password: string
}

export interface RefreshPayload {
  refreshToken: string
}

export interface AuthTokenData {
  accessToken?: string
  refreshToken?: string
  [key: string]: unknown
}

export type UserProfile = Record<string, unknown>

export function parseAuthErrorMessage(error: unknown): string {
  if (typeof error === 'object' && error !== null) {
    const maybeResponse = (error as { response?: { data?: { message?: unknown } } }).response
    const maybeMessage = maybeResponse?.data?.message
    if (typeof maybeMessage === 'string') {
      const normalizedMessage = maybeMessage.trim()
      if (normalizedMessage) {
        return normalizedMessage
      }
    }
  }

  if (error instanceof Error && error.message.trim()) {
    return error.message
  }

  return 'Authentication request failed'
}

export const authApi = {
  login(payload: LoginPayload) {
    return request.post<AuthTokenData>('/v1/auth/login', payload)
  },
  refresh(payload: RefreshPayload) {
    return request.post<AuthTokenData>('/v1/auth/refresh', payload)
  },
  logout() {
    return request.post<string>('/v1/auth/logout')
  },
  me() {
    return request.get<UserProfile>('/v1/auth/me')
  }
}

