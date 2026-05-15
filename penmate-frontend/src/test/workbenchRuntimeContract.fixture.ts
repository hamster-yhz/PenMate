import type {
  WorkbenchRecoverySnapshot,
  WorkbenchRuntimeEventSource,
  WorkbenchRuntimeToolCall,
} from '@/api/types'

const createTodoItems = () => ([
  {
    todoId: 'todo-1',
    sessionId: '90001',
    title: '修复密令来源',
    status: 'pending',
    priority: 'HIGH',
  },
  {
    todoId: 'todo-2',
    sessionId: '90001',
    title: '补充侍从转述桥段',
    status: 'pending',
    priority: 'MEDIUM',
  },
])

const createStoryBibleProposalItems = () => ([
  {
    entryKey: 'maid.secret_order',
    entryType: 'CHARACTER_KNOWLEDGE',
    proposedContent: '侍从知晓密令并负责转述',
    canonicalStatus: 'PROPOSED',
    riskLevel: 2,
    sourceText: '第二段侍从转述密令',
    sourceChapterId: 301,
    inferenceLevel: 'DIRECT',
  },
])

const createDraftToolCall = (): WorkbenchRuntimeToolCall => ({
  toolCallId: 'call-draft-1',
  toolCode: 'draft_generation',
  toolName: '正文生成',
  status: 'running',
  argumentsPreview: {
    chapterId: '301',
  },
  output: {
    draftText: '夜雨中的追踪在巷口停住。',
  },
  errorMessage: '',
})

const createTodoToolCall = (): WorkbenchRuntimeToolCall => ({
  toolCallId: 'call-todo-1',
  toolCode: 'todo_planner',
  toolName: 'Todo 规划',
  status: 'done',
  argumentsPreview: {
    planningMode: 'FOLLOW_UP_MODIFICATION',
  },
  output: {
    planTitle: '第三章修订待办',
  },
  errorMessage: '',
})

const createStoryBibleToolCall = (): WorkbenchRuntimeToolCall => ({
  toolCallId: 'call-story-1',
  toolCode: 'story_bible_update',
  toolName: '故事圣经整理',
  status: 'waiting_approval',
  argumentsPreview: {
    chapterId: '301',
  },
  output: {
    proposalSummary: '建议补充侍从知晓密令的设定',
  },
  errorMessage: '',
})

const createFailedQualityToolCall = (): WorkbenchRuntimeToolCall => ({
  toolCallId: 'call-quality-timeout',
  toolCode: 'quality_review',
  toolName: '质量审查',
  status: 'failed',
  errorMessage: '质量审查超时',
})

export const createBaseRecoverySnapshot = (): WorkbenchRecoverySnapshot => ({
  session: {
    sessionId: '90001',
    title: '第三章夜雨追踪',
    status: 'ACTIVE',
    boundStyle: { styleId: '81', name: '冷峻悬疑' },
  },
  activeTask: {
    turnId: '50001',
    taskId: '70001',
    taskStatus: 'running',
    requestContextId: '71001',
  },
  pendingApproval: null,
  messages: [],
  workbenchContext: {
    chapterId: '301',
    selectedText: '夜雨中的追踪在巷口停住。',
    activePlugins: ['outline.search'],
    modelConfigId: 'mcfg-9001',
    activeTaskRuntime: {
      lastRuntimeStatus: 'draft_generation',
      recoveryCursor: 'tool_call:draft_generation:call-draft-1',
      activeToolCallsSnapshot: [createDraftToolCall()],
    },
    resultSummary: {
      draftSummary: {
        draftText: '夜雨中的追踪在巷口停住。',
      },
      qualityReportSummary: null,
      todoSummary: null,
      storyBibleProposalSummary: null,
    },
  },
})

export const createTodoReviewRecoverySnapshot = (): WorkbenchRecoverySnapshot => {
  const base = createBaseRecoverySnapshot()
  return {
    ...base,
    workbenchContext: {
      ...base.workbenchContext,
      activeTaskRuntime: {
        lastRuntimeStatus: 'todo_review',
        recoveryCursor: 'tool_call:todo_planner:call-todo-1',
        activeToolCallsSnapshot: [createTodoToolCall()],
      },
      resultSummary: {
        draftSummary: null,
        qualityReportSummary: {
          reviewSummary: '存在剧情逻辑问题，需要修订。',
        },
        todoSummary: {
          planTitle: '第三章修订待办',
          recommendedNextAction: 'apply_todo_plan',
          items: createTodoItems(),
        },
        storyBibleProposalSummary: null,
      },
    },
  }
}

export const createStoryBibleWaitingApprovalRecoverySnapshot = (): WorkbenchRecoverySnapshot => {
  const base = createBaseRecoverySnapshot()
  return {
    ...base,
    activeTask: {
      turnId: '50001',
      taskId: '70001',
      taskStatus: 'waiting_approval',
      requestContextId: '71001',
    },
    pendingApproval: {
      approvalId: '88001',
      approvalType: 'STORY_BIBLE_UPDATE',
      toolCallId: 'call-story-1',
      nextAction: 'await_approval',
    },
    messages: [
      {
        messageId: '1',
        role: 'assistant',
        contentMd: '故事圣经更新待确认',
        approvalId: '88001',
        approvalType: 'STORY_BIBLE_UPDATE',
        approvalStatus: 'pending',
      },
    ],
    workbenchContext: {
      ...base.workbenchContext,
      activeTaskRuntime: {
        lastRuntimeStatus: 'story_bible_review',
        recoveryCursor: 'approval:88001',
        activeToolCallsSnapshot: [createStoryBibleToolCall()],
      },
      resultSummary: {
        draftSummary: null,
        qualityReportSummary: null,
        todoSummary: null,
        storyBibleProposalSummary: {
          proposalSummary: '建议补充侍从知晓密令的设定',
          items: createStoryBibleProposalItems(),
        } as any,
      },
    },
  }
}

export const createFailedRecoverySnapshot = (): WorkbenchRecoverySnapshot => {
  const base = createBaseRecoverySnapshot()
  return {
    ...base,
    activeTask: {
      turnId: '50002',
      taskId: '70002',
      taskStatus: 'failed',
      requestContextId: '71001',
    },
    workbenchContext: {
      ...base.workbenchContext,
      activeTaskRuntime: {
        lastRuntimeStatus: 'failed',
        recoveryCursor: 'tool_call:quality_review:call-quality-timeout',
        activeToolCallsSnapshot: [createFailedQualityToolCall()],
      },
      resultSummary: {
        draftSummary: null,
        qualityReportSummary: null,
        todoSummary: null,
        storyBibleProposalSummary: null,
      },
    },
  }
}

export const createRuntimeWaitingApprovalEvent = (): WorkbenchRuntimeEventSource => ({
  eventName: 'generation.waiting_approval',
  taskId: '70001',
  sessionId: '90001',
  turnId: '50001',
  phase: 'waiting_approval',
  message: '等待审批',
  approval: {
    approvalId: '88001',
    approvalType: 'STORY_BIBLE_UPDATE',
    toolCallId: 'call-story-1',
    nextAction: 'await_approval',
  },
  toolCall: createStoryBibleToolCall(),
  todoPlan: {
    planTitle: '第三章修订待办',
    planSummary: '补齐密令来源链路',
    recommendedNextAction: 'apply_todo_plan',
    items: createTodoItems().map(({ todoId, sessionId, ...item }) => item),
  },
  storyBibleApproval: {
    approvalId: '88001',
    approvalType: 'STORY_BIBLE_UPDATE',
    proposalSummary: '建议补充侍从知晓密令的设定',
    entryKeys: ['maid.secret_order'],
    nextAction: 'await_approval',
  },
  recoverable: true,
  nextAction: 'await_approval',
})

export const createRuntimeFailedEvent = (): WorkbenchRuntimeEventSource => ({
  eventName: 'generation.failed',
  taskId: '70002',
  sessionId: '90001',
  turnId: '50002',
  phase: 'failed',
  message: '质量审查超时',
  recoverable: true,
  nextAction: 'retry_task',
})

export const createRuntimeDoneEvent = (): WorkbenchRuntimeEventSource => ({
  eventName: 'generation.done',
  taskId: '70001',
  sessionId: '90001',
  turnId: '50001',
  phase: 'done',
  message: '已完成',
  status: 'done',
  nextAction: 'show_result',
})
