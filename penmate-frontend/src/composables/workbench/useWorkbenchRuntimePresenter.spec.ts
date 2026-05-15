import { describe, expect, it } from 'vitest'
import { createWorkbenchRuntimePresenter } from './useWorkbenchRuntimePresenter'

describe('createWorkbenchRuntimePresenter', () => {
  it('maps_runtime_phases_into_user_facing_status_copy_and_cards', () => {
    const presenter = createWorkbenchRuntimePresenter()

    const viewModel = presenter.present({
      runtime: {
        eventName: 'generation.status',
        phase: 'story_bible_review',
        message: '正在整理故事圣经',
        recoverable: true,
        nextAction: 'review_story_bible',
        toolCall: {
          toolCallId: 'call-story-1',
          toolCode: 'story_bible_update',
          toolName: '故事圣经整理',
          status: 'running',
          iteration: 2,
          argumentsPreview: '{"chapterId":"301"}',
          output: '{"proposalSummary":"建议补充侍从知晓密令的设定"}',
          errorMessage: '',
        },
      },
      recovery: {
        pendingApproval: {
          approvalId: '88001',
          approvalType: 'STORY_BIBLE_UPDATE',
          toolCallId: 'call-story-1',
          nextAction: 'await_approval',
        },
        workbenchContext: {
          activeTaskRuntime: {
            lastRuntimeStatus: 'story_bible_review',
            recoveryCursor: 'approval:88001',
            activeToolCallsSnapshot: [
              {
                toolCallId: 'call-story-1',
                toolCode: 'story_bible_update',
                toolName: '故事圣经整理',
                status: 'waiting_approval',
                iteration: 2,
                argumentsPreview: '{"chapterId":"301"}',
                output: '{"proposalSummary":"建议补充侍从知晓密令的设定"}',
                errorMessage: '',
              },
            ],
          },
          resultSummary: {
            storyBibleProposalSummary: {
              proposalSummary: '建议补充侍从知晓密令的设定',
              entryKeys: ['maid.secret_order'],
              nextAction: 'await_approval',
            },
          },
        },
      },
    })

    expect(viewModel.status.badgeText).toBe('正在整理故事圣经')
    expect(viewModel.status.description).toBe('正在整理故事圣经')
    expect(viewModel.status.nextActionText).toBe('review_story_bible')
    expect(viewModel.toolCallCard).toMatchObject({
      title: '故事圣经整理',
      toolCode: 'story_bible_update',
      statusText: '进行中',
      argumentsPreview: '{"chapterId":"301"}',
    })
    expect(viewModel.storyBibleApprovalCard).toMatchObject({
      title: '故事圣经更新待确认',
      proposalSummary: '建议补充侍从知晓密令的设定',
      entryKeys: ['maid.secret_order'],
      nextActionText: 'await_approval',
    })
  })

  it('restores_todo_plan_and_failure_guidance_from_recovery_snapshot_without_local_fallback', () => {
    const presenter = createWorkbenchRuntimePresenter()

    const viewModel = presenter.present({
      runtime: {
        eventName: 'generation.failed',
        phase: 'failed',
        message: '执行失败',
        errorMsg: '质量审查超时',
        recoverable: true,
        nextAction: 'retry_generation',
      },
      recovery: {
        pendingApproval: null,
        workbenchContext: {
          activeTaskRuntime: {
            lastRuntimeStatus: 'todo_review',
            recoveryCursor: 'tool_call:todo_planner:call-todo-1',
            activeToolCallsSnapshot: [
              {
                toolCallId: 'call-todo-1',
                toolCode: 'todo_planner',
                toolName: 'Todo 规划',
                status: 'done',
                iteration: 1,
                argumentsPreview: '{"planningMode":"FOLLOW_UP_MODIFICATION"}',
                output: '{"planTitle":"第三章修订待办"}',
                errorMessage: '',
              },
            ],
          },
          resultSummary: {
            todoSummary: {
              planTitle: '第三章修订待办',
              items: [
                { title: '修正文脉络跳跃', status: 'pending', priority: 'HIGH' },
                { title: '补充侍从知晓密令的设定', status: 'pending', priority: 'MEDIUM' },
              ],
              nextAction: 'apply_todo_plan',
            },
          },
        },
      },
    })

    expect(viewModel.status.badgeText).toBe('执行失败')
    expect(viewModel.status.failureReasonText).toBe('质量审查超时')
    expect(viewModel.status.nextActionText).toBe('retry_generation')
    expect(viewModel.todoPlanCard).toMatchObject({
      title: '第三章修订待办',
      itemCountText: '2 项待办',
      nextActionText: 'apply_todo_plan',
    })
    expect(viewModel.todoPlanCard?.items).toEqual([
      { title: '修正文脉络跳跃', statusText: 'pending', priorityText: 'HIGH' },
      { title: '补充侍从知晓密令的设定', statusText: 'pending', priorityText: 'MEDIUM' },
    ])
    expect(viewModel.toolCallCard).toMatchObject({
      title: 'Todo 规划',
      toolCode: 'todo_planner',
      statusText: '已完成',
    })
  })

  it('builds_todo_and_story_bible_cards_directly_from_live_runtime_events', () => {
    const presenter = createWorkbenchRuntimePresenter()

    const todoViewModel = presenter.present({
      runtime: {
        eventName: 'generation.tool_call',
        phase: 'todo_review',
        message: '正在整理待办',
        nextAction: 'review_todo_plan',
        toolCall: {
          toolCallId: 'call-todo-1',
          toolCode: 'todo_planner',
          toolName: 'Todo 规划',
          status: 'running',
          output: JSON.stringify({
            planTitle: '第三章修订待办',
            items: [
              { title: '修正文脉络跳跃', status: 'pending', priority: 'HIGH' },
              { title: '补充侍从知晓密令的设定', status: 'pending', priority: 'MEDIUM' },
            ],
            nextAction: 'apply_todo_plan',
          }),
        },
      },
    })

    expect(todoViewModel.todoPlanCard).toMatchObject({
      title: '第三章修订待办',
      itemCountText: '2 项待办',
      nextActionText: 'apply_todo_plan',
    })

    const storyBibleViewModel = presenter.present({
      runtime: {
        eventName: 'generation.waiting_approval',
        phase: 'story_bible_review',
        message: '正在整理故事圣经',
        nextAction: 'await_approval',
        approval: {
          approvalId: '88001',
          approvalType: 'STORY_BIBLE_UPDATE',
          toolCallId: 'call-story-1',
          nextAction: 'await_approval',
          entryKeys: ['maid.secret_order'],
        },
        toolCall: {
          toolCallId: 'call-story-1',
          toolCode: 'story_bible_update',
          toolName: '故事圣经整理',
          status: 'waiting_approval',
          output: JSON.stringify({
            proposalSummary: '建议补充侍从知晓密令的设定',
            entryKeys: ['maid.secret_order'],
            nextAction: 'await_approval',
          }),
        },
      },
    })

    expect(storyBibleViewModel.storyBibleApprovalCard).toMatchObject({
      title: '故事圣经更新待确认',
      proposalSummary: '建议补充侍从知晓密令的设定',
      entryKeys: ['maid.secret_order'],
      nextActionText: 'await_approval',
    })
  })

  it('selects_current_tool_call_by_pending_approval_or_recovery_cursor_instead_of_snapshot_order', () => {
    const presenter = createWorkbenchRuntimePresenter()

    const viewModel = presenter.present({
      recovery: {
        pendingApproval: {
          approvalId: '88001',
          approvalType: 'STORY_BIBLE_UPDATE',
          toolCallId: 'call-story-1',
          nextAction: 'await_approval',
        },
        activeTask: {
          taskStatus: 'RUNNING',
        },
        workbenchContext: {
          activeTaskRuntime: {
            lastRuntimeStatus: 'story_bible_review',
            recoveryCursor: 'approval:88001',
            activeToolCallsSnapshot: [
              {
                toolCallId: 'call-quality-1',
                toolCode: 'quality_review',
                toolName: '质量审查',
                status: 'done',
                output: '{"reviewSummary":"已完成"}',
              },
              {
                toolCallId: 'call-story-1',
                toolCode: 'story_bible_update',
                toolName: '故事圣经整理',
                status: 'waiting_approval',
                output: '{"proposalSummary":"建议补充侍从知晓密令的设定"}',
              },
            ],
          },
          resultSummary: {
            storyBibleProposalSummary: {
              proposalSummary: '建议补充侍从知晓密令的设定',
              entryKeys: ['maid.secret_order'],
              nextAction: 'await_approval',
            },
          },
        },
      },
    })

    expect(viewModel.status.badgeText).toBe('正在整理故事圣经')
    expect(viewModel.toolCallCard).toMatchObject({
      title: '故事圣经整理',
      toolCode: 'story_bible_update',
      statusText: '等待审批',
    })
  })

  it('maps_real_backend_executing_and_tool_call_phases_into_user_facing_copy', () => {
    const presenter = createWorkbenchRuntimePresenter()

    expect(presenter.present({
      runtime: {
        eventName: 'generation.status',
        phase: 'executing',
        toolCall: {
          toolCode: 'quality_review',
          toolName: '质量审查',
          status: 'running',
        },
      },
    }).status.badgeText).toBe('正在审查质量')

    expect(presenter.present({
      runtime: {
        eventName: 'generation.tool_call',
        phase: 'tool_call',
        toolCall: {
          toolCode: 'todo_planner',
          toolName: 'Todo 规划',
          status: 'running',
        },
      },
    }).status.badgeText).toBe('正在整理待办')
  })

  it('covers_all_required_runtime_phase_labels', () => {
    const presenter = createWorkbenchRuntimePresenter()

    expect(presenter.present({ runtime: { phase: 'planning', eventName: 'generation.started' } }).status.badgeText).toBe('正在分析请求')
    expect(presenter.present({ runtime: { phase: 'context_building', eventName: 'generation.status' } }).status.badgeText).toBe('正在规划章节')
    expect(presenter.present({ runtime: { phase: 'draft_generation', eventName: 'generation.status' } }).status.badgeText).toBe('正在生成正文')
    expect(presenter.present({ runtime: { phase: 'quality_review', eventName: 'generation.status' } }).status.badgeText).toBe('正在审查质量')
    expect(presenter.present({ runtime: { phase: 'story_bible_review', eventName: 'generation.status' } }).status.badgeText).toBe('正在整理故事圣经')
    expect(presenter.present({ runtime: { phase: 'todo_review', eventName: 'generation.status' } }).status.badgeText).toBe('正在整理待办')
    expect(presenter.present({ runtime: { phase: 'waiting_approval', eventName: 'generation.waiting_approval' } }).status.badgeText).toBe('等待审批')
    expect(presenter.present({ runtime: { phase: 'done', eventName: 'generation.done' } }).status.badgeText).toBe('已完成')
    expect(presenter.present({ runtime: { phase: 'failed', eventName: 'generation.failed' } }).status.badgeText).toBe('执行失败')
  })
})
