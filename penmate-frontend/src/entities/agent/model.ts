export type AgentSafetyMode = 'STRICT' | 'STANDARD' | 'AUTONOMOUS' | 'FULL_AUTHORITY'

export type AgentQueuedRequest = {
  requestId: string
  type: 'MESSAGE' | 'COMPRESS'
  status: 'PENDING' | 'EXECUTING'
  payloadJson?: string | null
  attemptCount?: number
  createdAt?: string
  updatedAt?: string
}

export type AgentSessionContextUsage = {
  usedTokens: number
  maxContextTokens: number | null
  usageRatio: number | null
  promptTokens: number
  completionTokens: number
  modelName?: string | null
  usageSource?: 'PROVIDER_USAGE' | 'ESTIMATE'
  contextCapacitySource?: 'MANUAL' | 'PROVIDER' | 'CATALOG' | 'FALLBACK'
}
