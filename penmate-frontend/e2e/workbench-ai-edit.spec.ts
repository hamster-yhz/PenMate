import { expect, test, type Page } from '@playwright/test'

const envelope = (data: unknown) => ({ data, meta: { traceId: 'visual-ai-edit' } })
const chapterTitle = '雨夜来客：一封来自旧城深处无人署名却写满秘密的长信'

const expectNoHorizontalOverflow = async (page: Page) => {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
}

const mockAiLockedWorkbench = async (page: Page) => {
  await page.addInitScript(() => {
    localStorage.setItem('penmate.session', JSON.stringify({
      userId: '1001',
      userName: '测试作者',
      userEmail: 'writer@example.com',
    }))
  })
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname
    if (path.endsWith('/v1/auth/refresh')) {
      return route.fulfill({ json: envelope({ accessToken: 'visual-access-token' }) })
    }
    if (path.endsWith('/v1/auth/me')) {
      return route.fulfill({ json: envelope({ id: '1001', email: 'writer@example.com', displayName: '测试作者' }) })
    }
    if (path.endsWith('/v1/profile/menus')) {
      return route.fulfill({ json: envelope([{ menuId: '1', path: '/admin', title: '管理员工作台' }]) })
    }
    if (path.endsWith('/v1/novels/2001')) {
      return route.fulfill({ json: envelope({ projectId: '2001', title: '雾港来信' }) })
    }
    if (path.endsWith('/v1/novels/2001/directory')) {
      return route.fulfill({ json: envelope({
        structureRevision: 2,
        volumes: [
          { volumeId: '2101', projectId: '2001', title: '第一卷', sortOrder: 1 },
        ],
        chapters: [
          { chapterId: '3001', projectId: '2001', volumeId: '2101', title: chapterTitle, sortOrder: 1, displayNo: 1, wordCount: 1280 },
          { chapterId: '3002', projectId: '2001', volumeId: '2101', title: '旧城回声', sortOrder: 2, displayNo: 2, wordCount: 960 },
        ],
      }) })
    }
    if (path.endsWith('/v1/novels/2001/chapters/3001/lease')) {
      return route.fulfill({ json: envelope({
        editable: false,
        ownerType: 'AI',
        contentRevision: 8,
        content: '雨水沿着檐角落下。\n\n沈砚听见门外传来三声短促的叩响。',
        reason: 'AI 正在编辑当前章节',
      }) })
    }
    if (path.endsWith('/v1/novels/2001/chapters/3001/ai-undo')) {
      return route.fulfill({ json: envelope([{
        operationId: '8801', runId: '7701', chapterId: '3001', chapterTitle,
        status: 'AVAILABLE', sequenceNo: 1, expiresAt: '2026-07-23T02:00:00Z',
      }]) })
    }
    if (path.endsWith('/v1/model/preferences')) {
      return route.fulfill({ json: envelope({ defaultCreativeModelConfigId: '5001' }) })
    }
    if (path.endsWith('/v1/model/configurations')) {
      return route.fulfill({ json: envelope([{
        modelConfigId: '5001', displayName: '创作模型', modelName: 'writer-pro', modelType: 'CHAT', status: 'ACTIVE',
      }]) })
    }
    return route.fulfill({ json: envelope([]) })
  })
}

test('AI locked chapter stays legible and exposes undo without layout overflow', async ({ page }, testInfo) => {
  await mockAiLockedWorkbench(page)
  await page.goto('/workbench?projectId=2001')

  await expect(page.getByText('AI 正在编辑当前章节')).toBeVisible()
  await page.getByRole('button', { name: '打开账户菜单' }).click()
  await expect(page.getByRole('menuitem', { name: /管理员工作台/ })).toBeVisible()
  await page.keyboard.press('Escape')
  if (testInfo.project.name === 'mobile-chromium') {
    const directoryPanel = page.locator('.panel-left')
    const editorPanel = page.locator('.panel-center')
    const aiPanel = page.locator('.panel-right')

    await expect(editorPanel).toBeVisible()
    await expect(directoryPanel).toBeHidden()
    await expect(aiPanel).toBeHidden()
    await expectNoHorizontalOverflow(page)

    await page.getByRole('button', { name: '目录', exact: true }).click()
    await expect(directoryPanel).toBeVisible()
    await expect(editorPanel).toBeHidden()
    await expect(aiPanel).toBeHidden()
    await expect(page.getByTestId('chapter-label-3001')).toHaveText(chapterTitle)
    expect(await page.locator('.tab-content').evaluate((element) => element.scrollWidth <= element.clientWidth)).toBe(true)
    await expect(page.getByTestId('chapter-label-3001')).toHaveCSS('text-overflow', 'ellipsis')
    await expectNoHorizontalOverflow(page)
    await page.screenshot({ path: testInfo.outputPath('workbench-mobile-directory.png'), fullPage: true })

    await page.getByTestId('chapter-node-3001').click()
    await expect(editorPanel).toBeVisible()
    await expect(directoryPanel).toBeHidden()
    await expect(aiPanel).toBeHidden()

    await page.getByRole('button', { name: 'AI', exact: true }).click()
    await expect(aiPanel).toBeVisible()
    await expect(directoryPanel).toBeHidden()
    await expect(editorPanel).toBeHidden()
    await expect(aiPanel.getByRole('button', { name: `撤回 ${chapterTitle} 的 AI 修改` })).toBeVisible()
    await expect(aiPanel).toHaveCSS('position', 'relative')
    await expectNoHorizontalOverflow(page)
    await page.screenshot({ path: testInfo.outputPath('workbench-mobile-ai.png'), fullPage: true })

    await page.getByRole('button', { name: '正文', exact: true }).click()
    await expect(editorPanel).toBeVisible()
    await page.setViewportSize({ width: 412, height: 520 })
    await expect(page.locator('.editor-statusbar')).toBeVisible()
    const editorBox = await page.locator('.editor-area').boundingBox()
    const statusBox = await page.locator('.editor-statusbar').boundingBox()
    expect(editorBox).not.toBeNull()
    expect(statusBox).not.toBeNull()
    expect(editorBox!.y + editorBox!.height).toBeLessThanOrEqual(statusBox!.y + 1)
    expect(statusBox!.y + statusBox!.height).toBeLessThanOrEqual(520)
    await expectNoHorizontalOverflow(page)
    await page.screenshot({ path: testInfo.outputPath('workbench-mobile-short-viewport.png'), fullPage: true })
  } else {
    await expect(page.getByRole('button', { name: `撤回 ${chapterTitle} 的 AI 修改` })).toBeVisible()

    const chooseLayout = async (name: '均衡' | '专注写作' | 'AI 协作') => {
      await page.getByRole('button', { name: '选择工作台布局' }).click()
      await page.getByRole('menuitem', { name, exact: true }).click()
    }
    const directoryPanel = page.locator('.panel-left')
    const aiPanel = page.locator('.panel-right')

    await chooseLayout('AI 协作')
    await expect(directoryPanel).toHaveClass(/collapsed/)
    await expect(aiPanel).not.toHaveClass(/collapsed/)
    await expect(aiPanel).toHaveCSS('width', '600px')
    await aiPanel.locator('.resize-handle').press('ArrowRight')
    await expect(aiPanel).toHaveCSS('width', '584px')
    await aiPanel.locator('.resize-handle').dblclick()
    await expect(aiPanel).toHaveCSS('width', '600px')

    await chooseLayout('专注写作')
    await expect(directoryPanel).toHaveClass(/collapsed/)
    await expect(aiPanel).toHaveClass(/collapsed/)
    await expect(page.getByRole('button', { name: '展开作品目录' })).toBeVisible()
    await expect(page.getByRole('button', { name: '展开 AI 协作' })).toBeVisible()

    await chooseLayout('均衡')
    await expect(directoryPanel).not.toHaveClass(/collapsed/)
    await expect(directoryPanel).toHaveCSS('width', '220px')
    await expect(aiPanel).not.toHaveClass(/collapsed/)
    await expect(aiPanel).toHaveCSS('width', '440px')
    expect(await page.evaluate(() => JSON.parse(
      localStorage.getItem('penmate.layout.1001.2001.writing') || '{}',
    ).preset)).toBe('balanced')

    await page.setViewportSize({ width: 1080, height: 720 })
    await expect(directoryPanel).toHaveClass(/collapsed/)
    await expect(aiPanel).not.toHaveClass(/collapsed/)
    await expect(page.getByRole('button', { name: '展开作品目录' })).toBeVisible()
    expect((await page.locator('.panel-center').boundingBox())!.width).toBeGreaterThanOrEqual(520)
    expect(await page.evaluate(() => JSON.parse(
      localStorage.getItem('penmate.layout.1001.2001.writing') || '{}',
    ).leftCollapsed)).toBe(false)

    await page.getByRole('button', { name: '展开作品目录' }).click()
    await expect(directoryPanel).not.toHaveClass(/collapsed/)
    await expect(page.locator('.workbench-shell')).toHaveClass(/directory-overlay-mode/)
    expect((await page.locator('.panel-center').boundingBox())!.width).toBeGreaterThanOrEqual(520)
  }
  const lockedEditor = page.locator('.editor-frame.ai-editing')
  await expect(lockedEditor).toBeVisible()
  await expect(lockedEditor).not.toHaveCSS('border-top-color', 'rgba(0, 0, 0, 0)')
  await expect(lockedEditor).not.toHaveCSS('filter', 'none')
  await expectNoHorizontalOverflow(page)

  await page.screenshot({ path: testInfo.outputPath('workbench-ai-locked.png'), fullPage: true })
})

test('plain text editor handles Chinese punctuation, paste, undo, and redo', async ({ page }, testInfo) => {
  await page.addInitScript(() => {
    localStorage.setItem('penmate.session', JSON.stringify({
      userId: '1001', userName: '测试作者', userEmail: 'writer@example.com',
    }))
  })
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname
    if (path.endsWith('/v1/auth/refresh')) return route.fulfill({ json: envelope({ accessToken: 'editor-token' }) })
    if (path.endsWith('/v1/auth/me')) {
      return route.fulfill({ json: envelope({ id: '1001', email: 'writer@example.com', displayName: '测试作者' }) })
    }
    if (path.endsWith('/v1/novels/2001')) {
      return route.fulfill({ json: envelope({ projectId: '2001', title: '雾港来信' }) })
    }
    if (path.endsWith('/v1/novels/2001/directory')) {
      return route.fulfill({ json: envelope({
        structureRevision: 1,
        volumes: [{ volumeId: '2101', projectId: '2001', title: '第一卷', sortOrder: 1 }],
        chapters: [{
          chapterId: '3001', projectId: '2001', volumeId: '2101', title: '第一章',
          sortOrder: 1, displayNo: 1, wordCount: 0,
        }],
      }) })
    }
    if (path.endsWith('/v1/novels/2001/chapters/3001/lease') && route.request().method() === 'POST') {
      return route.fulfill({ json: envelope({
        editable: true,
        ownerType: 'USER',
        leaseToken: 'editor-lease',
        contentRevision: 1,
        content: '雨落在雾港的石板路上。\n\n沈砚拆开那封没有署名的来信，纸页间夹着一枚褪色的车票。\n\n远处的钟楼敲过午夜，旧城的灯一盏接一盏熄灭。',
      }) })
    }
    if (path.endsWith('/v1/novels/2001/chapters/3001/content')) {
      return route.fulfill({ json: envelope({ contentRevision: 2 }) })
    }
    if (path.endsWith('/v1/model/preferences')) {
      return route.fulfill({ json: envelope({ defaultCreativeModelConfigId: '5001' }) })
    }
    if (path.endsWith('/v1/model/configurations')) {
      return route.fulfill({ json: envelope([{
        modelConfigId: '5001', displayName: '创作模型', modelName: 'writer-pro', modelType: 'CHAT', status: 'ACTIVE',
      }]) })
    }
    return route.fulfill({ json: envelope([]) })
  })

  await page.goto('/workbench?projectId=2001')
  const editor = page.locator('.cm-content')
  await expect(editor).toBeVisible()
  await expect(page.locator('.project-title')).toContainText('雾港来信')
  await expect(editor).toContainText('沈砚拆开那封没有署名的来信')
  await page.screenshot({ path: testInfo.outputPath('workbench-editor.png'), fullPage: true })
  await editor.click()
  await page.keyboard.press('Control+a')
  await page.keyboard.press('Backspace')
  await page.keyboard.insertText('“')
  await expect(editor).toHaveText('“”')
  await page.keyboard.insertText('夜')
  await expect(editor).toHaveText('“夜”')
  await page.waitForTimeout(650)
  await page.keyboard.insertText('深')
  await expect(editor).toHaveText('“夜深”')
  await page.keyboard.press('Control+z')
  await expect(editor).toHaveText('“夜”')
  await page.keyboard.press('Control+Shift+Z')
  await expect(editor).toHaveText('“夜深”')

  await page.keyboard.press('Control+a')
  await page.keyboard.press('Backspace')
  await page.context().grantPermissions(['clipboard-read', 'clipboard-write'])
  await page.evaluate(() => navigator.clipboard.writeText('《粘贴内容》'))
  await page.keyboard.press('Control+v')
  await expect(editor).toHaveText('《粘贴内容》')
  await page.keyboard.insertText('中文输入法，保持原样。')
  await expect(editor).toHaveText('《粘贴内容》中文输入法，保持原样。')
  await page.keyboard.press('Control+h')
  await expect(page.locator('.cm-search')).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.locator('.cm-search')).toBeHidden()
})
