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

