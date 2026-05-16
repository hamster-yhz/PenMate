import type {
  WorkbenchRecoverySnapshot,
  WorkbenchRuntimeEventSource,
  WorkbenchRuntimeStoryBibleApproval,
  WorkbenchRuntimeToolCall,
} from '@/api/types'

export type RuntimeStatusCardViewModel = {
  title: string
  badgeText: string
  description: string
  nextActionText: string
  failureReasonText: string
}

export type ToolCallStatusCardViewModel = {
  title: string
  toolCode: string
  statusText: string
  argumentsPreview: string
  outputPreview: string
  errorMessage: string
}

export type TodoPlanCardItemViewModel = {
  title: string
  statusText: string
  priorityText: string
}

export type TodoPlanCardViewModel = {
  title: string
  itemCountText: string
  nextActionText: string
  items: TodoPlanCardItemViewModel[]
}

export type StoryBibleApprovalCardViewModel = {
  title: string
  proposalSummary: string
  entryKeys: string[]
  nextActionText: string
}

export type WorkbenchRuntimePresenterViewModel = {
  status: RuntimeStatusCardViewModel
  toolCallCard?: ToolCallStatusCardViewModel
  todoPlanCard?: TodoPlanCardViewModel
  storyBibleApprovalCard?: StoryBibleApprovalCardViewModel
}

const PHASE_LABELS: Record<string, string> = {
  planning: '正在分析请求',
  context_building: '正在规划章节',
  draft_generation: '正在生成正文',
  quality_review: '正在审查质量',
  story_bible_review: '正在整理故事圣经',
  todo_review: '正在整理待办',
  waiting_approval: '等待审批',
  done: '已完成',
  failed: '执行失败',
}

const TOOL_STATUS_LABELS: Record<string, string> = {
  pending: '待执行',
  running: '进行中',
  waiting_approval: '等待审批',
  done: '已完成',
  applied: '已应用',
  failed: '执行失败',
  cancelled: '已取消',
}

const TOOL_STATUS_PRIORITY: Record<string, number> = {
  waiting_approval: 5,
  running: 4,
  failed: 3,
  pending: 2,
  done: 1,
  applied: 1,
  cancelled: 0,
}

const normalizeText = (value: unknown) => String(value ?? '').trim()

const normalizeDisplayValue = (value: unknown) => {
  if (value == null) return ''
  if (typeof value === 'string') return value.trim()
  try {
    return JSON.stringify(value)
  } catch {
    return String(value).trim()
  }
}

const normalizePhase = (value: unknown) => normalizeText(value).toLowerCase().replace(/-/g, '_')

const phaseLabel = (value: unknown) => PHASE_LABELS[normalizePhase(value)] || normalizeText(value) || '就绪'

const toolStatusLabel = (value: unknown) => TOOL_STATUS_LABELS[normalizePhase(value)] || normalizeText(value) || '未知'

const toStringArray = (value: unknown) => Array.isArray(value)
  ? value.map((item) => normalizeText(item)).filter(Boolean)
  : []

const parseJsonRecord = (value: unknown): Record<string, unknown> | null => {
  if (!value) return null
  if (typeof value === 'object' && !Array.isArray(value)) {
    return value as Record<string, unknown>
  }
  if (typeof value !== 'string') return null
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? parsed as Record<string, unknown>
      : null
  } catch {
    return null
  }
}

const resolveTargetToolCallId = (runtime?: WorkbenchRuntimeEventSource | null, recovery?: WorkbenchRecoverySnapshot | null) => {
  const runtimeApproval = runtime?.approval as Record<string, unknown> | null | undefined
  const recoveryApproval = recovery?.pendingApproval as Record<string, unknown> | null | undefined
  const approvalToolCallId = normalizeText(runtimeApproval?.toolCallId || recoveryApproval?.toolCallId)
  if (approvalToolCallId) return approvalToolCallId

  const recoveryCursor = normalizeText(recovery?.workbenchContext?.activeTaskRuntime?.recoveryCursor)
  const toolCallMatch = /^tool_call:[^:]+:(.+)$/i.exec(recoveryCursor)
  if (toolCallMatch?.[1]) {
    return normalizeText(toolCallMatch[1])
  }
  return ''
}

const resolveToolCall = (runtime?: WorkbenchRuntimeEventSource | null, recovery?: WorkbenchRecoverySnapshot | null): WorkbenchRuntimeToolCall | null => {
  if (runtime?.toolCall) {
    return runtime.toolCall
  }

  const snapshots = recovery?.workbenchContext?.activeTaskRuntime?.activeToolCallsSnapshot
  if (!Array.isArray(snapshots) || snapshots.length === 0) {
    return null
  }

  const targetToolCallId = resolveTargetToolCallId(runtime, recovery)
  if (targetToolCallId) {
    const matched = snapshots.find((item) => normalizeText(item?.toolCallId) === targetToolCallId)
    if (matched) {
      return matched
    }
  }

  const currentPhase = normalizePhase(recovery?.workbenchContext?.activeTaskRuntime?.lastRuntimeStatus)
  const byPhase = snapshots.find((item) => {
    const toolCode = normalizePhase(item?.toolCode)
    return (currentPhase === 'story_bible_review' && toolCode === 'story_bible_update')
      || (currentPhase === 'todo_review' && toolCode === 'todo_planner')
      || (currentPhase === 'quality_review' && toolCode === 'quality_review')
      || (currentPhase === 'draft_generation' && toolCode === 'draft_generation')
    })
  if (byPhase) {
    return byPhase
  }

  return [...snapshots].sort((left, right) => {
    const rightScore = TOOL_STATUS_PRIORITY[normalizePhase(right?.status)] ?? -1
    const leftScore = TOOL_STATUS_PRIORITY[normalizePhase(left?.status)] ?? -1
    return rightScore - leftScore
  })[0] || null
}

const resolvePhaseFromToolCode = (toolCode: unknown, fallback: string) => {
  const normalizedToolCode = normalizePhase(toolCode)
  if (normalizedToolCode === 'draft_generation') return 'draft_generation'
  if (normalizedToolCode === 'quality_review') return 'quality_review'
  if (normalizedToolCode === 'todo_planner' || normalizedToolCode === 'todo_crud') return 'todo_review'
  if (normalizedToolCode === 'story_bible_update') return 'story_bible_review'
  return fallback
}

const resolvePhase = (runtime?: WorkbenchRuntimeEventSource | null, recovery?: WorkbenchRecoverySnapshot | null) => {
  const runtimePhase = normalizePhase(runtime?.phase)
  if (runtimePhase) {
    if (runtimePhase === 'executing' || runtimePhase === 'tool_call') {
      return resolvePhaseFromToolCode(runtime?.toolCall?.toolCode, runtimePhase)
    }
    return runtimePhase
  }

  const recoveryRuntimePhase = normalizePhase(recovery?.workbenchContext?.activeTaskRuntime?.lastRuntimeStatus)
  if (recoveryRuntimePhase) {
    if (recoveryRuntimePhase === 'executing' || recoveryRuntimePhase === 'tool_call') {
      return resolvePhaseFromToolCode(resolveToolCall(runtime, recovery)?.toolCode, recoveryRuntimePhase)
    }
    return recoveryRuntimePhase
  }

  const taskStatus = normalizePhase(recovery?.activeTask?.taskStatus)
  if (taskStatus === 'waiting_approval' || taskStatus === 'done' || taskStatus === 'failed') {
    return taskStatus
  }
  if (taskStatus === 'running') {
    return resolvePhaseFromToolCode(resolveToolCall(runtime, recovery)?.toolCode, taskStatus)
  }
  return taskStatus
}

const resolveTodoNextAction = (summary?: Record<string, unknown> | null) => {
  return normalizeText(summary?.nextAction)
    || normalizeText(summary?.recommendedNextAction)
}

const resolveRuntimeTodoSummary = (runtime?: WorkbenchRuntimeEventSource | null) => {
  return parseJsonRecord(runtime?.todoPlan)
}

const resolveRuntimeStoryBibleApproval = (runtime?: WorkbenchRuntimeEventSource | null) => {
  return parseJsonRecord(runtime?.storyBibleApproval) as WorkbenchRuntimeStoryBibleApproval | Record<string, unknown> | null
}

const resolveNextAction = (runtime?: WorkbenchRuntimeEventSource | null, recovery?: WorkbenchRecoverySnapshot | null) => {
  const runtimeNextAction = normalizeText(runtime?.nextAction)
  if (runtimeNextAction) {
    return runtimeNextAction
  }

  const approvalNextAction = normalizeText((runtime?.approval as Record<string, unknown> | null | undefined)?.nextAction)
    || normalizeText((recovery?.pendingApproval as Record<string, unknown> | null | undefined)?.nextAction)
  const runtimeStoryBibleSummary = resolveRuntimeStoryBibleApproval(runtime)
  const runtimeTodoSummary = resolveRuntimeTodoSummary(runtime)
  const storyBibleNextAction = normalizeText(runtimeStoryBibleSummary?.nextAction)
  const todoNextAction = resolveTodoNextAction(runtimeTodoSummary)
    || resolveTodoNextAction(recovery?.workbenchContext?.resultSummary?.todoSummary as Record<string, unknown> | null | undefined)
  const currentPhase = resolvePhase(runtime, recovery)

  if (currentPhase === 'todo_review') {
    return todoNextAction || approvalNextAction || storyBibleNextAction
  }
  if (currentPhase === 'story_bible_review' || currentPhase === 'waiting_approval') {
    return approvalNextAction || storyBibleNextAction || todoNextAction
  }
  return approvalNextAction || storyBibleNextAction || todoNextAction
}

const resolveFailureReason = (runtime?: WorkbenchRuntimeEventSource | null, recovery?: WorkbenchRecoverySnapshot | null) => {
  return normalizeText(runtime?.errorMsg)
    || normalizeText(runtime?.message)
    || normalizeText(resolveToolCall(runtime, recovery)?.errorMessage)
}

const buildToolCallCard = (toolCall: WorkbenchRuntimeToolCall | null): ToolCallStatusCardViewModel | undefined => {
  if (!toolCall) return undefined
  const title = normalizeText(toolCall.toolName) || normalizeText(toolCall.toolCode)
  const toolCode = normalizeText(toolCall.toolCode)
  if (!title && !toolCode) return undefined
  return {
    title: title || '工具调用',
    toolCode,
    statusText: toolStatusLabel(toolCall.status),
    argumentsPreview: normalizeDisplayValue(toolCall.argumentsPreview),
    outputPreview: normalizeDisplayValue(toolCall.output),
    errorMessage: normalizeText(toolCall.errorMessage),
  }
}

const mergeTodoCrudOutputIntoSummary = (
  baseSummary: Record<string, unknown> | null | undefined,
  toolOutput: Record<string, unknown> | null,
) => {
  if (!baseSummary || !toolOutput) return baseSummary
  const operation = normalizePhase(toolOutput.operation)
  if (!operation) return baseSummary

  const rawItems = Array.isArray(baseSummary.items) ? baseSummary.items : []
  const nextItems = rawItems.map((item) => ({ ...(item as Record<string, unknown>) }))
  const targetTodoId = normalizeText(toolOutput.todoId)
  const targetTitle = normalizeText(toolOutput.title)
  const targetIndex = nextItems.findIndex((item) => {
    const itemTodoId = normalizeText(item.todoId)
    const itemTitle = normalizeText(item.title)
    return (targetTodoId && itemTodoId === targetTodoId) || (!targetTodoId && !!targetTitle && itemTitle === targetTitle)
  })

  if ((operation === 'update' || operation === 'complete') && targetIndex >= 0) {
    nextItems[targetIndex] = {
      ...nextItems[targetIndex],
      ...toolOutput,
      status: normalizeText(toolOutput.todoStatus) || normalizeText(toolOutput.status) || normalizeText(nextItems[targetIndex].status),
    }
  }

  if (operation === 'delete' && targetIndex >= 0) {
    nextItems.splice(targetIndex, 1)
  }

  if (operation === 'create' && targetIndex < 0 && (targetTodoId || targetTitle)) {
    nextItems.push({
      ...toolOutput,
      status: normalizeText(toolOutput.todoStatus) || normalizeText(toolOutput.status),
    })
  }

  if (operation === 'list' && Array.isArray(toolOutput.items)) {
    return {
      ...baseSummary,
      items: toolOutput.items,
    }
  }

  return {
    ...baseSummary,
    items: nextItems,
  }
}

const resolveRecoveryTodoSummaryFromToolSnapshot = (recovery?: WorkbenchRecoverySnapshot | null) => {
  const snapshots = recovery?.workbenchContext?.activeTaskRuntime?.activeToolCallsSnapshot
  if (!Array.isArray(snapshots)) return null
  const plannerSnapshot = snapshots.find((item) => normalizePhase(item?.toolCode) === 'todo_planner')
  return parseJsonRecord(plannerSnapshot?.output)
}

const resolveTodoSummary = (runtime?: WorkbenchRuntimeEventSource | null, recovery?: WorkbenchRecoverySnapshot | null) => {
  const runtimeTodoSummary = resolveRuntimeTodoSummary(runtime)
  if (runtimeTodoSummary) {
    return runtimeTodoSummary
  }
  const runtimeToolCall = runtime?.toolCall
  if (normalizePhase(runtimeToolCall?.toolCode) === 'todo_planner') {
    const parsed = parseJsonRecord(runtimeToolCall?.output)
    if (parsed) return parsed
  }
  const recoveryTodoSummary = (recovery?.workbenchContext?.resultSummary?.todoSummary as Record<string, unknown> | null | undefined)
    || resolveRecoveryTodoSummaryFromToolSnapshot(recovery)
  if (normalizePhase(runtimeToolCall?.toolCode) === 'todo_crud') {
    return mergeTodoCrudOutputIntoSummary(recoveryTodoSummary, parseJsonRecord(runtimeToolCall?.output))
  }
  return recoveryTodoSummary
}

const buildTodoPlanCard = (runtime?: WorkbenchRuntimeEventSource | null, recovery?: WorkbenchRecoverySnapshot | null): TodoPlanCardViewModel | undefined => {
  const summary = resolveTodoSummary(runtime, recovery)
  const title = normalizeText(summary?.planTitle)
  const rawItems = Array.isArray(summary?.items) ? summary.items : []
  if (!title && rawItems.length === 0) return undefined
  const items = rawItems.map((item) => ({
    title: normalizeText((item as Record<string, unknown> | null | undefined)?.title),
    statusText: normalizeText((item as Record<string, unknown> | null | undefined)?.status),
    priorityText: normalizeText((item as Record<string, unknown> | null | undefined)?.priority),
  })).filter((item) => item.title)
  return {
    title: title || '待办计划',
    itemCountText: `${items.length} 项待办`,
    nextActionText: resolveTodoNextAction(summary),
    items,
  }
}

const resolveStoryBibleEntryKeysFromItems = (summary?: Record<string, unknown> | null) => {
  const items = Array.isArray(summary?.items) ? summary.items : []
  return items
    .map((item) => normalizeText((item as Record<string, unknown> | null | undefined)?.entryKey))
    .filter(Boolean)
}

const resolveStoryBibleSummary = (runtime?: WorkbenchRuntimeEventSource | null, recovery?: WorkbenchRecoverySnapshot | null) => {
  const recoverySummary = recovery?.workbenchContext?.resultSummary?.storyBibleProposalSummary as Record<string, unknown> | null | undefined
  const runtimeStoryBibleSummary = resolveRuntimeStoryBibleApproval(runtime)
  if (runtimeStoryBibleSummary) {
    return {
      ...(recoverySummary || {}),
      ...runtimeStoryBibleSummary,
      entryKeys: [
        ...resolveStoryBibleEntryKeysFromItems(recoverySummary),
        ...toStringArray(runtimeStoryBibleSummary.entryKeys),
      ].filter((item, index, array) => array.indexOf(item) === index),
    }
  }
  const runtimeToolCall = runtime?.toolCall
  if (normalizePhase(runtimeToolCall?.toolCode) === 'story_bible_update') {
    const parsed = parseJsonRecord(runtimeToolCall?.output)
    if (parsed) {
      return {
        ...(recoverySummary || {}),
        ...parsed,
        entryKeys: [
          ...resolveStoryBibleEntryKeysFromItems(recoverySummary),
          ...toStringArray(parsed.entryKeys),
        ].filter((item, index, array) => array.indexOf(item) === index),
      }
    }
  }
  return recoverySummary
}

const buildStoryBibleApprovalCard = (runtime?: WorkbenchRuntimeEventSource | null, recovery?: WorkbenchRecoverySnapshot | null): StoryBibleApprovalCardViewModel | undefined => {
  const summary = resolveStoryBibleSummary(runtime, recovery)
  const runtimeApproval = runtime?.approval as Record<string, unknown> | null | undefined
  const recoveryApproval = recovery?.pendingApproval as Record<string, unknown> | null | undefined
  const approvalType = normalizeText(runtimeApproval?.approvalType || recoveryApproval?.approvalType)
  const proposalSummary = normalizeText(summary?.proposalSummary)
  const entryKeys = [
    ...resolveStoryBibleEntryKeysFromItems(summary as Record<string, unknown> | null | undefined),
    ...toStringArray(summary?.entryKeys),
    ...toStringArray(runtimeApproval?.entryKeys),
    ...toStringArray(recoveryApproval?.entryKeys),
  ].filter((item, index, array) => array.indexOf(item) === index)
  const hasPendingApproval = approvalType === 'STORY_BIBLE_UPDATE'
  if (!proposalSummary && entryKeys.length === 0 && !hasPendingApproval) return undefined
  return {
    title: '故事圣经更新待确认',
    proposalSummary,
    entryKeys,
    nextActionText: normalizeText(summary?.nextAction)
      || normalizeText(runtimeApproval?.nextAction)
      || normalizeText(recoveryApproval?.nextAction),
  }
}

export const createWorkbenchRuntimePresenter = () => {
  const present = (input: {
    runtime?: WorkbenchRuntimeEventSource | null
    recovery?: WorkbenchRecoverySnapshot | null
  }): WorkbenchRuntimePresenterViewModel => {
    const runtime = input.runtime ?? null
    const recovery = input.recovery ?? null
    const phase = resolvePhase(runtime, recovery)
    const badgeText = phaseLabel(phase)
    const description = normalizeText(runtime?.message) || badgeText
    const nextActionText = resolveNextAction(runtime, recovery)
    const failureReasonText = resolveFailureReason(runtime, recovery)
    const toolCall = resolveToolCall(runtime, recovery)

    return {
      status: {
        title: '运行状态',
        badgeText,
        description,
        nextActionText,
        failureReasonText,
      },
      toolCallCard: buildToolCallCard(toolCall),
      todoPlanCard: buildTodoPlanCard(runtime, recovery),
      storyBibleApprovalCard: buildStoryBibleApprovalCard(runtime, recovery),
    }
  }

  return { present }
}
