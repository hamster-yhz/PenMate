import axios, { type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { parse, isSafeNumber, toSafeNumberOrThrow } from 'lossless-json'
import type { ApiEnvelope, ApiErrorPayload, AppError } from '@/api/types'
import { clearSession, getSession, setSession } from '@/stores/session'

interface RequestConfig extends InternalAxiosRequestConfig {
  skipAuth?: boolean
  _retry?: boolean
}

const parseJsonLosslessly = (raw: unknown) => {
  if (typeof raw !== 'string') {
    return raw
  }

  const payload = raw.trim()
  if (!payload) {
    return raw
  }

  try {
    return parse(payload, undefined, (value: string) => {
      if (isSafeNumber(value)) {
        return toSafeNumberOrThrow(value)
      }
      return value
    })
  } catch {
    return raw
  }
}

const requestRaw = axios.create({
  baseURL: import.meta.env.VITE_APP_API_BASE_URL || '/api',
  timeout: 10000,
  transformResponse: [(data) => parseJsonLosslessly(data)]
})

let refreshPromise: Promise<string> | null = null

const createTraceId = () => {
  const random = Math.random().toString(36).slice(2, 10)
  return `fe-${Date.now()}-${random}`
}

const toAppError = (payload: ApiErrorPayload, fallbackStatus?: number): AppError => {
  const body = payload?.data || {}
  const error = new Error(body.message || '请求失败') as AppError
  error.status = body.status || fallbackStatus
  error.errorCode = body.errorCode
  error.traceId = payload.meta?.traceId
  error.details = body.details
  return error
}

const refreshToken = async () => {
  const session = getSession()
  if (!session.refreshToken) {
    throw new Error('缺少 refreshToken')
  }
  const response = await requestRaw.post<ApiEnvelope<Record<string, unknown>>>(
    '/v1/auth/refresh',
    { refreshToken: session.refreshToken },
    { skipAuth: true } as AxiosRequestConfig
  )
  const tokenData = response.data?.data || {}
  const accessToken = String(tokenData.accessToken || '')
  const nextRefreshToken = String(tokenData.refreshToken || session.refreshToken)
  if (!accessToken) {
    throw new Error('刷新令牌失败：缺少 accessToken')
  }
  setSession({ accessToken, refreshToken: nextRefreshToken })
  return accessToken
}

requestRaw.interceptors.request.use(
  (config: RequestConfig) => {
    const nextConfig = config
    const session = getSession()
    nextConfig.headers = nextConfig.headers || {}
    const headers = nextConfig.headers as Record<string, string>
    headers['X-Trace-Id'] = headers['X-Trace-Id'] || createTraceId()
    if (!nextConfig.skipAuth && session.accessToken) {
      headers.Authorization = `Bearer ${session.accessToken}`
    }
    return nextConfig
  },
  (error) => Promise.reject(error)
)

requestRaw.interceptors.response.use(
  (response) => response,
  async (error) => {
    const config = (error?.config || {}) as RequestConfig
    const status = error?.response?.status
    if (status === 401 && !config._retry && !config.skipAuth) {
      config._retry = true
      try {
        if (!refreshPromise) {
          refreshPromise = refreshToken().finally(() => {
            refreshPromise = null
          })
        }
        const token = await refreshPromise
        config.headers = config.headers || {}
        ;(config.headers as Record<string, string>).Authorization = `Bearer ${token}`
        return requestRaw(config)
      } catch (refreshErr) {
        clearSession()
        return Promise.reject(refreshErr)
      }
    }
    const errorPayload = (error?.response?.data || {}) as ApiErrorPayload
    return Promise.reject(toAppError(errorPayload, status))
  }
)

const unwrap = <T>(envelope: ApiEnvelope<T>) => envelope?.data

const request = {
  get<T>(url: string, config?: AxiosRequestConfig) {
    return requestRaw.get<ApiEnvelope<T>>(url, config).then((res) => unwrap<T>(res.data))
  },
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
    return requestRaw.post<ApiEnvelope<T>>(url, data, config).then((res) => unwrap<T>(res.data))
  },
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
    return requestRaw.put<ApiEnvelope<T>>(url, data, config).then((res) => unwrap<T>(res.data))
  },
  patch<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
    return requestRaw.patch<ApiEnvelope<T>>(url, data, config).then((res) => unwrap<T>(res.data))
  },
  delete<T>(url: string, config?: AxiosRequestConfig) {
    return requestRaw.delete<ApiEnvelope<T>>(url, config).then((res) => unwrap<T>(res.data))
  }
}

export default request

