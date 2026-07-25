import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { authApi } from '@/api/modules/auth.api'
import { rbacApi } from '@/api/modules/rbac.api'
import { clearSession, getSession, setSession } from '@/stores/session'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/Home/index.vue'),
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login/index.vue'),
  },
  {
    path: '/mybooks',
    name: 'MyBooks',
    meta: { requiresAuth: true },
    component: () => import('@/views/MyBooks/index.vue'),
  },
  {
    path: '/profile',
    name: 'Profile',
    meta: { requiresAuth: true },
    component: () => import('@/views/Profile/index.vue'),
  },
  {
    path: '/projects/:projectId/settings',
    name: 'ProjectSettings',
    meta: { requiresAuth: true },
    component: () => import('@/views/ProjectSettings/index.vue'),
  },
  {
    path: '/projects/:projectId/print',
    name: 'ProjectPrint',
    meta: { requiresAuth: true },
    component: () => import('@/views/ProjectPrint/index.vue'),
  },
  {
    path: '/workbench',
    name: 'Workbench',
    meta: { requiresAuth: true },
    component: () => import('@/views/Workbench/index.vue'),
  },
  {
    path: '/admin',
    name: 'AdminOverview',
    meta: { requiresAuth: true, permission: '/admin', adminSection: 'overview' },
    component: () => import('@/views/Admin/index.vue'),
  },
  ...[
    ['models', 'AdminModels'],
    ['users', 'AdminUsers'],
    ['rbac', 'AdminRbac'],
    ['tasks', 'AdminTasks'],
    ['audit', 'AdminAudit'],
  ].map(([section, name]) => ({
    path: `/admin/${section}`,
    name,
    meta: { requiresAuth: true, permission: '/admin', adminSection: section },
    component: () => import('@/views/Admin/index.vue'),
  })),
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

let restorePromise: Promise<boolean> | null = null

const restoreSessionFromCookie = () => {
  if (getSession().accessToken) return Promise.resolve(true)
  if (restorePromise) return restorePromise

  restorePromise = (async () => {
    try {
      const tokens = await authApi.refresh()
      const accessToken = String(tokens?.accessToken || '')
      if (!accessToken) return false
      setSession({ accessToken })

      const profile = await authApi.me()
      setSession({
        userId: String(profile.userId ?? profile.id ?? '').trim() || undefined,
        userEmail: String(profile.email ?? '').trim() || undefined,
        userName: String(profile.displayName ?? profile.username ?? profile.name ?? '').trim() || undefined,
      })
      return true
    } catch {
      clearSession()
      return false
    } finally {
      restorePromise = null
    }
  })()

  return restorePromise
}

router.beforeEach(async (to) => {
  if (to.meta.requiresAuth && !getSession().accessToken) {
    const restored = await restoreSessionFromCookie()
    if (!restored) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }

  if (!to.path.startsWith('/admin')) return true

  const session = getSession()
  if (!session.userId) return { path: '/login', query: { redirect: to.fullPath } }

  try {
    const menus = await rbacApi.listProfileMenus(session.userId)
    const canAccess = (menus || []).some((menu) =>
      String((menu as Record<string, unknown>)?.path || '').startsWith('/admin'),
    )
    return canAccess ? true : '/mybooks'
  } catch {
    return '/mybooks'
  }
})

export default router
