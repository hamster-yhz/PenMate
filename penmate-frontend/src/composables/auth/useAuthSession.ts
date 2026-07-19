import { authApi } from '@/api/modules/auth.api'
import { clearSession, getSession } from '@/stores/session'

let logoutPromise: Promise<void> | null = null

export const logoutCurrentSession = () => {
  if (logoutPromise) return logoutPromise

  logoutPromise = (async () => {
    try {
      if (getSession().accessToken) {
        await authApi.logout()
      }
    } finally {
      clearSession()
      logoutPromise = null
    }
  })()

  return logoutPromise
}
