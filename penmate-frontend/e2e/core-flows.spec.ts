import { expect, test, type Page } from '@playwright/test'

const envelope = (data: unknown) => ({ data, meta: { traceId: 'e2e-trace' } })

const mockAuthenticatedApi = async (page: Page) => {
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname

    if (path.endsWith('/v1/auth/refresh')) {
      return route.fulfill({ json: envelope({ accessToken: 'e2e-access-token' }) })
    }
    if (path.endsWith('/v1/auth/me')) {
      return route.fulfill({
        json: envelope({
          id: '1001',
          email: 'writer@example.com',
          displayName: '测试作者',
          bio: '真实资料',
          permissions: [{ code: 'app:access' }],
        }),
      })
    }
    if (path.endsWith('/v1/auth/logout')) return route.fulfill({ json: envelope('ok') })
    if (path.endsWith('/v1/novels')) return route.fulfill({ json: envelope([]) })
    if (path.includes('/v1/model/keys')) return route.fulfill({ json: envelope([]) })
    if (path.includes('/v1/model/preferences')) {
      return route.fulfill({ json: envelope({ candidateConfigs: [] }) })
    }
    return route.fulfill({ json: envelope([]) })
  })
}

test('home shows the real workbench and opens the login experience', async ({ page }, testInfo) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { level: 1, name: 'PenMate' })).toBeVisible()
  const preview = page.getByTestId('home-preview').getByRole('img')
  await expect(preview).toBeVisible()
  expect(await preview.evaluate((image: HTMLImageElement) => image.complete && image.naturalWidth > 0)).toBe(true)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('home.png'), fullPage: true })
  await page.getByTestId('home-nav-enter').click()
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByTestId('login-form')).toBeVisible()
})

test('anonymous users are redirected away from protected routes', async ({ page }) => {
  await page.route('**/api/v1/auth/refresh', (route) =>
    route.fulfill({ status: 401, json: envelope({ message: 'Login required' }) }),
  )
  await page.goto('/profile')
  await expect(page).toHaveURL(/\/login\?redirect=\/profile$/)
})

test('cookie session recovery opens profile and logout ends the session', async ({ page }, testInfo) => {
  await page.addInitScript((theme) => localStorage.setItem('penmate.theme', theme),
    testInfo.project.name.includes('mobile') ? 'dark' : 'light')
  await mockAuthenticatedApi(page)
  await page.goto('/profile')
  const profileCard = page.getByTestId('profile-hero-card')
  await expect(profileCard.getByText('测试作者')).toBeVisible()
  await expect(profileCard.getByText('writer@example.com')).toBeVisible()
  await profileCard.getByRole('button', { name: '编辑资料' }).click()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('profile-edit.png'), fullPage: true })
  await profileCard.getByRole('button', { name: '取消' }).click()
  await page.locator('button.account-button').click()
  await page.getByRole('menuitem', { name: '退出登录' }).click()
  await expect(page).toHaveURL(/\/login$/)
})

test('changing the login email requires the current password and returns to login', async ({ page }, testInfo) => {
  await page.addInitScript((theme) => localStorage.setItem('penmate.theme', theme),
    testInfo.project.name.includes('mobile') ? 'dark' : 'light')
  await mockAuthenticatedApi(page)
  let changePayload: unknown
  await page.route('**/api/v1/auth/email', async (route) => {
    changePayload = route.request().postDataJSON()
    await route.fulfill({ json: envelope('ok') })
  })
  await page.goto('/profile?section=security')

  await page.getByTestId('profile-security-email-toggle').click()
  await page.getByTestId('profile-security-email-input').fill('new@example.com')
  await page.getByTestId('profile-security-email-password').fill('correct-password')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('profile-security.png'), fullPage: true })
  await page.getByTestId('profile-security-email-save').click()

  await expect(page).toHaveURL(/\/login$/)
  expect(changePayload).toEqual({ currentPassword: 'correct-password', newEmail: 'new@example.com' })
  expect(await page.evaluate(() => localStorage.getItem('penmate.session'))).toBeNull()
})

test('security settings can end all other device sessions without ending the current one', async ({ page }) => {
  await mockAuthenticatedApi(page)
  let bulkRevokeCalls = 0
  await page.route('**/api/v1/auth/sessions', async (route) => {
    if (route.request().method() === 'DELETE') {
      bulkRevokeCalls += 1
      return route.fulfill({ json: envelope(2) })
    }
    return route.fulfill({
      json: envelope([
        {
          sessionId: 'current-session', deviceName: 'Desktop', browserName: 'Chrome',
          operatingSystem: 'Windows', ipAddress: '127.0.0.1', current: true,
        },
        {
          sessionId: 'other-session-1', deviceName: 'Mobile', browserName: 'Safari',
          operatingSystem: 'iOS', ipAddress: '10.0.0.8', current: false,
        },
        {
          sessionId: 'other-session-2', deviceName: 'Desktop', browserName: 'Firefox',
          operatingSystem: 'Linux', ipAddress: '10.0.0.9', current: false,
        },
      ]),
    })
  })

  await page.goto('/profile?section=security')
  await expect(page.getByText('Chrome · Windows')).toBeVisible()
  await expect(page.getByText('Safari · iOS')).toBeVisible()

  await page.getByTestId('profile-sessions-revoke-others').click()

  await expect(page.getByText('Safari · iOS')).toBeHidden()
  await expect(page.getByText('Firefox · Linux')).toBeHidden()
  await expect(page.getByText('Chrome · Windows')).toBeVisible()
  expect(bulkRevokeCalls).toBe(1)
})

test('cookie session recovery opens the composed workbench', async ({ page }) => {
  await mockAuthenticatedApi(page)
  await page.goto('/workbench?projectId=2001')

  await expect(page.getByRole('group', { name: '工作模式' })).toBeVisible()
  await expect(page.getByRole('button', { name: '写作' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Story Bible' })).toBeVisible()
})

test('account deletion requires password and explicit risk confirmation', async ({ page }, testInfo) => {
  await mockAuthenticatedApi(page)
  await page.goto('/profile')
  if ((page.viewportSize()?.width ?? 1280) <= 760) await page.getByRole('combobox', { name: '设置分区' }).selectOption('data')
  else await page.getByRole('button', { name: '数据与账户' }).click()
  await page.getByRole('button', { name: '注销账户' }).click()

  const dialog = page.getByRole('dialog')
  await expect(dialog.getByText('确认注销账户')).toBeVisible()
  await expect(dialog.getByRole('button', { name: '确认注销' })).toBeDisabled()
  await dialog.getByLabel('当前密码').fill('correct-password')
  await dialog.getByText('我已了解等待期结束后数据无法恢复').click()
  await expect(dialog.getByRole('button', { name: '确认注销' })).toBeEnabled()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('account-deletion-confirmation.png'), fullPage: true })
})
