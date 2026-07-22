import { ref } from 'vue'
import { authApi } from '@/api/modules/auth.api'
import { clearSession, setSession } from '@/stores/session'

export interface LoginSubmitPayload {
  username: string
  password: string
}

export interface LoginSubmitResult {
  success: boolean
  error?: string
}

export const useLoginSubmit = () => {
  const isLoading = ref(false)

  const submitLogin = async (payload: LoginSubmitPayload) => {
    const username = payload.username.trim()
    const password = payload.password.trim()

    if (!username || !password) {
      return { success: false, error: '请输入邮箱和密码' } satisfies LoginSubmitResult
    }

    isLoading.value = true
    try {
      const tokenData = await authApi.login({
        email: username,
        password: payload.password,
      })

      setSession({
        accessToken: String(tokenData?.accessToken || ''),
      })

      const profile = await authApi.me()
      const uid = String(profile.userId ?? profile.id ?? profile.uid ?? '').trim()
      const email = String(profile.email ?? profile.userEmail ?? username)
      const name = String(profile.displayName ?? profile.username ?? profile.name ?? '创作者')

      setSession({
        userId: uid || undefined,
        userEmail: email,
        userName: name,
      })

      return { success: true } satisfies LoginSubmitResult
    } catch (error: unknown) {
      clearSession()
      return {
        success: false,
        error: error instanceof Error ? error.message : '登录失败',
      } satisfies LoginSubmitResult
    } finally {
      isLoading.value = false
    }
  }

  return {
    isLoading,
    submitLogin,
  }
}
