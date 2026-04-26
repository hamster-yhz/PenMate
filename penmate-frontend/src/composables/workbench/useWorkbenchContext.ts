export const LAST_PROJECT_ID_KEY = 'penmate.lastProjectId'
export const LAST_OPERATOR_ID_KEY = 'penmate.lastOperatorId'

export interface WorkbenchContextQuery {
  projectId?: unknown
  operatorId?: unknown
  userId?: unknown
}

export interface WorkbenchContextSession {
  userId?: number
  userName?: string
  userEmail?: string
}

export interface WorkbenchContextOptions {
  query: WorkbenchContextQuery
  session: WorkbenchContextSession
}

const pickScalar = (value: unknown) => Array.isArray(value) ? value[0] : value

const toPositiveNumber = (value: unknown) => {
  const normalized = Number(pickScalar(value) ?? 0)
  return Number.isFinite(normalized) && normalized > 0 ? normalized : 0
}

const readStoredPositiveNumber = (storageKey: string) => {
  try {
    const cached = Number(window.localStorage.getItem(storageKey) || 0)
    return Number.isFinite(cached) && cached > 0 ? cached : 0
  } catch {
    return 0
  }
}

const writeStoredPositiveNumber = (storageKey: string, value: number) => {
  if (!Number.isFinite(value) || value <= 0) return
  try {
    window.localStorage.setItem(storageKey, String(value))
  } catch {
    // 忽略浏览器存储异常
  }
}

export const useWorkbenchContext = ({ query, session }: WorkbenchContextOptions) => {
  const username = typeof session.userName === 'string' ? session.userName : ''
  const userEmail = typeof session.userEmail === 'string' ? session.userEmail : ''

  const getCurrentProjectId = () => {
    const queryProjectId = toPositiveNumber(query.projectId)
    if (queryProjectId > 0) return queryProjectId
    return readStoredPositiveNumber(LAST_PROJECT_ID_KEY)
  }

  const resolveOperatorId = () => {
    const queryOperatorId = toPositiveNumber(query.operatorId) || toPositiveNumber(query.userId)
    if (queryOperatorId > 0) return queryOperatorId
    const sessionOperatorId = typeof session.userId === 'number' && session.userId > 0 ? session.userId : 0
    if (sessionOperatorId > 0) return sessionOperatorId
    const cachedOperatorId = readStoredPositiveNumber(LAST_OPERATOR_ID_KEY)
    return cachedOperatorId > 0 ? cachedOperatorId : null
  }

  const getContext = () => {
    const projectId = getCurrentProjectId()
    const operatorId = resolveOperatorId()
    writeStoredPositiveNumber(LAST_PROJECT_ID_KEY, projectId)
    if (operatorId) {
      writeStoredPositiveNumber(LAST_OPERATOR_ID_KEY, operatorId)
    }
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
