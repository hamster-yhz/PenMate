import { expect, test, type Page } from '@playwright/test'

const envelope = (data: unknown) => ({ data, meta: { traceId: 'trash-e2e' } })

const mockTrash = async (page: Page) => {
  let deletedProjects = [
    { projectId: '2001', title: '旧城夜话', summary: '守夜人的旧城档案。', genre: '悬疑', totalWords: 42000, totalChapters: 18, deletedAt: new Date(Date.now() - 2 * 86_400_000).toISOString() },
    { projectId: '2002', title: '北境来信', summary: '来自雪原的未寄信件。', genre: '现实', totalWords: 18000, totalChapters: 7, deletedAt: new Date(Date.now() - 5 * 86_400_000).toISOString() },
  ]
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (path.endsWith('/v1/auth/refresh')) return route.fulfill({ json: envelope({ accessToken: 'trash-token' }) })
    if (path.endsWith('/v1/auth/me')) return route.fulfill({ json: envelope({ id: '1001', displayName: '测试作者', email: 'writer@example.com' }) })
    if (path.endsWith('/v1/novels/trash') && request.method() === 'GET') return route.fulfill({ json: envelope(deletedProjects) })
    if (path.endsWith('/v1/novels/trash/2001/restore')) {
      deletedProjects = deletedProjects.filter((project) => project.projectId !== '2001')
      return route.fulfill({ json: envelope({ projectId: '2001', title: '旧城夜话' }) })
    }
    if (path.endsWith('/v1/novels/trash/2002') && request.method() === 'DELETE') {
      expect(request.postDataJSON()).toEqual({ confirmationTitle: '北境来信' })
      deletedProjects = deletedProjects.filter((project) => project.projectId !== '2002')
      return route.fulfill({ json: envelope('deleted') })
    }
    if (path.endsWith('/v1/novels')) return route.fulfill({ json: envelope([]) })
    return route.fulfill({ json: envelope([]) })
  })
}

test('trash restores books and requires the exact title for permanent deletion', async ({ page }, testInfo) => {
  await mockTrash(page)
  await page.goto('/mybooks?view=trash')

  await expect(page.getByRole('heading', { name: '回收站' })).toBeVisible()
  const oldCity = page.getByTestId('trash-book-row').filter({ hasText: '旧城夜话' })
  await expect(oldCity.getByText(/还剩 \d+ 天/)).toBeVisible()
  await oldCity.getByRole('button', { name: '恢复' }).click()
  await expect(oldCity).toHaveCount(0)

  const northernLetter = page.getByTestId('trash-book-row').filter({ hasText: '北境来信' })
  await page.screenshot({ path: testInfo.outputPath('trash-list.png'), fullPage: true })
  await northernLetter.getByTitle('永久删除').click()
  const dialog = page.getByRole('dialog', { name: '永久删除作品' })
  const confirmButton = dialog.getByRole('button', { name: '永久删除' })
  await expect(confirmButton).toBeDisabled()
  await dialog.getByRole('textbox').fill('北境来信')
  await page.screenshot({ path: testInfo.outputPath('trash-confirmation.png'), fullPage: true })
  await confirmButton.click()

  await expect(page.getByText('回收站是空的')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
})
