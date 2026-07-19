export const LAST_PROJECT_ID_KEY = 'penmate.lastProjectId'
export const LAST_OPERATOR_ID_KEY = 'penmate.lastOperatorId'

export interface WorkbenchContextQuery {
  projectId?: unknown
  operatorId?: unknown
  userId?: unknown
}

export interface WorkbenchContextSession {
  userId?: string
  userName?: string
  userEmail?: string
}

export interface WorkbenchContextOptions {
  query: WorkbenchContextQuery
  session: WorkbenchContextSession
}

const pickScalar = (value: unknown) => (Array.isArray(value) ? value[0] : value)

const toBusinessId = (value: unknown) => {
  const normalized = String(pickScalar(value) ?? '').trim()
  return normalized || null
}

const readStoredBusinessId = (storageKey: string) => {
  try {
    const cached = String(window.localStorage.getItem(storageKey) ?? '').trim()
    return cached || null
  } catch {
    return null
  }
}

const writeStoredBusinessId = (storageKey: string, value: string | null) => {
  if (!value) return
  try {
    window.localStorage.setItem(storageKey, value)
  } catch {
    // 忽略浏览器存储异常
  }
}

export const useWorkbenchContext = ({ query, session }: WorkbenchContextOptions) => {
  const username = typeof session.userName === 'string' ? session.userName : ''
  const userEmail = typeof session.userEmail === 'string' ? session.userEmail : ''

  const getCurrentProjectId = () => {
    const queryProjectId = toBusinessId(query.projectId)
    if (queryProjectId) return queryProjectId
    return readStoredBusinessId(LAST_PROJECT_ID_KEY)
  }

  const resolveOperatorId = () => {
    const queryOperatorId = toBusinessId(query.operatorId)
    if (queryOperatorId) return queryOperatorId
    const sessionOperatorId = toBusinessId(session.userId)
    if (sessionOperatorId) return sessionOperatorId
    return readStoredBusinessId(LAST_OPERATOR_ID_KEY)
  }

  const getContext = () => {
    const projectId = getCurrentProjectId()
    const operatorId = resolveOperatorId()
    writeStoredBusinessId(LAST_PROJECT_ID_KEY, projectId)
    writeStoredBusinessId(LAST_OPERATOR_ID_KEY, operatorId)
    return {
      projectId,
      operatorId,
    }
  }

  const ensureContext = () => {
    const { projectId, operatorId } = getContext()
    return {
      projectId,
      operatorId,
      username,
      userEmail,
    }
  }

  const projectId = getCurrentProjectId()
  const operatorId = resolveOperatorId()

  return {
    projectId,
    operatorId,
    username,
    userEmail,
    ensureContext,
  }
}
