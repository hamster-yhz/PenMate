import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { authApi } from '@/api/modules/auth.api'
import type { AuthSessionItem } from '@/entities/auth/model'
import { getErrorMessage } from '@/utils/errors'

export const useProfileSessions = () => {
  const authSessions = ref<AuthSessionItem[]>([])
  const authSessionsLoading = ref(false)
  const authSessionsError = ref('')
  const revokingSessionId = ref('')
  const revokingOtherSessions = ref(false)
  const authSessionsActionError = ref('')

  const loadAuthSessions = async () => {
    authSessionsLoading.value = true
    authSessionsError.value = ''
    authSessionsActionError.value = ''
    try {
      authSessions.value = await authApi.listSessions()
    } catch (error: unknown) {
      authSessionsError.value = error instanceof Error ? error.message : '加载设备失败'
    } finally {
      authSessionsLoading.value = false
    }
  }

  const revokeAuthSession = async (sessionId: string) => {
    if (revokingSessionId.value || revokingOtherSessions.value) return
    revokingSessionId.value = sessionId
    authSessionsActionError.value = ''
    try {
      await authApi.revokeSession(sessionId)
      authSessions.value = authSessions.value.filter((item) => item.sessionId !== sessionId)
      message.success('设备已退出')
    } catch (error: unknown) {
      authSessionsActionError.value = getErrorMessage(error, '退出设备失败')
    } finally {
      revokingSessionId.value = ''
    }
  }

  const revokeOtherAuthSessions = async () => {
    if (revokingSessionId.value || revokingOtherSessions.value) return
    const otherCount = authSessions.value.filter((item) => !item.current).length
    if (!otherCount) return
    revokingOtherSessions.value = true
    authSessionsActionError.value = ''
    try {
      const revoked = await authApi.revokeOtherSessions()
      authSessions.value = authSessions.value.filter((item) => item.current)
      message.success(`${revoked || otherCount} 台其他设备已退出`)
    } catch (error: unknown) {
      authSessionsActionError.value = getErrorMessage(error, '退出其他设备失败')
    } finally {
      revokingOtherSessions.value = false
    }
  }

  return {
    authSessions,
    authSessionsLoading,
    authSessionsError,
    revokingSessionId,
    revokingOtherSessions,
    authSessionsActionError,
    loadAuthSessions,
    revokeAuthSession,
    revokeOtherAuthSessions,
  }
}
