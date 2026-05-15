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

export interface WorkbenchRuntimeToolCall {
  toolCallId?: string | null
  toolCode?: string | null
  toolName?: string | null
  status?: string | null
  iteration?: number | null
  argumentsPreview?: string | null
  output?: string | null
  errorMessage?: string | null
}

export interface WorkbenchRuntimeApproval {
  approvalId?: string | null
  approvalType?: string | null
  toolCallId?: string | null
  nextAction?: string | null
  [key: string]: unknown
}

export interface WorkbenchRuntimeEventSource {
  eventName?: string | null
  sessionId?: string | null
  turnId?: string | null
  taskId?: string | null
  phase?: string | null
  message?: string | null
  errorMsg?: string | null
  recoverable?: boolean
  nextAction?: string | null
  status?: string | null
  toolCall?: WorkbenchRuntimeToolCall | null
  approval?: WorkbenchRuntimeApproval | null
}

export interface WorkbenchActiveTaskRuntimeSnapshot {
  lastRuntimeStatus?: string | null
  recoveryCursor?: string | null
  activeToolCallsSnapshot?: WorkbenchRuntimeToolCall[] | null
}

export interface WorkbenchTodoSummarySnapshot {
  planTitle?: string | null
  items?: Array<Record<string, unknown>> | null
  nextAction?: string | null
}

export interface WorkbenchStoryBibleProposalSummarySnapshot {
  proposalSummary?: string | null
  entryKeys?: string[] | null
  nextAction?: string | null
}

export interface WorkbenchResultSummarySnapshot {
  draftSummary?: Record<string, unknown> | null
  qualityReportSummary?: Record<string, unknown> | null
  todoSummary?: WorkbenchTodoSummarySnapshot | null
  storyBibleProposalSummary?: WorkbenchStoryBibleProposalSummarySnapshot | null
}

export interface WorkbenchRecoveryContextSnapshot {
  chapterId?: string | null
  selectedText?: string | null
  activePlugins?: string[] | null
  modelConfigId?: string | null
  ragRefs?: string[] | null
  outlineSnapshot?: Record<string, unknown> | null
  activeTaskRuntime?: WorkbenchActiveTaskRuntimeSnapshot | null
  resultSummary?: WorkbenchResultSummarySnapshot | null
}

export interface WorkbenchRecoverySnapshot {
  session?: {
    sessionId?: string | null
    title?: string | null
    status?: string | null
    boundStyle?: {
      styleId?: string | null
      name?: string | null
    } | null
  } | null
  activeTask?: {
    turnId?: string | null
    taskId?: string | null
    taskStatus?: string | null
    requestContextId?: string | null
    streamChannelKey?: string | null
  } | null
  pendingApproval?: WorkbenchRuntimeApproval | Record<string, unknown> | null
  messages?: Array<Record<string, unknown>> | null
  workbenchContext?: WorkbenchRecoveryContextSnapshot | null
}
