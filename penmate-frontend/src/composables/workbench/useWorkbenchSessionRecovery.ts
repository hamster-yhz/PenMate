type RecoverySnapshot = {
  session?: {
    sessionId?: number | string | null
    title?: string | null
    status?: string | null
    boundStyle?: {
      styleId?: number | string | null
      name?: string | null
    } | null
  } | null
  activeTask?: {
    taskId?: number | string | null
    taskStatus?: string | null
    streamChannelKey?: string | null
  } | null
  pendingApproval?: Record<string, unknown> | null
  messages?: Array<Record<string, unknown>> | null
  workbenchContext?: {
    chapterId?: number | string | null
    selectedText?: string | null
    activePlugins?: string[] | null
    modelConfigId?: string | null
  } | null
}

/**
 * 工作台会话恢复编排器。
 * <p>负责调用后端 recovery / resume 接口，并把 snapshot 回填到前端 store；若存在运行中任务，则自动重连任务流。</p>
 */
export const useWorkbenchSessionRecovery = (deps: {
  getSessionRecovery: (projectId: number, sessionId: number) => Promise<RecoverySnapshot>
  resumeSession: (projectId: number, sessionId: number, payload: Record<string, unknown>) => Promise<RecoverySnapshot>
  openTaskStream: (projectId: number, taskId: number) => EventSource
  hydrateStore: (snapshot: RecoverySnapshot) => void
  resumeRunningTask?: (projectId: number, taskId: number) => Promise<void>
}) => {
  const restore = async (projectId: number, sessionId: number) => {
    const snapshot = await deps.resumeSession(projectId, sessionId, { trigger: 'WORKBENCH_ENTER' })
    deps.hydrateStore(snapshot)
    const taskId = Number(snapshot?.activeTask?.taskId ?? 0)
    const taskStatus = String(snapshot?.activeTask?.taskStatus ?? '').toUpperCase()
    if (taskId > 0 && taskStatus === 'RUNNING') {
      if (deps.resumeRunningTask) {
        await deps.resumeRunningTask(projectId, taskId)
      } else {
        deps.openTaskStream(projectId, taskId)
      }
    }
    return snapshot
  }

  return { restore }
}
