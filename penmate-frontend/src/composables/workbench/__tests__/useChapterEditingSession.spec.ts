import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { acquireLease, renewLease, releaseLease, saveContent } = vi.hoisted(() => ({
  acquireLease: vi.fn(),
  renewLease: vi.fn(),
  releaseLease: vi.fn(),
  saveContent: vi.fn(),
}))

vi.mock('@/api/modules/chapter.api', () => ({
  chapterApi: { acquireLease, renewLease, releaseLease, saveContent },
}))

import { useChapterEditingSession } from '../useChapterEditingSession'

describe('useChapterEditingSession', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    sessionStorage.clear()
    acquireLease.mockResolvedValue({
      editable: true,
      leaseToken: 'lease-1',
      contentRevision: 4,
      content: '远端正文',
    })
    renewLease.mockResolvedValue({})
    releaseLease.mockResolvedValue('released')
    saveContent.mockResolvedValue({ contentRevision: 5 })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('debounces a save for one second and advances the server revision', async () => {
    const onSaved = vi.fn()
    const session = useChapterEditingSession({ onSaved })
    await session.open('project-101', 'chapter-301')

    session.scheduleSave('新的正文')
    await vi.advanceTimersByTimeAsync(999)
    expect(saveContent).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(1)
    expect(saveContent).toHaveBeenCalledWith('project-101', 'chapter-301', {
      leaseToken: 'lease-1',
      expectedRevision: 4,
      content: '新的正文',
    })
    expect(session.contentRevision.value).toBe(5)
    expect(session.saveStatus.value).toBe('已保存')
    expect(onSaved).toHaveBeenCalledWith('project-101', 'chapter-301', '新的正文')
    await session.release()
  })

  it('coalesces changes made while a save request is in flight', async () => {
    let finishFirstSave!: (value: Record<string, unknown>) => void
    saveContent
      .mockImplementationOnce(() => new Promise((resolve) => { finishFirstSave = resolve }))
      .mockResolvedValueOnce({ contentRevision: 6 })
    const session = useChapterEditingSession()
    await session.open('project-101', 'chapter-301')

    session.scheduleSave('第一批')
    await vi.advanceTimersByTimeAsync(1000)
    session.scheduleSave('第二批')
    finishFirstSave({ contentRevision: 5 })
    await vi.waitFor(() => expect(saveContent).toHaveBeenCalledTimes(2))

    expect(saveContent).toHaveBeenNthCalledWith(2, 'project-101', 'chapter-301', {
      leaseToken: 'lease-1',
      expectedRevision: 5,
      content: '第二批',
    })
    await session.release()
  })

  it('does not create a new revision when flushing unchanged content', async () => {
    const session = useChapterEditingSession()
    await session.open('project-101', 'chapter-301')

    await session.flush('远端正文')

    expect(saveContent).not.toHaveBeenCalled()
    await session.release()
  })

  it('resumes the tab-scoped lease after a page refresh', async () => {
    sessionStorage.setItem('penmate.chapter-lease:project-101:chapter-301', 'lease-before-refresh')
    renewLease.mockResolvedValueOnce({
      editable: true,
      leaseToken: 'lease-before-refresh',
      ownerType: 'USER',
      contentRevision: 8,
      content: '刷新前正文',
    })
    const session = useChapterEditingSession()

    const opened = await session.open('project-101', 'chapter-301')

    expect(renewLease).toHaveBeenCalledWith('project-101', 'chapter-301', 'lease-before-refresh')
    expect(acquireLease).not.toHaveBeenCalled()
    expect(opened).toEqual({ content: '刷新前正文', editable: true })
    expect(session.contentRevision.value).toBe(8)
    await session.release()
    expect(sessionStorage.getItem('penmate.chapter-lease:project-101:chapter-301')).toBeNull()
  })

  it('falls back to a fresh lease when the stored lease cannot be resumed', async () => {
    sessionStorage.setItem('penmate.chapter-lease:project-101:chapter-301', 'expired-lease')
    renewLease.mockRejectedValueOnce(new Error('expired'))
    const session = useChapterEditingSession()

    await session.open('project-101', 'chapter-301')

    expect(acquireLease).toHaveBeenCalledWith('project-101', 'chapter-301', false)
    expect(sessionStorage.getItem('penmate.chapter-lease:project-101:chapter-301')).toBe('lease-1')
    await session.release()
  })

  it('keeps the recovery token when releasing the lease fails', async () => {
    releaseLease.mockRejectedValueOnce(new Error('network unavailable'))
    const session = useChapterEditingSession()

    await session.open('project-101', 'chapter-301')
    await session.release()

    expect(sessionStorage.getItem('penmate.chapter-lease:project-101:chapter-301')).toBe('lease-1')
  })

  it('stays read-only when another editor owns the chapter lease', async () => {
    acquireLease.mockResolvedValueOnce({
      editable: false,
      ownerType: 'AI',
      contentRevision: 7,
      content: 'AI 编辑前正文',
    })
    const session = useChapterEditingSession()

    const opened = await session.open('project-101', 'chapter-301')
    session.scheduleSave('不应保存')
    await vi.advanceTimersByTimeAsync(5000)

    expect(opened).toEqual({ content: 'AI 编辑前正文', editable: false })
    expect(session.lockReason.value).toBe('AI 正在编辑当前章节')
    expect(saveContent).not.toHaveBeenCalled()
  })

  it('keeps dirty content offline and saves it once connectivity returns', async () => {
    const session = useChapterEditingSession()
    await session.open('project-101', 'chapter-301')

    session.setOnline(false)
    session.scheduleSave('离线正文')
    await vi.advanceTimersByTimeAsync(5000)

    expect(session.saveStatus.value).toBe('离线')
    expect(saveContent).not.toHaveBeenCalled()

    session.setOnline(true)
    await vi.waitFor(() => expect(saveContent).toHaveBeenCalledOnce())
    expect(saveContent).toHaveBeenCalledWith('project-101', 'chapter-301', {
      leaseToken: 'lease-1',
      expectedRevision: 4,
      content: '离线正文',
    })
    expect(session.saveStatus.value).toBe('已保存')
    await session.release()
  })
})
