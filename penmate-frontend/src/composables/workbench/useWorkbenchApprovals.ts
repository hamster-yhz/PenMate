import { ref } from 'vue'
import type { ChatMessage } from '@/components/workbench/workbenchTypes'
import { getErrorMessage } from '@/utils/errors'

type ContextProfile = {
  projectId?: string | null
  operatorId?: string | null
}

type UseWorkbenchApprovalsDeps = {
  getContext: () => ContextProfile
  getMessages: () => ChatMessage[]
  approve: (projectId: string, approvalId: string, payload: Record<string, unknown>) => Promise<unknown>
  reject: (projectId: string, approvalId: string, payload: Record<string, unknown>) => Promise<unknown>
  onResolved?: (resolvedAction: 'approved' | 'rejected', approvalId: string) => Promise<void> | void
  notifyWarning?: (message: string) => void
}

export const useWorkbenchApprovals = (deps: UseWorkbenchApprovalsDeps) => {
  const approvalBusyIds = ref<string[]>([])

  const isApprovalBusy = (id: string) => approvalBusyIds.value.includes(id)

  const runApprovalAction = async (
    id: string,
    request: (projectId: string, approvalId: string, payload: Record<string, unknown>) => Promise<unknown>,
    comment: string,
    resolvedAction: 'approved' | 'rejected',
    failureMessage: string,
  ) => {
    if (isApprovalBusy(id)) return

    const messageItem = deps.getMessages().find((item) => item.approval?.id === id)
    if (!messageItem?.approval) return
    if (messageItem.approval.resolved) return
    if (!('resolvedAction' in messageItem.approval)) {
      messageItem.approval.resolvedAction = undefined
    }

    const { projectId, operatorId } = deps.getContext()
    const approvalId = id.trim()
    if (!projectId || !operatorId || !approvalId) {
      deps.notifyWarning?.('缺少审批上下文，无法完成操作')
      return
    }

    approvalBusyIds.value.push(id)
    try {
      await request(projectId, approvalId, {
        reviewedBy: operatorId,
        comment,
      })
      messageItem.approval.resolved = true
      messageItem.approval.resolvedAction = resolvedAction
      try {
        await deps.onResolved?.(resolvedAction, approvalId)
      } catch {
        deps.notifyWarning?.('审批已完成，但运行状态刷新失败')
      }
    } catch (error: unknown) {
      deps.notifyWarning?.(getErrorMessage(error, failureMessage))
    } finally {
      approvalBusyIds.value = approvalBusyIds.value.filter((item) => item !== id)
    }
  }

  const handleApprove = async (id: string) => {
    await runApprovalAction(id, deps.approve, '前端审批通过', 'approved', '审批通过失败')
  }

  const handleReject = async (id: string) => {
    await runApprovalAction(id, deps.reject, '前端审批拒绝', 'rejected', '审批拒绝失败')
  }

  return {
    approvalBusyIds,
    isApprovalBusy,
    handleApprove,
    handleReject,
  }
}
