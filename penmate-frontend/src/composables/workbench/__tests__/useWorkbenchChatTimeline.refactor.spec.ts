import { describe, expect, it } from 'vitest'

import { buildApprovalCard, pickConversationId } from '../useWorkbenchChatTimeline'

describe('useWorkbenchChatTimeline refactor contract', () => {
  it('reads sessionId only and rejects legacy conversationId fallback', () => {
    expect(pickConversationId({ sessionId: 'session-90001', conversationId: 'legacy-1' })).toBe('session-90001')
    expect(pickConversationId({ conversationId: 'legacy-1' })).toBe('')
  })

  it('preserves_the_durable_story_bible_approval_preview', () => {
    expect(
      buildApprovalCard({
        approvalId: '43',
        toolCode: 'story_bible_update',
        approvalPreview: { kind: 'update_node', nodeId: '71' },
        approvalStatus: 'pending',
      }),
    ).toMatchObject({
      id: '43',
      toolCode: 'story_bible_update',
      preview: { kind: 'update_node', nodeId: '71' },
      resolved: false,
    })
  })
})
