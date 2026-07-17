import { reactive } from 'vue'
import type {
  WorkbenchContextPackageSnapshot,
  WorkbenchPromptPlanSnapshot,
  WorkbenchTaskProfileSnapshot,
} from '@/api/types'

export type WorkbenchSessionState = {
  sessionId: string | null
  title: string
  status: string
  boundStyle: { styleId: string | null; name: string }
  activeRun: { runId: string | null; runStatus: string; runPhase: string; latestSequence: string }
  pendingApproval: Record<string, unknown> | null
  messages: Array<Record<string, unknown>>
  workbenchContext: {
    chapterId: string | null
    selectedText: string
    activePlugins: string[]
    modelConfigId: string
    taskProfile: WorkbenchTaskProfileSnapshot | null
    promptPlan: WorkbenchPromptPlanSnapshot | null
    contextPackage: WorkbenchContextPackageSnapshot | null
  }
  resumeToken: string
}

/**
 * 工作台会话恢复状态。
 * <p>作为 recovery snapshot 在前端的唯一内存事实源，承载会话摘要、活动任务、审批与工作台上下文。</p>
 */
export const createWorkbenchSessionState = (): WorkbenchSessionState => reactive({
  sessionId: null,
  title: '',
  status: 'IDLE',
  boundStyle: { styleId: null, name: '' },
  activeRun: { runId: null, runStatus: '', runPhase: '', latestSequence: '0' },
  pendingApproval: null,
  messages: [],
  workbenchContext: {
    chapterId: null,
    selectedText: '',
    activePlugins: [],
    modelConfigId: '',
    taskProfile: null,
    promptPlan: null,
    contextPackage: null,
  },
  resumeToken: '',
})
