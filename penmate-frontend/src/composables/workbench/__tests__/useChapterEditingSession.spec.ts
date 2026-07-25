import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { getChapter, saveContent } = vi.hoisted(() => ({
  getChapter: vi.fn(),
  saveContent: vi.fn(),
}))

vi.mock('@/api/modules/chapter.api', () => ({
  chapterApi: { getChapter, saveContent },
}))

import { useChapterEditingSession } from '../useChapterEditingSession'

const appError = (errorCode: string, message: string) => Object.assign(new Error(message), { errorCode })

describe('useChapterEditingSession', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    getChapter.mockResolvedValue({
      contentRevision: 4,
      content: '远端正文',
      leaseOwnerType: null,
      leaseExpiresAt: null,
    })
    saveContent.mockResolvedValue({ contentRevision: 5 })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('opens without acquiring a user lease and saves with optimistic revision control', async () => {
    const onSaved = vi.fn()
    const session = useChapterEditingSession({ onSaved })
    await session.open('project-101', 'chapter-301')

    session.scheduleSave('新的正文')
    await vi.advanceTimersByTimeAsync(1000)

    expect(getChapter).toHaveBeenCalledWith('project-101', 'chapter-301')
    expect(saveContent).toHaveBeenCalledWith('project-101', 'chapter-301', {
      expectedRevision: 4,
      content: '新的正文',
    })
    expect(session.contentRevision.value).toBe(5)
    expect(session.saveStatus.value).toBe('已保存')
    expect(onSaved).toHaveBeenCalledWith('project-101', 'chapter-301', '新的正文')
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
      expectedRevision: 5,
      content: '第二批',
    })
  })

  it('keeps the chapter read-only while an active AI lease exists', async () => {
    getChapter.mockResolvedValueOnce({
      contentRevision: 7,
      content: 'AI 编辑前正文',
      leaseOwnerType: 'AI',
      leaseExpiresAt: new Date(Date.now() + 60_000).toISOString(),
    })
    const session = useChapterEditingSession()

    const opened = await session.open('project-101', 'chapter-301')
    session.scheduleSave('不应保存')
    await vi.advanceTimersByTimeAsync(5000)

    expect(opened).toEqual({ content: 'AI 编辑前正文', editable: false })
    expect(session.leaseOwnerType.value).toBe('AI')
    expect(session.lockReason.value).toBe('AI 正在编辑当前章节')
    expect(saveContent).not.toHaveBeenCalled()
  })

  it('ignores an expired AI lease when opening the chapter', async () => {
    getChapter.mockResolvedValueOnce({
      contentRevision: 7,
      content: '正文',
      leaseOwnerType: 'AI',
      leaseExpiresAt: new Date(Date.now() - 1000).toISOString(),
    })
    const session = useChapterEditingSession()

    const opened = await session.open('project-101', 'chapter-301')

    expect(opened.editable).toBe(true)
    expect(session.leaseOwnerType.value).toBe('')
  })

  it('quarantines the local draft after another page wins the revision race', async () => {
    const onConflict = vi.fn()
    saveContent.mockRejectedValueOnce(appError('CHAPTER_REVISION_CONFLICT', 'stale revision'))
    const session = useChapterEditingSession({ onConflict })
    await session.open('project-101', 'chapter-301')

    session.scheduleSave('本地草稿')
    await vi.advanceTimersByTimeAsync(1000)

    expect(session.editable.value).toBe(false)
    expect(session.saveStatus.value).toBe('版本冲突')
    expect(session.lockReason.value).toContain('本地草稿已保留')
    expect(onConflict).toHaveBeenCalledWith('project-101', 'chapter-301', '本地草稿')
  })

  it('switches to AI read-only state when a user save loses the lock race', async () => {
    saveContent.mockRejectedValueOnce(appError('CHAPTER_AI_EDITING', 'AI editing'))
    const session = useChapterEditingSession()
    await session.open('project-101', 'chapter-301')

    session.scheduleSave('本地草稿')
    await vi.advanceTimersByTimeAsync(1000)

    expect(session.editable.value).toBe(false)
    expect(session.leaseOwnerType.value).toBe('AI')
    expect(session.saveStatus.value).toBe('AI 正在编辑')
  })

  it('locks immediately when the chapter edit event arrives', async () => {
    const session = useChapterEditingSession()
    await session.open('project-101', 'chapter-301')

    session.lockForAi('chapter-301')

    expect(session.editable.value).toBe(false)
    expect(session.leaseOwnerType.value).toBe('AI')
  })

  it('keeps dirty content offline and saves it once connectivity returns', async () => {
    const session = useChapterEditingSession()
    await session.open('project-101', 'chapter-301')

    session.setOnline(false)
    session.scheduleSave('离线正文')
    await vi.advanceTimersByTimeAsync(5000)
    expect(saveContent).not.toHaveBeenCalled()

    session.setOnline(true)
    await vi.waitFor(() => expect(saveContent).toHaveBeenCalledOnce())
    expect(saveContent).toHaveBeenCalledWith('project-101', 'chapter-301', {
      expectedRevision: 4,
      content: '离线正文',
    })
  })
})
