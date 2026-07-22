import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useWorkbenchDraft } from '../useWorkbenchDraft'

describe('useWorkbenchDraft', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    localStorage.clear()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('keeps the latest draft available and never writes chapter正文 to localStorage', async () => {
    const drafts = useWorkbenchDraft()

    drafts.saveDraft('project-101', 'chapter-301', '第一稿')
    drafts.saveDraft('project-101', 'chapter-301', '第二稿')
    await vi.advanceTimersByTimeAsync(250)

    expect(await drafts.resolveStoredDraft('project-101', 'chapter-301')).toBe('第二稿')
    expect(localStorage.length).toBe(0)
  })

  it('does not clear a newer local draft when an older server save completes', async () => {
    const drafts = useWorkbenchDraft()
    drafts.saveDraft('project-101', 'chapter-301', '旧内容')
    await vi.advanceTimersByTimeAsync(250)
    drafts.saveDraft('project-101', 'chapter-301', '更新内容')

    await drafts.markDraftSynced('project-101', 'chapter-301', '旧内容')

    expect(await drafts.resolveStoredDraft('project-101', 'chapter-301')).toBe('更新内容')
  })

  it('clears the recovery draft after the same content is saved remotely', async () => {
    const drafts = useWorkbenchDraft()
    drafts.saveDraft('project-101', 'chapter-301', '已同步内容')

    await drafts.markDraftSynced('project-101', 'chapter-301', '已同步内容')

    expect(await drafts.resolveStoredDraft('project-101', 'chapter-301')).toBeNull()
  })
})
