import { expect, test, type Page } from '@playwright/test'

const envelope = (data: unknown) => ({ data, meta: { traceId: 'visual-project-settings' } })
const configuration = {
  creativeModelConfigId: '501',
  routerModelConfigId: '501',
  embeddingModelConfigId: '502',
  storyBibleRoutingMode: 'RETRIEVAL_THEN_LLM',
  indexStatus: 'READY',
  lastIndexCompletedAt: '2026-07-22 10:30',
}

const mockProjectSettings = async (page: Page) => {
  await page.addInitScript(() => {
    localStorage.setItem('penmate.session', JSON.stringify({ userId: '1001', userName: '测试作者', userEmail: 'writer@example.com' }))
  })
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/v1/auth/refresh')) return route.fulfill({ json: envelope({ accessToken: 'visual-access-token' }) })
    if (path.endsWith('/v1/auth/me')) {
      return route.fulfill({
        json: envelope({
          id: '1001',
          displayName: '测试作者',
          email: 'writer@example.com',
          permissions: [{ code: 'app:access' }],
        }),
      })
    }
    if (path.endsWith('/v1/auth/ui-preferences')) return route.fulfill({ json: envelope({ themeMode: 'LIGHT', editorFontFamily: 'SERIF' }) })
    if (path.endsWith('/v1/model/configurations')) return route.fulfill({ json: envelope([
      { modelConfigId: '501', scopeType: 'USER', displayName: '长篇创作', modelType: 'CHAT', modelName: 'writer-pro', status: 'ACTIVE' },
      { modelConfigId: '502', scopeType: 'USER', displayName: '设定向量', modelType: 'EMBEDDING', modelName: 'embedding-pro', status: 'ACTIVE' },
    ]) })
    if (path.endsWith('/v1/novels/2001/rag/configuration')) return route.fulfill({ json: envelope(configuration) })
    if (path.endsWith('/v1/novels/2001/rag/rebuild')) return route.fulfill({ json: envelope({ status: 'QUEUED', jobId: '701' }) })
    if (path.endsWith('/v1/novels/2001/cover')) return route.fulfill({ json: envelope({ status: 'EMPTY' }) })
    if (path.endsWith('/v1/novels/2001')) return route.fulfill({ json: envelope({ projectId: '2001', title: '旧城夜话', summary: '守夜人与远征队追查旧城异变。', genre: '悬疑', tags: ['群像', '旧城'] }) })
    return route.fulfill({ json: envelope([]) })
  })
}

test('project settings exposes every agreed section without overflow', async ({ page }, testInfo) => {
  await mockProjectSettings(page)
  await page.goto('/projects/2001/settings')

  const selectSection = async (label: string, value: string) => {
    if (testInfo.project.name.includes('mobile')) {
      await page.getByTestId('project-settings-mobile-section').selectOption(value)
    } else {
      await page.getByRole('button', { name: label }).click()
    }
  }

  await expect(page.getByRole('heading', { name: '基本信息' })).toBeVisible()
  await selectSection('AI 与上下文', 'ai')
  await expect(page.getByRole('heading', { name: 'AI 与上下文' })).toBeVisible()
  await expect(page.getByText('混合筛选', { exact: true })).toBeVisible()
  await selectSection('上下文索引', 'index')
  await expect(page.getByText('设定向量', { exact: true })).toBeVisible()
  await selectSection('数据管理', 'data')
  await expect(page.getByText('DOCX', { exact: true })).toBeVisible()
  await selectSection('危险操作', 'danger')
  await expect(page.getByText('作品会从书架隐藏，并可在 30 天内从回收站恢复。')).toBeVisible()
  await selectSection('AI 与上下文', 'ai')

  if (testInfo.project.name.includes('mobile')) {
    await expect(page.getByTestId('project-settings-mobile-section')).toHaveValue('ai')
    await expect(page.getByRole('navigation', { name: '作品设置分区' })).toBeHidden()
  }

  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('project-settings-ai.png'), fullPage: true })
})

test('cover upload opens a fixed two-to-three crop workspace', async ({ page }, testInfo) => {
  await mockProjectSettings(page)
  await page.goto('/projects/2001/settings')

  const png = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAIAAAADCAIAAAA2iEnWAAAAFElEQVR4nGP4z8DAwMDAxMDAwMAAAAwAAf8CBYkAAAAASUVORK5CYII=',
    'base64',
  )
  await page.locator('input[type="file"]').setInputFiles({ name: 'cover.png', mimeType: 'image/png', buffer: png })

  await expect(page.getByRole('dialog').getByText('裁切作品封面')).toBeVisible()
  await expect(page.getByRole('button', { name: '使用这个裁切' })).toBeEnabled()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('project-cover-crop.png'), fullPage: true })
})
