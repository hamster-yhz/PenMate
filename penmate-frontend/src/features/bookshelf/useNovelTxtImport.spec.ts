import { beforeEach, describe, expect, it, vi } from 'vitest'
import { novelApi } from '@/api/modules/novel.api'
import { useNovelTxtImport } from './useNovelTxtImport'

vi.mock('@/api/modules/novel.api', () => ({
  novelApi: {
    previewTxtImport: vi.fn(),
    importTxtProject: vi.fn(),
  },
}))

describe('useNovelTxtImport', () => {
  beforeEach(() => vi.clearAllMocks())

  it('keeps_preview_adjustments_local_until_the_user_confirms', async () => {
    vi.mocked(novelApi.previewTxtImport).mockResolvedValue({
      projectTitle: '长夜',
      volumes: [{
        title: '第一卷',
        chapters: [
          { title: '第一章', content: '雨水落下。' },
          { title: '第二章', content: '城门合拢。' },
        ],
      }],
    })
    vi.mocked(novelApi.importTxtProject).mockResolvedValue({ projectId: '9001' })
    const importer = useNovelTxtImport()

    await expect(importer.selectFile(new File(['正文'], '长夜.txt', { type: 'text/plain' }))).resolves.toBe(true)
    expect(novelApi.importTxtProject).not.toHaveBeenCalled()
    importer.preview.value!.projectTitle = '调整后的长夜'
    importer.addVolume()
    expect(importer.canConfirm.value).toBe(false)
    importer.moveChapter(0, 1, 1)
    importer.preview.value!.volumes[1]!.title = '第二卷'
    expect(importer.canConfirm.value).toBe(true)

    await expect(importer.confirmImport()).resolves.toBe('9001')
    expect(novelApi.importTxtProject).toHaveBeenCalledWith({
      projectTitle: '调整后的长夜',
      volumes: [
        { title: '第一卷', chapters: [{ title: '第一章', content: '雨水落下。' }] },
        { title: '第二卷', chapters: [{ title: '第二章', content: '城门合拢。' }] },
      ],
    })
  })

  it('rejects_non_txt_and_oversized_files_before_requesting_a_preview', async () => {
    const importer = useNovelTxtImport()

    await expect(importer.selectFile(new File(['x'], 'novel.docx'))).resolves.toBe(false)
    expect(importer.error.value).toBe('请选择 TXT 文件')
    expect(novelApi.previewTxtImport).not.toHaveBeenCalled()
  })
})
