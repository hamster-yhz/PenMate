import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { authApi } from '@/api/modules/auth.api'
import { clearSession, setSession } from '@/stores/session'

export interface LoginSubmitPayload {
  username: string
  password: string
  remember: boolean
}

export const useLoginSubmit = () => {
  const isLoading = ref(false)

  const submitLogin = async (payload: LoginSubmitPayload) => {
    const username = payload.username.trim()
    const password = payload.password.trim()

    if (!username || !password) {
      message.warning('请输入账号与密码')
      return false
    }

    isLoading.value = true
    try {
      const tokenData = await authApi.login({
        email: username,
        password: payload.password,
      })

      setSession({
        accessToken: String(tokenData?.accessToken || ''),
        refreshToken: String(tokenData?.refreshToken || ''),
      })

      const profile = await authApi.me()
      const uid = Number(profile.userId ?? profile.id ?? profile.uid ?? 0)
      const email = String(profile.email ?? profile.userEmail ?? username)
      const name = String(profile.displayName ?? profile.username ?? profile.name ?? '创作者')

      setSession({
        userId: Number.isFinite(uid) && uid > 0 ? uid : undefined,
        userEmail: email,
        userName: name,
      })

      message.success('登录成功')
      return true
    } catch (error: unknown) {
      clearSession()
      message.error(error instanceof Error ? error.message : '登录失败')
      return false
    } finally {
      isLoading.value = false
    }
  }

  return {
    isLoading,
    submitLogin,
  }
}
