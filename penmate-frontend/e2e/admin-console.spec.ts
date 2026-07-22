import { expect, test, type Page } from '@playwright/test'

const envelope = (data: unknown) => ({ data, meta: { traceId: 'admin-e2e-trace' } })

const mockAdminApi = async (page: Page) => {
  await page.addInitScript(() => {
    localStorage.setItem('penmate.session', JSON.stringify({
      accessToken: 'admin-access-token',
      userId: '1',
      userName: '系统管理员',
      userEmail: 'admin@penmate.local',
    }))
  })
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/v1/auth/refresh')) {
      return route.fulfill({ json: envelope({ accessToken: 'admin-access-token' }) })
    }
    if (path.endsWith('/v1/auth/me')) {
      return route.fulfill({
        json: envelope({ id: '1', displayName: '系统管理员', email: 'admin@penmate.local' }),
      })
    }
    if (path.endsWith('/v1/profile/menus')) {
      return route.fulfill({ json: envelope([{ menuId: '1', path: '/admin', title: '管理员工作台' }]) })
    }
    if (path.endsWith('/v1/model/providers')) {
      return route.fulfill({ json: envelope([{
        providerId: '11', code: 'openai', name: 'OpenAI',
        capabilities: [{ capabilityCode: 'CHAT', protocolCode: 'OPENAI_CHAT_COMPLETIONS' }],
      }]) })
    }
    if (path.endsWith('/v1/model/configurations')) {
      return route.fulfill({ json: envelope([{
        modelConfigId: '501', scopeType: 'SYSTEM', providerId: '11', providerName: 'OpenAI',
        displayName: '官方长篇创作', modelType: 'CHAT', modelName: 'gpt-5', maskedApiKey: '****1234',
        credentialConfigured: true, status: 'ACTIVE', lastTestStatus: null,
      }]) })
    }
    if (path.endsWith('/v1/model/system-configurations/501/connection-tests')) {
      return route.fulfill({ json: envelope({ success: true, latencyMs: 86, testedAt: '2026-07-22T03:00:00Z' }) })
    }
    if (path.endsWith('/v1/users/1/roles')) {
      return route.fulfill({ json: envelope({
        revision: 3,
        items: [{ roleId: '2001', code: 'ADMIN', name: '系统管理员' }],
      }) })
    }
    if (path.endsWith('/v1/roles/2001/permissions')) {
      return route.fulfill({ json: envelope({
        revision: 7,
        items: [
          { permissionId: '3001', code: 'rbac.manage', name: '管理角色与权限', module: 'rbac' },
          { permissionId: '3002', code: 'model.manage', name: '管理官方模型', module: 'model' },
        ],
      }) })
    }
    if (path.endsWith('/v1/users')) {
      return route.fulfill({ json: envelope([
        { userId: '1', email: 'admin@penmate.local', displayName: '系统管理员', status: 1, authMethod: 'local' },
      ]) })
    }
    if (path.endsWith('/v1/roles')) {
      return route.fulfill({ json: envelope([
        { roleId: '2001', code: 'ADMIN', name: '系统管理员', description: '平台完整管理权限', isSystem: true },
      ]) })
    }
    if (path.endsWith('/v1/permissions')) {
      return route.fulfill({ json: envelope([
        { permissionId: '3001', code: 'rbac.manage', name: '管理角色与权限', module: 'rbac' },
        { permissionId: '3002', code: 'model.manage', name: '管理官方模型', module: 'model' },
        { permissionId: '3003', code: 'novel.read', name: '查看作品', module: 'novel' },
        { permissionId: '3004', code: 'novel.manage', name: '管理作品', module: 'novel' },
      ]) })
    }
    if (path.endsWith('/v1/menus')) {
      return route.fulfill({ json: envelope([
        { menuId: '4001', title: '管理员工作台', path: '/admin', permissionCode: 'rbac.manage', visible: true },
      ]) })
    }
    return route.fulfill({ json: envelope([]) })
  })
}

test('administrator workspace integrates RBAC and official model management', async ({ page }, testInfo) => {
  await mockAdminApi(page)
  await page.goto('/admin/rbac')

  await expect(page.getByRole('heading', { level: 1, name: '角色与权限' })).toBeVisible()
  await expect(page.getByText('权限接口已接入')).toBeVisible()
  if (testInfo.project.name.includes('mobile')) {
    await expect(page.getByText('请使用桌面端管理角色与权限')).toBeVisible()
    await expect(page.getByTestId('rbac-tab-users')).toBeHidden()
  } else {
    await expect(page.getByTestId('rbac-tab-users')).toBeVisible()
    await page.getByTestId('rbac-tab-roles').click()
    await expect(page.getByRole('heading', { level: 3, name: '权限分配' })).toBeVisible()
    await expect(page.getByText('权限与角色')).toBeVisible()
    await expect(page.getByText('作品管理')).toBeVisible()
    await expect(page.getByTestId('rbac-permission-search')).toBeVisible()
    const permissionWidth = await page.getByTestId('rbac-permission-search').evaluate((element) =>
      element.getBoundingClientRect().width,
    )
    expect(permissionWidth).toBeGreaterThan(700)
  }
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('admin-rbac.png'), fullPage: true })

  await page.getByRole('button', { name: '官方模型' }).click()
  await expect(page).toHaveURL(/\/admin\/models$/)
  await expect(page.getByText('模型接口已接入')).toBeVisible()
  await expect(page.getByRole('table', { name: '官方模型列表' })).toBeVisible()
  await expect(page.getByText('官方长篇创作')).toBeVisible()
  await page.getByRole('button', { name: '测试连接：官方长篇创作' }).click()
  if (testInfo.project.name.includes('mobile')) await expect(page.getByText('连接成功，86 ms')).toBeVisible()
  else await expect(page.getByText('成功 · 86 ms')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('admin-models.png'), fullPage: true })
})
