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
  argumentsPreview?: unknown
  output?: unknown
  errorMessage?: string | null
}

export interface WorkbenchRuntimeApproval {
  approvalId?: string | null
  approvalType?: string | null
  toolCallId?: string | null
  nextAction?: string | null
  [key: string]: unknown
}

export interface WorkbenchRuntimeStoryBibleApproval {
  approvalId?: string | null
  approvalType?: string | null
  proposalSummary?: string | null
  entryKeys?: string[] | null
  nextAction?: string | null
}

export interface WorkbenchRuntimeEventSource {
  eventName?: string | null
  sessionId?: string | null
  turnId?: string | null
  runId?: string | null
  sequence?: string | null
  phase?: string | null
  message?: string | null
  errorMsg?: string | null
  recoverable?: boolean
  nextAction?: string | null
  status?: string | null
  toolCall?: WorkbenchRuntimeToolCall | null
  approval?: WorkbenchRuntimeApproval | null
  todoPlan?: WorkbenchTodoSummarySnapshot | Record<string, unknown> | null
  storyBibleApproval?: WorkbenchRuntimeStoryBibleApproval | Record<string, unknown> | null
  payload?: Record<string, unknown>
}

export interface WorkbenchActiveRunRuntimeSnapshot {
  lastRuntimeStatus?: string | null
  latestSequence?: string | null
  activeToolCallsSnapshot?: WorkbenchRuntimeToolCall[] | null
}

export interface WorkbenchTaskProfileSnapshot {
  intentTags?: string[] | null
  executionProfile?: string | null
  tools?: string[] | null
  hardConstraints?: string[] | null
  outputExpectation?: string | null
  needsApproval?: boolean
  includeStoryBible?: boolean
  includeRag?: boolean
  reasoningSummary?: string | null
}

export interface WorkbenchPromptPlanModuleSnapshot {
  moduleKey?: string | null
  source?: string | null
  enabled?: boolean
  notes?: string | null
}

export interface WorkbenchPromptPlanSnapshot {
  modules?: WorkbenchPromptPlanModuleSnapshot[] | null
  finalProfile?: string | null
  assembledPromptPreview?: string | null
}

export interface WorkbenchContextPackageSnapshot {
  sources?: string[] | null
  missingContextFlags?: string[] | null
  conflicts?: string[] | null
  storyBibleEntries?: string[] | null
  styleSnapshot?: string | null
  chapterScope?: string | null
}

export interface WorkbenchTodoSummarySnapshot {
  planTitle?: string | null
  items?: Array<Record<string, unknown>> | null
  nextAction?: string | null
  recommendedNextAction?: string | null
}

export interface WorkbenchStoryBibleProposalSummarySnapshot {
  proposalSummary?: string | null
  items?: Array<Record<string, unknown>> | null
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
  outlineSnapshot?: Record<string, unknown> | null
  taskProfile?: WorkbenchTaskProfileSnapshot | null
  promptPlan?: WorkbenchPromptPlanSnapshot | null
  contextPackage?: WorkbenchContextPackageSnapshot | null
  activeRunRuntime?: WorkbenchActiveRunRuntimeSnapshot | null
  resultSummary?: WorkbenchResultSummarySnapshot | null
}

export interface WorkbenchRecoverySnapshot {
  session?: {
    sessionId?: string | null
    title?: string | null
    status?: string | null
    activeSkills?: string[] | null
    boundStyle?: {
      styleId?: string | null
      name?: string | null
    } | null
  } | null
  activeRun?: {
    turnId?: string | null
    runId?: string | null
    runStatus?: string | null
    runPhase?: string | null
    latestSequence?: string | null
  } | null
  pendingApproval?: WorkbenchRuntimeApproval | Record<string, unknown> | null
  messages?: Array<Record<string, unknown>> | null
  workbenchContext?: WorkbenchRecoveryContextSnapshot | null
}

export interface AgentSkillCatalogItem {
  name: string
  description: string
}
