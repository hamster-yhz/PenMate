import { reactive } from 'vue'

export interface SessionState {
  accessToken: string
  userId?: string
  userName?: string
  userEmail?: string
  permissionCodes?: string[]
}

const SESSION_KEY = 'penmate.session'

const state = reactive<SessionState>({
  accessToken: '',
})

const safeParse = (value: string | null): SessionState | null => {
  if (!value) return null
  try {
    const parsed = JSON.parse(value) as Partial<SessionState>
    if (!parsed || typeof parsed !== 'object') return null
    return {
      accessToken: '',
      userId: parsed.userId,
      userName: parsed.userName,
      userEmail: parsed.userEmail,
      permissionCodes: Array.isArray(parsed.permissionCodes)
        ? parsed.permissionCodes.filter((item): item is string => typeof item === 'string')
        : undefined,
    }
  } catch {
    return null
  }
}

export const restoreSession = () => {
  const restored = safeParse(localStorage.getItem(SESSION_KEY))
  if (restored) {
    Object.assign(state, restored)
  }
  return state
}

export const getSession = () => state

export const setSession = (patch: Partial<SessionState>) => {
  Object.assign(state, patch)
  const { userId, userName, userEmail, permissionCodes } = state
  localStorage.setItem(SESSION_KEY, JSON.stringify({ userId, userName, userEmail, permissionCodes }))
  return state
}

export const clearSession = () => {
  Object.assign(state, {
    accessToken: '',
    userId: undefined,
    userName: undefined,
    userEmail: undefined,
    permissionCodes: undefined,
  })
  localStorage.removeItem(SESSION_KEY)
}

export const expireSession = () => {
  clearSession()
  window.dispatchEvent(new Event('penmate:session-expired'))
}

restoreSession()
