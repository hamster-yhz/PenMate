interface BrowserMutexRecord {
  owner: string
  expiresAt: number
}

export interface BrowserMutexOptions {
  ttlMs?: number
  retryMinMs?: number
  retryMaxMs?: number
  now?: () => number
  random?: () => number
}

const parseRecord = (value: string | null): BrowserMutexRecord | null => {
  if (!value) return null
  try {
    const parsed = JSON.parse(value) as Partial<BrowserMutexRecord>
    if (typeof parsed.owner !== 'string' || typeof parsed.expiresAt !== 'number') return null
    return { owner: parsed.owner, expiresAt: parsed.expiresAt }
  } catch {
    return null
  }
}

const delay = (milliseconds: number) => new Promise<void>((resolve) => window.setTimeout(resolve, milliseconds))

const randomOwner = () => {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

export const withLocalStorageMutex = async <T>(
  key: string,
  task: () => Promise<T>,
  options: BrowserMutexOptions = {},
): Promise<T> => {
  if (typeof window === 'undefined' || !window.localStorage) return task()

  const ttlMs = options.ttlMs ?? 15_000
  const retryMinMs = options.retryMinMs ?? 24
  const retryMaxMs = Math.max(retryMinMs, options.retryMaxMs ?? 64)
  const now = options.now ?? Date.now
  const random = options.random ?? Math.random
  const owner = randomOwner()

  while (true) {
    const existing = parseRecord(window.localStorage.getItem(key))
    if (!existing || existing.expiresAt <= now()) {
      const candidate: BrowserMutexRecord = { owner, expiresAt: now() + ttlMs }
      window.localStorage.setItem(key, JSON.stringify(candidate))

      // localStorage has no compare-and-set. A short confirmation window lets all
      // contenders observe the final writer before one of them enters the task.
      await delay(retryMinMs)
      if (parseRecord(window.localStorage.getItem(key))?.owner === owner) break
    }

    const jitter = retryMinMs + Math.floor(random() * (retryMaxMs - retryMinMs + 1))
    await delay(jitter)
  }

  try {
    return await task()
  } finally {
    if (parseRecord(window.localStorage.getItem(key))?.owner === owner) {
      window.localStorage.removeItem(key)
    }
  }
}
