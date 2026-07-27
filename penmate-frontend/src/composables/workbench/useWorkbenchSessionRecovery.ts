import { pickBusinessRecord } from '@/utils/apiPayload'
import type { WorkbenchRecoverySnapshot } from '@/api/types'
import type { AgentRunEventStream } from '@/api/agentRunStream'

/**
 * 工作台会话恢复编排器。
 * <p>负责调用后端 recovery / resume 接口，并把 snapshot 回填到前端 store；若存在运行中任务，则自动重连任务流。</p>
 */
export const useWorkbenchSessionRecovery = (deps: {
  getSessionRecovery: (projectId: string, sessionId: string) => Promise<WorkbenchRecoverySnapshot>
  resumeSession: (
    projectId: string,
    sessionId: string,
    payload: Record<string, unknown>,
  ) => Promise<WorkbenchRecoverySnapshot>
  openRunStream: (projectId: string, runId: string, after?: string) => AgentRunEventStream
  hydrateStore: (snapshot: WorkbenchRecoverySnapshot) => void
  resumeRunningRun?: (projectId: string, runId: string, after?: string) => Promise<void>
}) => {
  const restore = async (projectId: string, sessionId: string, operatorId?: string) => {
    const snapshot = pickBusinessRecord(
      await deps.resumeSession(projectId, sessionId, {
        trigger: 'WORKBENCH_ENTER',
        ...(operatorId != null ? { operatorId } : {}),
      }),
    ) as WorkbenchRecoverySnapshot
    deps.hydrateStore(snapshot)
    const runId = snapshot?.activeRun?.runId
    const runStatus = String(snapshot?.activeRun?.runStatus ?? '').toUpperCase()
    const latestSequence = String(snapshot?.activeRun?.latestSequence ?? '0')
    const shouldResumeRun = ['PENDING', 'RUNNING', 'SUSPENDED'].includes(runStatus)
    if (runId != null && String(runId).trim() !== '' && String(runId) !== '0' && shouldResumeRun) {
      if (deps.resumeRunningRun) {
        void deps.resumeRunningRun(projectId, runId, latestSequence).catch(() => undefined)
      } else {
        deps.openRunStream(projectId, runId, latestSequence)
      }
    }
    return snapshot
  }

  return { restore }
}
