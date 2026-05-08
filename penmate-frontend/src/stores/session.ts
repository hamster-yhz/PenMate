export interface SessionState {
  accessToken: string
  refreshToken: string
  userId?: string
  userName?: string
  userEmail?: string
}

const SESSION_KEY = 'penmate.session'

let state: SessionState = {
  accessToken: '',
  refreshToken: ''
}

const safeParse = (value: string | null): SessionState | null => {
  if (!value) return null
  try {
    const parsed = JSON.parse(value) as SessionState
    if (!parsed || typeof parsed !== 'object') return null
    return {
      accessToken: parsed.accessToken || '',
      refreshToken: parsed.refreshToken || '',
      userId: parsed.userId,
      userName: parsed.userName,
      userEmail: parsed.userEmail
    }
  } catch {
    return null
  }
}

export const restoreSession = () => {
  const restored = safeParse(localStorage.getItem(SESSION_KEY))
  if (restored) {
    state = restored
  }
  return state
}

export const getSession = () => state

export const setSession = (patch: Partial<SessionState>) => {
  state = {
    ...state,
    ...patch
  }
  localStorage.setItem(SESSION_KEY, JSON.stringify(state))
  return state
}

export const clearSession = () => {
  state = {
    accessToken: '',
    refreshToken: ''
  }
  localStorage.removeItem(SESSION_KEY)
}

restoreSession()

