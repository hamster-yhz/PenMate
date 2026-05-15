import { pickBusinessRecord } from '@/utils/apiPayload'
import type { WorkbenchRecoverySnapshot } from '@/api/types'

/**
 * 工作台会话恢复编排器。
 * <p>负责调用后端 recovery / resume 接口，并把 snapshot 回填到前端 store；若存在运行中任务，则自动重连任务流。</p>
 */
export const useWorkbenchSessionRecovery = (deps: {
  getSessionRecovery: (projectId: string, sessionId: string) => Promise<WorkbenchRecoverySnapshot>
  resumeSession: (projectId: string, sessionId: string, payload: Record<string, unknown>) => Promise<WorkbenchRecoverySnapshot>
  openTurnStream: (projectId: string, sessionId: string, turnId: string) => EventSource
  hydrateStore: (snapshot: WorkbenchRecoverySnapshot) => void
  resumeRunningTask?: (projectId: string, sessionId: string, turnId: string) => Promise<void>
}) => {
  const restore = async (projectId: string, sessionId: string, operatorId?: string) => {
    const snapshot = pickBusinessRecord(await deps.resumeSession(projectId, sessionId, {
      trigger: 'WORKBENCH_ENTER',
      ...(operatorId != null ? { operatorId } : {}),
    })) as WorkbenchRecoverySnapshot
    deps.hydrateStore(snapshot)
    const turnId = snapshot?.activeTask?.turnId
    const taskStatus = String(snapshot?.activeTask?.taskStatus ?? '').toUpperCase()
    if (turnId != null && String(turnId).trim() !== '' && String(turnId) !== '0' && taskStatus === 'RUNNING') {
      if (deps.resumeRunningTask) {
        await deps.resumeRunningTask(projectId, sessionId, turnId)
      } else {
        deps.openTurnStream(projectId, sessionId, turnId)
      }
    }
    return snapshot
  }

  return { restore }
}
