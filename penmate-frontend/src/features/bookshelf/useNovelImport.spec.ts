import { beforeEach, describe, expect, it, vi } from 'vitest'
import { novelApi } from '@/api/modules/novel.api'
import { useNovelImport } from './useNovelImport'

vi.mock('@/api/modules/novel.api', () => ({
  novelApi: {
    previewNovelImport: vi.fn(),
    confirmNovelImport: vi.fn(),
    getNovelImport: vi.fn(),
    pauseNovelImport: vi.fn(),
    resumeNovelImport: vi.fn(),
    cancelNovelImport: vi.fn(),
    retryNovelImport: vi.fn(),
  },
}))

describe('useNovelImport', () => {
  beforeEach(() => vi.clearAllMocks())

  it('keeps preview edits local and supports directory operations', async () => {
    vi.mocked(novelApi.previewNovelImport).mockResolvedValue({
      sessionId: '8001',
      draft: {
        projectTitle: '长夜', sourceFormat: 'MARKDOWN',
        volumes: [{ title: '第一卷', chapters: [
          { title: '第一章', content: '雨水落下。' },
          { title: '第二章', content: '城门合拢。' },
        ] }],
      },
    })
    vi.mocked(novelApi.confirmNovelImport).mockResolvedValue({
      sessionId: '8001', status: 'PAUSED', jobStatus: 'CANCELLED',
    })
    const importer = useNovelImport()

    await expect(importer.selectFile(new File(['正文'], '长夜.md'))).resolves.toBe(true)
    expect(novelApi.confirmNovelImport).not.toHaveBeenCalled()
    importer.draft.value!.projectTitle = '调整后的长夜'
    importer.addVolume()
    importer.selectVolume(0)
    importer.selectChapter(0)
    importer.moveChapterToVolume(1)
    expect(importer.draft.value!.volumes[1]!.chapters).toHaveLength(1)
    importer.selectVolume(0)
    importer.selectChapter(0)
    importer.splitChapter(2)
    expect(importer.draft.value!.volumes[0]!.chapters).toHaveLength(2)
    importer.mergePrevious()
    expect(importer.draft.value!.volumes[0]!.chapters).toHaveLength(1)

    await expect(importer.confirmImport()).resolves.toBe(true)
    expect(novelApi.confirmNovelImport).toHaveBeenCalledWith('8001', expect.objectContaining({
      projectTitle: '调整后的长夜',
    }))
  })

  it('accepts the three file formats and rejects unsupported files', async () => {
    const importer = useNovelImport()
    await expect(importer.selectFile(new File(['x'], 'novel.pdf'))).resolves.toBe(false)
    expect(importer.error.value).toBe('请选择 TXT、Markdown 或 DOCX 文件')
    expect(novelApi.previewNovelImport).not.toHaveBeenCalled()
  })
})
