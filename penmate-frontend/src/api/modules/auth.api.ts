import request from '@/utils/request'

export interface LoginPayload {
  email: string
  password: string
}

export interface AuthTokenData {
  accessToken?: string
  [key: string]: unknown
}

export interface UserProfile {
  id?: string
  userId?: string
  uid?: string
  email?: string
  userEmail?: string
  displayName?: string
  username?: string
  name?: string
  bio?: string
  roles?: Array<Record<string, unknown>>
  permissions?: Array<Record<string, unknown>>
}

export interface ProfileUpdatePayload {
  displayName: string
  email: string
  bio: string
}

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
    return request.post<AuthTokenData>('/v1/auth/login', payload, { skipAuth: true })
  },
  refresh() {
    return request.post<AuthTokenData>('/v1/auth/refresh', undefined, { skipAuth: true })
  },
  logout() {
    return request.post<string>('/v1/auth/logout')
  },
  me() {
    return request.get<UserProfile>('/v1/auth/me')
  },
  updateProfile(payload: ProfileUpdatePayload) {
    return request.patch<UserProfile>('/v1/auth/me', payload)
  },
  changePassword(payload: { currentPassword: string; newPassword: string }) {
    return request.post<string>('/v1/auth/password', payload)
  },
}
