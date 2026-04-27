import { describe, expect, it, vi } from 'vitest'

type ApprovalCardData = {
  id: string
  message: string
  time: string
  resolved: boolean
  resolvedAction?: 'approved' | 'rejected'
}

type ChatMessage = {
  id: number
  role: 'user' | 'assistant' | 'system'
  text: string
  approval?: ApprovalCardData
}

type UseWorkbenchApprovalsFactory = (deps: any) => {
  approvalBusyIds: { value: string[] }
  isApprovalBusy: (id: string) => boolean
  handleApprove: (id: string) => Promise<void>
  handleReject: (id: string) => Promise<void>
}

const loadUseWorkbenchApprovals = async (): Promise<UseWorkbenchApprovalsFactory> => {
  const modulePath = '../useWorkbenchApprovals'
  return (await import(/* @vite-ignore */ modulePath)).useWorkbenchApprovals as UseWorkbenchApprovalsFactory
}

describe('useWorkbenchApprovals', () => {
  it('approves_message_approval_and_clears_busy_state', async () => {
    const useWorkbenchApprovals = await loadUseWorkbenchApprovals()
    const approve = vi.fn().mockResolvedValue('ok')
    const messages = [
      {
        id: 1,
        role: 'assistant',
        text: '待审批消息',
        approval: {
          id: '42',
          message: '归档角色卡',
          time: '20:00',
          resolved: false,
        },
      },
    ] as ChatMessage[]

    const approvals = useWorkbenchApprovals({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      getMessages: () => messages,
      approve,
      reject: vi.fn(),
      notifyWarning: vi.fn(),
    })

    await approvals.handleApprove('42')

    expect(approve).toHaveBeenCalledWith(101, 42, {
      reviewedBy: 201,
      comment: '前端审批通过',
    })
    expect(messages[0].approval).toMatchObject({
      resolved: true,
      resolvedAction: 'approved',
    })
    expect(approvals.isApprovalBusy('42')).toBe(false)
    expect(approvals.approvalBusyIds.value).toEqual([])
  })

  it('rejects_message_approval_and_marks_rejected', async () => {
    const useWorkbenchApprovals = await loadUseWorkbenchApprovals()
    const reject = vi.fn().mockResolvedValue('ok')
    const messages = [
      {
        id: 2,
        role: 'assistant',
        text: '待审批消息',
        approval: {
          id: '43',
          message: '拒绝设定',
          time: '20:01',
          resolved: false,
        },
      },
    ] as ChatMessage[]

    const approvals = useWorkbenchApprovals({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      getMessages: () => messages,
      approve: vi.fn(),
      reject,
      notifyWarning: vi.fn(),
    })

    await approvals.handleReject('43')

    expect(reject).toHaveBeenCalledWith(101, 43, {
      reviewedBy: 201,
      comment: '前端审批拒绝',
    })
    expect(messages[0].approval).toMatchObject({
      resolved: true,
      resolvedAction: 'rejected',
    })
    expect(approvals.approvalBusyIds.value).toEqual([])
  })

  it('surfaces_string_request_errors_and_clears_busy_state_without_mutating_approval', async () => {
    const useWorkbenchApprovals = await loadUseWorkbenchApprovals()
    const notifyWarning = vi.fn()
    const approve = vi.fn().mockRejectedValue('网络故障')
    const messages = [
      {
        id: 4,
        role: 'assistant',
        text: '待审批消息',
        approval: {
          id: '44',
          message: '审批失败场景',
          time: '20:03',
          resolved: false,
        },
      },
    ] as ChatMessage[]

    const approvals = useWorkbenchApprovals({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      getMessages: () => messages,
      approve,
      reject: vi.fn(),
      notifyWarning,
    })

    await approvals.handleApprove('44')

    expect(approve).toHaveBeenCalledWith(101, 44, {
      reviewedBy: 201,
      comment: '前端审批通过',
    })
    expect(notifyWarning).toHaveBeenCalledWith('网络故障')
    expect(messages[0].approval).toMatchObject({
      resolved: false,
      resolvedAction: undefined,
    })
    expect(approvals.isApprovalBusy('44')).toBe(false)
    expect(approvals.approvalBusyIds.value).toEqual([])
  })

  it('skips_already_resolved_approvals_without_reissuing_requests', async () => {
    const useWorkbenchApprovals = await loadUseWorkbenchApprovals()
    const approve = vi.fn().mockResolvedValue('ok')
    const messages = [
      {
        id: 5,
        role: 'assistant',
        text: '已处理审批消息',
        approval: {
          id: '45',
          message: '已通过的审批',
          time: '20:04',
          resolved: true,
          resolvedAction: 'approved',
        },
      },
    ] as ChatMessage[]

    const approvals = useWorkbenchApprovals({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      getMessages: () => messages,
      approve,
      reject: vi.fn(),
      notifyWarning: vi.fn(),
    })

    await approvals.handleApprove('45')

    expect(approve).not.toHaveBeenCalled()
    expect(messages[0].approval).toMatchObject({
      resolved: true,
      resolvedAction: 'approved',
    })
    expect(approvals.isApprovalBusy('45')).toBe(false)
    expect(approvals.approvalBusyIds.value).toEqual([])
  })

  it('warns_and_skips_when_approval_context_missing', async () => {
    const useWorkbenchApprovals = await loadUseWorkbenchApprovals()
    const notifyWarning = vi.fn()
    const approve = vi.fn()
    const messages = [
      {
        id: 3,
        role: 'assistant',
        text: '待审批消息',
        approval: {
          id: '0',
          message: '无效审批',
          time: '20:02',
          resolved: false,
        },
      },
    ] as ChatMessage[]

    const approvals = useWorkbenchApprovals({
      getContext: () => ({ projectId: 0, operatorId: 0 }),
      getMessages: () => messages,
      approve,
      reject: vi.fn(),
      notifyWarning,
    })

    await approvals.handleApprove('0')

    expect(notifyWarning).toHaveBeenCalledWith('缺少审批上下文，无法完成操作')
    expect(approve).not.toHaveBeenCalled()
    expect(messages[0].approval).toMatchObject({
      resolved: false,
      resolvedAction: undefined,
    })
  })
})
