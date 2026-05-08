import { describe, expect, it } from 'vitest'

import { pickConversationId } from '../useWorkbenchChatTimeline'

describe('useWorkbenchChatTimeline refactor contract', () => {
  it('reads sessionId only and rejects legacy conversationId fallback', () => {
    expect(pickConversationId({ sessionId: 'session-90001', conversationId: 'legacy-1' })).toBe('session-90001')
    expect(pickConversationId({ conversationId: 'legacy-1' })).toBe('')
  })
})
