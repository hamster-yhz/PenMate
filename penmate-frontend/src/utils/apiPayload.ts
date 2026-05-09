type AnyRecord = Record<string, unknown>

const isRecord = (value: unknown): value is AnyRecord => value != null && typeof value === 'object' && !Array.isArray(value)

export const unwrapBusinessPayload = <T = unknown>(payload: T): T => {
  let current: unknown = payload
  const visited = new Set<unknown>()

  while (isRecord(current) && 'data' in current) {
    const nested = current.data
    if (nested == null || visited.has(nested)) {
      break
    }
    visited.add(nested)
    current = nested
  }

  return current as T
}

export const pickBusinessArray = <T = AnyRecord>(payload: unknown): T[] => {
  const normalized = unwrapBusinessPayload(payload)
  if (Array.isArray(normalized)) {
    return normalized as T[]
  }
  if (isRecord(normalized) && Array.isArray(normalized.data)) {
    return normalized.data as T[]
  }
  return []
}

export const pickBusinessRecord = (payload: unknown): AnyRecord => {
  const normalized = unwrapBusinessPayload(payload)
  return isRecord(normalized) ? normalized : {}
}
