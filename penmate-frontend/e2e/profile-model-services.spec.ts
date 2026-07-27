import { expect, test, type Page } from '@playwright/test'

const envelope = (data: unknown) => ({ data, meta: { traceId: 'visual-profile-models' } })

const mockProfileModels = async (page: Page) => {
  await page.addInitScript(() => {
    localStorage.setItem('penmate.session', JSON.stringify({ userId: '1001', userName: '测试作者', userEmail: 'writer@example.com', permissionCodes: ['app:access'] }))
  })
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/v1/auth/refresh')) return route.fulfill({ json: envelope({ accessToken: 'visual-access-token' }) })
    if (path.endsWith('/v1/auth/me')) return route.fulfill({ json: envelope({ id: '1001', displayName: '测试作者', email: 'writer@example.com', bio: '正在写一部长篇小说。', permissions: [{ code: 'app:access' }] }) })
    if (path.endsWith('/v1/auth/ui-preferences')) return route.fulfill({ json: envelope({ themeMode: 'LIGHT', editorFontFamily: 'SERIF', editorFontSize: 17, editorLineHeight: 1.9, editorParagraphSpacing: 0.35, editorContentWidth: 760, typewriterMode: false, highlightCurrentParagraph: true }) })
    if (path.endsWith('/v1/model/providers')) return route.fulfill({ json: envelope([{ providerId: '11', code: 'openai', name: 'OpenAI', baseUrl: 'https://api.openai.com/v1', authType: 'API_KEY', capabilities: [{ capabilityCode: 'CHAT', protocolCode: 'OPENAI_CHAT_COMPLETIONS' }, { capabilityCode: 'EMBEDDING', protocolCode: 'OPENAI_EMBEDDINGS' }] }]) })
    if (path.endsWith('/v1/model/model-discoveries')) return route.fulfill({ json: envelope({ models: ['gpt-4.1', 'gpt-5.6-sol', 'gpt-5-mini', 'o3', 'o4-mini'], count: 5 }) })
    if (path.endsWith('/v1/model/configurations')) return route.fulfill({ json: envelope([
      { modelConfigId: '501', scopeType: 'USER', ownerUserId: '1001', providerId: '11', providerCode: 'openai', providerName: 'OpenAI', displayName: '长篇创作', modelType: 'CHAT', modelName: 'gpt-5.6-sol', baseUrl: 'https://api.openai.com/v1', maskedApiKey: '****8F3A', credentialConfigured: true, status: 'ACTIVE', maxContextTokens: 1050000, maxOutputTokens: 128000, contextCapacitySource: 'CATALOG', contextCapacitySourceUrl: 'https://developers.openai.com/api/docs/models/gpt-5.6-sol', lastTestStatus: 'SUCCESS', lastTestLatencyMs: 462, lastTestedAt: '2026-07-22T02:30:00Z' },
      { modelConfigId: '502', scopeType: 'USER', ownerUserId: '1001', providerId: '11', providerCode: 'openai', providerName: 'OpenAI', displayName: '设定向量', modelType: 'EMBEDDING', modelName: 'text-embedding-3-large', maskedApiKey: '****8F3A', credentialConfigured: true, status: 'ACTIVE', embeddingDimensions: 3072 },
      { modelConfigId: '900', scopeType: 'SYSTEM', providerId: '11', providerName: 'OpenAI', displayName: '后台官方模型', modelType: 'CHAT', modelName: 'official-chat', status: 'ACTIVE' },
    ]) })
    if (path.endsWith('/v1/model/preferences')) return route.fulfill({ json: envelope({ defaultCreativeModelConfigId: '501', defaultContextSelectorModelConfigId: '501', defaultEmbeddingModelConfigId: '502' }) })
    if (path.endsWith('/v1/novels')) return route.fulfill({ json: envelope([]) })
    return route.fulfill({ json: envelope([]) })
  })
}

test('personal model services stay compact and open an editable drawer', async ({ page }, testInfo) => {
  await mockProfileModels(page)
  await page.goto('/profile?section=models')

  await expect(page.getByRole('heading', { name: '个人模型服务' })).toBeVisible()
  await expect(page.getByText('长篇创作', { exact: true })).toBeVisible()
  await expect(page.getByText('1,050,000 Token · 能力目录', { exact: true })).toBeVisible()
  await expect(page.getByText('后台官方模型', { exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '编辑模型' }).first().click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await expect(page.getByRole('switch', { name: /自动识别模型容量/ })).toBeChecked()
  await expect(page.getByPlaceholder('留空保留现有密钥')).toBeVisible()
  await page.getByRole('button', { name: '探测模型' }).click()
  await expect(page.getByRole('listbox', { name: '5 个可用模型' })).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('profile-model-discovery.png'), fullPage: true })
  await page.getByRole('option', { name: 'gpt-5-mini' }).click()
  await expect(page.getByPlaceholder('例如：gpt-5')).toHaveValue('gpt-5-mini')
  await expect(page.getByText('已从站点选择 gpt-5-mini')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)

  await page.screenshot({ path: testInfo.outputPath('profile-model-services.png'), fullPage: true })
})
