import { createApp } from 'vue'
import './style.css'
import App from './App.vue'
import router from './router'
import { reportFrontendError } from '@/utils/telemetry'

const app = createApp(App)

app.use(router)
app.config.errorHandler = (error, instance) => {
  reportFrontendError(error, {
    source: 'vue',
    component: instance?.$options.name || instance?.$options.__name,
  })
}
window.addEventListener('error', (event) => reportFrontendError(event.error || event.message, { source: 'window' }))
window.addEventListener('unhandledrejection', (event) => reportFrontendError(event.reason, { source: 'promise' }))
window.addEventListener('penmate:session-expired', () => {
  if (router.currentRoute.value.path !== '/login') {
    void router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
  }
})
app.mount('#app')
