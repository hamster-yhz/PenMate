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
        json: envelope({ id: '1001', email: 'writer@example.com', displayName: '测试作者', bio: '真实资料' }),
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

test('home opens the real login experience', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
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

test('cookie session recovery opens profile and logout ends the session', async ({ page }) => {
  await mockAuthenticatedApi(page)
  await page.goto('/profile')
  const profileCard = page.getByTestId('profile-hero-card')
  await expect(profileCard.getByText('测试作者')).toBeVisible()
  await expect(profileCard.getByText('writer@example.com')).toBeVisible()
  await page.getByRole('button', { name: '退出', exact: true }).click()
  await expect(page).toHaveURL(/\/login$/)
})

test('cookie session recovery opens the composed workbench', async ({ page }) => {
  await mockAuthenticatedApi(page)
  await page.goto('/workbench?projectId=2001')

  await expect(page.getByRole('group', { name: '工作模式' })).toBeVisible()
  await expect(page.getByRole('button', { name: '写作' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Story Bible' })).toBeVisible()
})
