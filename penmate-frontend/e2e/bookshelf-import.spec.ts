import { expect, test } from '@playwright/test'

const envelope = (data: unknown) => ({ data, meta: { traceId: 'bookshelf-import' } })

test('TXT import previews an adjustable directory before creating the project', async ({ page }, testInfo) => {
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
    if (path.endsWith('/v1/novels/imports/txt/preview')) {
      return route.fulfill({ json: envelope({
        projectTitle: '长夜',
        volumes: [{
          title: '第一卷',
          chapters: [
            { title: '第一章 来客', content: '雨水落下。' },
            { title: '第二章 回声', content: '城门合拢。' },
          ],
        }],
      }) })
    }
    if (path.endsWith('/v1/novels/imports/txt') && request.method() === 'POST') {
      importPayload = request.postDataJSON() as Record<string, unknown>
      return route.fulfill({ json: envelope({ projectId: '9001', title: '调整后的长夜' }) })
    }
    if (path.endsWith('/v1/novels')) return route.fulfill({ json: envelope([]) })
    return route.fulfill({ json: envelope([]) })
  })

  await page.goto('/mybooks')
  await page.getByRole('button', { name: '导入 TXT' }).click()
  await page.locator('input[type="file"]').setInputFiles({
    name: '长夜.txt', mimeType: 'text/plain', buffer: Buffer.from('第一章 来客\n雨水落下。'),
  })

  await expect(page.getByRole('dialog', { name: '导入 TXT' })).toBeVisible()
  await page.getByLabel('作品名').fill('调整后的长夜')
  await page.getByRole('button', { name: '添加卷' }).click()
  await expect(page.getByRole('button', { name: '确认导入' })).toBeDisabled()
  await page.getByLabel('卷名').nth(1).fill('第二卷 旧城')
  await page.getByLabel('所属卷').nth(1).selectOption('1')
  await expect(page.getByRole('button', { name: '确认导入' })).toBeEnabled()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('bookshelf-txt-import-preview.png'), fullPage: true })
  await page.getByRole('button', { name: '确认导入' }).click()

  await expect(page).toHaveURL(/\/workbench\?projectId=9001$/)
  expect(importPayload).toMatchObject({ projectTitle: '调整后的长夜' })
  expect((importPayload!.volumes as Array<{ title: string; chapters: unknown[] }>)).toEqual([
    { title: '第一卷', chapters: [{ title: '第一章 来客', content: '雨水落下。' }] },
    { title: '第二卷 旧城', chapters: [{ title: '第二章 回声', content: '城门合拢。' }] },
  ])
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
})
