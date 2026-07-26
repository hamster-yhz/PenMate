import { expect, test } from '@playwright/test'

const envelope = (data: unknown) => ({ data, meta: { traceId: 'bookshelf-import' } })

test('multi-format import reviews the manuscript before publishing the project', async ({ page }, testInfo) => {
  let importPayload: Record<string, unknown> | null = null
  await page.addInitScript(() => {
    localStorage.setItem('penmate.session', JSON.stringify({
      userId: '1001', userName: '测试作者', userEmail: 'writer@example.com',
    }))
  })
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (path.endsWith('/v1/auth/refresh')) return route.fulfill({ json: envelope({ accessToken: 'access' }) })
    if (path.endsWith('/v1/auth/me')) return route.fulfill({ json: envelope({ id: '1001', displayName: '测试作者' }) })
    if (path.endsWith('/v1/novels/imports/preview')) {
      return route.fulfill({ json: envelope({
        sessionId: '8001',
        draft: {
          projectTitle: '长夜', sourceFormat: 'EPUB', diagnostics: [],
          volumes: [{
            title: '第一卷',
            chapters: [
              { title: '第一章 来客', content: '雨水落下。' },
              { title: '第二章 回声', content: '城门合拢。' },
            ],
          }],
        },
      }) })
    }
    if (path.endsWith('/v1/novels/imports/8001/confirm') && request.method() === 'POST') {
      importPayload = request.postDataJSON() as Record<string, unknown>
      return route.fulfill({ json: envelope({ sessionId: '8001', status: 'QUEUED', totalChapters: 2 }) })
    }
    if (path.endsWith('/v1/novels/imports/8001') && request.method() === 'GET') {
      return route.fulfill({ json: envelope({
        sessionId: '8001', status: 'COMPLETED', projectId: '9001',
        checkpointChapter: 2, totalChapters: 2, progressCurrent: 2, progressTotal: 2,
      }) })
    }
    if (path.endsWith('/v1/novels')) return route.fulfill({ json: envelope([]) })
    return route.fulfill({ json: envelope([]) })
  })

  await page.goto('/mybooks')
  await page.getByRole('button', { name: '从文件创建作品' }).click()
  await page.locator('input[type="file"]').setInputFiles({
    name: '长夜.epub', mimeType: 'application/epub+zip', buffer: Buffer.from('mock epub'),
  })

  await expect(page.getByRole('dialog', { name: '从内容创建作品' })).toBeVisible()
  await page.getByLabel('作品名').fill('调整后的长夜')
  await page.getByTitle('添加卷').click()
  await page.getByLabel('卷名').fill('第二卷 旧城')
  await page.getByRole('button', { name: /第一卷/ }).click()
  await page.getByRole('button', { name: /第二章 回声/ }).click()
  await page.getByLabel('移动到其他卷').selectOption('1')
  await expect(page.getByRole('button', { name: '开始导入' })).toBeEnabled()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('bookshelf-import-review.png'), fullPage: true })
  await page.getByRole('button', { name: '开始导入' }).click()

  await expect(page).toHaveURL(/\/workbench\?projectId=9001$/)
  expect(importPayload).toMatchObject({ projectTitle: '调整后的长夜' })
  expect((importPayload!.volumes as Array<{ title: string; chapters: unknown[] }>)).toEqual([
    { title: '第一卷', chapters: [{ title: '第一章 来客', content: '雨水落下。' }] },
    { title: '第二卷 旧城', chapters: [{ title: '第二章 回声', content: '城门合拢。' }] },
  ])
})
