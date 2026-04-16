import { createRouter, createWebHistory } from 'vue-router'

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
    path: '/domain-console',
    name: 'DomainConsole',
    component: () => import('@/views/DomainConsole/index.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
