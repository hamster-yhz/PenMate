export interface ApiMeta {
  traceId?: string
  timestamp?: string
}

export interface ApiEnvelope<T> {
  data: T
  meta?: ApiMeta
}

export interface ApiErrorData {
  status?: number
  errorCode?: string
  message?: string
  details?: unknown
  path?: string
}

export interface ApiErrorPayload {
  data?: ApiErrorData
  meta?: ApiMeta
}

export interface AppError extends Error {
  status?: number
  errorCode?: string
  traceId?: string
  details?: unknown
}
