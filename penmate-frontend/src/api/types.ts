export interface ApiMeta {
  traceId?: string
  timestamp?: string
}

export interface ApiEnvelope<T> {
  data: T
  meta?: ApiMeta
}

export interface ApiErrorPayload {
  status?: number
  errorCode?: string
  message?: string
  details?: unknown
  meta?: ApiMeta
}

export interface AppError extends Error {
  status?: number
  errorCode?: string
  traceId?: string
  details?: unknown
}

export type IdLike = number | string

