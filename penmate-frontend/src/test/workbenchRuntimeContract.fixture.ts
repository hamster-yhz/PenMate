import type { WorkbenchRecoverySnapshot, WorkbenchRuntimeEventSource, WorkbenchRuntimeToolCall } from '@/api/types'

const createTodoItems = () => [
  { todoId: 'todo-1', sessionId: '90001', title: '修复密令来源', status: 'pending', priority: 'HIGH' },
  { todoId: 'todo-2', sessionId: '90001', title: '补充侍从转述桥段', status: 'pending', priority: 'MEDIUM' },
]

const createStoryBibleProposalItems = () => [
  {
    entryKey: 'maid.secret_order',
    entryType: 'CHARACTER_KNOWLEDGE',
    proposedContent: '侍从知晓密令并负责转运',
    canonicalStatus: 'PROPOSED',
    riskLevel: 2,
    sourceText: '第二段侍从转述密令',
    sourceChapterId: 301,
    inferenceLevel: 'DIRECT',
  },
]

const createChapterEditToolCall = (): WorkbenchRuntimeToolCall => ({
  toolCallId: 'call-chapter-edit-1',
  toolCode: 'chapter_edit',
  toolName: '编辑章节正文',
  status: 'running',
  argumentsPreview: { chapterId: '301' },
  output: { operationId: '81001', contentRevision: '4' },
  errorMessage: '',
})

const createTodoToolCall = (): WorkbenchRuntimeToolCall => ({
  toolCallId: 'call-todo-1',
  toolCode: 'todo_planner',
  toolName: 'Todo 规划',
  status: 'done',
  argumentsPreview: { planningMode: 'FOLLOW_UP_MODIFICATION' },
  output: { planTitle: '第三章修订待办' },
  errorMessage: '',
})

const createStoryBibleToolCall = (): WorkbenchRuntimeToolCall => ({
  toolCallId: 'call-story-1',
  toolCode: 'story_bible_update',
  toolName: '故事圣经整理',
  status: 'waiting_approval',
  argumentsPreview: { chapterId: '301' },
  output: { proposalSummary: '建议补充侍从知晓密令的设定' },
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
  activeRun: {
    turnId: '50001',
    runId: '70001',
    runStatus: 'running',
    runPhase: 'executing',
    latestSequence: '12',
  },
  pendingApproval: null,
  messages: [],
  workbenchContext: {
    chapterId: '301',
    selectedText: '夜雨中的追踪在巷口停住。',
    activePlugins: ['outline.search'],
    modelConfigId: 'mcfg-9001',
    activeRunRuntime: {
      lastRuntimeStatus: 'chapter_edit',
      latestSequence: '12',
      activeToolCallsSnapshot: [createChapterEditToolCall()],
    },
    resultSummary: {
      draftSummary: { draftText: '夜雨中的追踪在巷口停住。' },
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
      activeRunRuntime: {
        lastRuntimeStatus: 'todo_review',
        latestSequence: '32',
        activeToolCallsSnapshot: [createTodoToolCall()],
      },
      resultSummary: {
        draftSummary: null,
        qualityReportSummary: { reviewSummary: '存在剧情逻辑问题，需要修订。' },
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
    activeRun: {
      turnId: '50001',
      runId: '70001',
      runStatus: 'waiting_approval',
      runPhase: 'waiting_approval',
      latestSequence: '41',
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
      activeRunRuntime: {
        lastRuntimeStatus: 'story_bible_review',
        latestSequence: '41',
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
    activeRun: {
      turnId: '50002',
      runId: '70002',
      runStatus: 'failed',
      runPhase: 'failed',
      latestSequence: '52',
    },
    workbenchContext: {
      ...base.workbenchContext,
      activeRunRuntime: {
        lastRuntimeStatus: 'failed',
        latestSequence: '52',
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
  eventName: 'approval.requested',
  runId: '70001',
  sequence: '41',
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
    items: createTodoItems().map(({ todoId, sessionId, ...item }) => {
      void todoId
      void sessionId
      return item
    }),
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
  eventName: 'run.failed',
  runId: '70002',
  sequence: '52',
  sessionId: '90001',
  turnId: '50002',
  phase: 'failed',
  message: '质量审查超时',
  recoverable: true,
  nextAction: 'retry_run',
})

export const createRuntimeDoneEvent = (): WorkbenchRuntimeEventSource => ({
  eventName: 'run.completed',
  runId: '70001',
  sequence: '61',
  sessionId: '90001',
  turnId: '50001',
  phase: 'done',
  message: '已完成',
  status: 'completed',
  nextAction: 'show_result',
})
