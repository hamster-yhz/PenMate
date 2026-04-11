import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/Home/index.vue')
  },
  {
    path: '/workbench',
    name: 'Workbench',
    component: () => import('@/views/Workbench/index.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router

