import { createRouter, createWebHistory } from 'vue-router'
import { rbacApi } from '@/api/modules/rbac.api'
import { getSession } from '@/stores/session'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/Home/index.vue')
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login/index.vue')
  },
  {
    path: '/mybooks',
    name: 'MyBooks',
    component: () => import('@/views/MyBooks/index.vue')
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/Profile/index.vue')
  },
  {
    path: '/workbench',
    name: 'Workbench',
    component: () => import('@/views/Workbench/index.vue')
  },
  {
    path: '/admin/rbac',
    name: 'AdminRbac',
    component: () => import('@/views/AdminRbac/index.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  if (to.path !== '/admin/rbac') {
    return true
  }

  const session = getSession()
  if (!session.userId) {
    return '/login'
  }

  try {
    const menus = await rbacApi.listProfileMenus(session.userId)
    const canAccess = (menus || []).some(
      (menu) => String((menu as Record<string, unknown>)?.path || '') === '/admin/rbac'
    )
    return canAccess ? true : '/mybooks'
  } catch {
    return '/mybooks'
  }
})

export default router
