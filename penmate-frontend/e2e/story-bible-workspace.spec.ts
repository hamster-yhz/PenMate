import { expect, test, type Page } from '@playwright/test'

const envelope = (data: unknown) => ({ data, meta: { traceId: 'visual-story-bible' } })
const nodeType = { typeId: '21', storyBibleId: '11', typeCode: 'CHARACTER', semanticFamily: 'CHARACTER', displayName: '角色', iconCode: 'user', fieldSchemaJson: '{"type":"object","properties":{"身份":{"type":"string","title":"身份"}}}', system: true, sortOrder: 1 }
const nodes = [
  { nodeId: '71', storyBibleId: '11', typeId: '21', title: '沈砚', summary: '沉默寡言的旧城守夜人', bodyMarkdown: '他熟悉旧城每一条暗巷。', attributesJson: '{"身份":"守夜人"}', inclusionPolicy: 'AUTO_RETRIEVE', canonStatus: 'CANON', revision: 2 },
  { nodeId: '72', storyBibleId: '11', typeId: '21', title: '陆青禾', summary: '远征队领队', attributesJson: '{}', inclusionPolicy: 'AUTO_RETRIEVE', canonStatus: 'CANON', revision: 1 },
]

const mockStoryBible = async (page: Page, themeMode: 'LIGHT' | 'DARK') => {
  await page.addInitScript(() => localStorage.setItem('penmate.session', JSON.stringify({ userId: '1001', userName: '测试作者', userEmail: 'writer@example.com' })))
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url()); const path = url.pathname
    if (path.endsWith('/v1/auth/refresh')) return route.fulfill({ json: envelope({ accessToken: 'visual-access-token' }) })
    if (path.endsWith('/v1/auth/me')) return route.fulfill({ json: envelope({ id: '1001', displayName: '测试作者', email: 'writer@example.com' }) })
    if (path.endsWith('/v1/auth/ui-preferences')) return route.fulfill({ json: envelope({ themeMode, editorFontFamily: 'SERIF', editorFontSize: 17, editorLineHeight: 1.9, editorParagraphSpacing: 0.35, editorContentWidth: 760, typewriterMode: false, highlightCurrentParagraph: true }) })
    if (path.endsWith('/v1/novels/2001/volumes')) return route.fulfill({ json: envelope([{ volumeId: '2101', projectId: '2001', title: '第一卷', sortOrder: 1 }]) })
    if (path.endsWith('/v1/novels/2001/chapters')) return route.fulfill({ json: envelope([{ chapterId: '3001', projectId: '2001', volumeId: '2101', title: '雨夜来客', sortOrder: 1, displayNo: 1 }]) })
    if (path.endsWith('/v1/novels/2001/chapters/3001/lease')) return route.fulfill({ json: envelope({ editable: true, leaseToken: 'lease', contentRevision: 1, content: '' }) })
    if (path.endsWith('/v1/novels/2001/chapters/3001/ai-undo')) return route.fulfill({ json: envelope([]) })
    if (path.endsWith('/v1/model/preferences')) return route.fulfill({ json: envelope({ defaultCreativeModelConfigId: '5001' }) })
    if (path.endsWith('/v1/model/configurations')) return route.fulfill({ json: envelope([{ modelConfigId: '5001', displayName: '创作模型', modelName: 'writer-pro' }]) })
    if (path.endsWith('/v1/novels/2001/story-bible/node-types')) return route.fulfill({ json: envelope([nodeType]) })
    if (path.endsWith('/v1/novels/2001/story-bible/nodes/71/changesets')) return route.fulfill({ json: envelope([{ changesetId: '81', storyBibleId: '11', contentRevision: 3, actorType: 'AGENT', sourceRunId: '901', changeSummary: '补充角色身份', createdAt: '2026-07-22T02:00:00Z' }]) })
    if (path.endsWith('/v1/novels/2001/story-bible/nodes/71/effective-state')) return route.fulfill({ json: envelope({ 身份: '守夜人', 阵营: '旧城' }) })
    if (path.endsWith('/v1/novels/2001/story-bible/nodes/71')) return route.fulfill({ json: envelope({ node: nodes[0], aliases: [{ aliasId: '1', nodeId: '71', alias: '阿砚' }], categoryIds: [], tagIds: [] }) })
    if (path.endsWith('/v1/novels/2001/story-bible/nodes')) return route.fulfill({ json: envelope(nodes) })
    if (path.endsWith('/v1/novels/2001/story-bible/relations')) return route.fulfill({ json: envelope([{ relationId: '91', storyBibleId: '11', sourceNodeId: '71', targetNodeId: '72', relationType: 'ALLY_OF', description: '共同守护旧城', attributesJson: '{}', revision: 1 }]) })
    if (path.endsWith('/v1/novels/2001/story-bible/progressions')) return route.fulfill({ json: envelope([]) })
    if (path.endsWith('/v1/novels/2001/story-bible/categories') || path.endsWith('/v1/novels/2001/story-bible/tags') || path.endsWith('/v1/novels/2001/story-bible/views')) return route.fulfill({ json: envelope([]) })
    if (path.endsWith('/v1/novels/2001/story-bible/changesets')) return route.fulfill({ json: envelope([]) })
    if (path.endsWith('/v1/novels/2001/story-bible')) return route.fulfill({ json: envelope({ storyBibleId: '11', projectId: '2001', title: '旧城设定集', contentRevision: 3 }) })
    if (path.endsWith('/v1/novels/2001/agent/routing-preference')) return route.fulfill({ json: envelope({ mode: 'LLM_SELECTOR' }) })
    return route.fulfill({ json: envelope([]) })
  })
}

test('Story Bible uses a combined browser and readable relationship graph', async ({ page }, testInfo) => {
  const mobile = testInfo.project.name === 'mobile-chromium'
  await mockStoryBible(page, mobile ? 'DARK' : 'LIGHT')
  await page.goto('/workbench?projectId=2001&mode=story-bible')
  if (mobile) await page.getByRole('button', { name: '导航' }).click()
  await page.getByRole('button', { name: /沈砚/ }).click()
  await page.getByRole('button', { name: '关系' }).click()

  const graph = page.getByRole('img', { name: '沈砚的关系图' })
  await expect(graph).toBeVisible()
  await expect(graph.getByText('盟友', { exact: true })).toBeVisible()
  await expect(page.getByText('ALLY_OF')).toHaveCount(0)
  await expect(page.getByText(/修订/)).toHaveCount(0)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('story-bible-relationships.png'), fullPage: true })

  if (mobile) await page.getByRole('button', { name: '导航' }).click()
  const manageStructure = page.getByRole('button', { name: '管理 Story Bible 结构' })
  await manageStructure.click()
  const structureDialog = page.getByRole('dialog', { name: 'Story Bible 结构管理' })
  await expect(structureDialog).toBeVisible()
  await expect(structureDialog.getByPlaceholder('类型名称')).toBeFocused()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('story-bible-structure-manager.png'), fullPage: true })
  await page.keyboard.press('Escape')
  await expect(structureDialog).toBeHidden()
  await expect(manageStructure).toBeFocused()
})
