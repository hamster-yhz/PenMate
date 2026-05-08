import { reactive } from 'vue'

export type WorkbenchSessionState = {
  sessionId: number | null
  title: string
  status: string
  boundStyle: { styleId: number | null; name: string }
  activeTask: { taskId: number | null; taskStatus: string; streamChannelKey: string }
  pendingApproval: Record<string, unknown> | null
  messages: Array<Record<string, unknown>>
  workbenchContext: {
    chapterId: number | null
    selectedText: string
    activePlugins: string[]
    modelConfigId: string
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
  activeTask: { taskId: null, taskStatus: '', streamChannelKey: '' },
  pendingApproval: null,
  messages: [],
  workbenchContext: { chapterId: null, selectedText: '', activePlugins: [], modelConfigId: '' },
  resumeToken: '',
})
