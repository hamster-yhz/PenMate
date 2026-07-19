import type { AppError } from '@/api/types'

type ErrorContext = {
  source: 'vue' | 'window' | 'promise'
  component?: string
}

const normalizeError = (value: unknown) => {
  const error: AppError = value instanceof Error ? (value as AppError) : new Error(String(value))
  return {
    name: error.name,
    message: error.message,
    stack: error.stack,
    traceId: error.traceId,
    route: window.location.pathname,
    release: String(import.meta.env.VITE_APP_RELEASE || 'development'),
  }
}

export const reportFrontendError = (error: unknown, context: ErrorContext) => {
  const event = { ...normalizeError(error), ...context, timestamp: new Date().toISOString() }
  console.error('[PenMate frontend error]', event)

  const endpoint = String(import.meta.env.VITE_TELEMETRY_ENDPOINT || '').trim()
  if (endpoint && navigator.sendBeacon) {
    navigator.sendBeacon(endpoint, new Blob([JSON.stringify(event)], { type: 'application/json' }))
  }
}
