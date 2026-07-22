import { beforeEach, describe, expect, it, vi } from 'vitest'

const { listSessionsMock, revokeSessionMock, revokeOtherSessionsMock, successMock } = vi.hoisted(() => ({
  listSessionsMock: vi.fn(),
  revokeSessionMock: vi.fn(),
  revokeOtherSessionsMock: vi.fn(),
  successMock: vi.fn(),
}))

vi.mock('@/api/modules/auth.api', () => ({
  authApi: {
    listSessions: listSessionsMock,
    revokeSession: revokeSessionMock,
    revokeOtherSessions: revokeOtherSessionsMock,
  },
}))

vi.mock('ant-design-vue', () => ({
  message: { success: successMock },
}))

const sessions = [
  {
    sessionId: 'current-session',
    deviceName: 'Desktop',
    browserName: 'Chrome',
    operatingSystem: 'Windows',
    ipAddress: '127.0.0.1',
    current: true,
  },
  {
    sessionId: 'other-session',
    deviceName: 'Mobile',
    browserName: 'Safari',
    operatingSystem: 'iOS',
    ipAddress: '10.0.0.8',
    current: false,
  },
]

describe('useProfileSessions', () => {
  beforeEach(() => {
    listSessionsMock.mockReset()
    revokeSessionMock.mockReset()
    revokeOtherSessionsMock.mockReset()
    successMock.mockReset()
  })

  it('revokes all non-current sessions and preserves the current session', async () => {
    listSessionsMock.mockResolvedValue(sessions)
    revokeOtherSessionsMock.mockResolvedValue(1)
    const { useProfileSessions } = await import('./useProfileSessions')
    const state = useProfileSessions()
    await state.loadAuthSessions()

    await state.revokeOtherAuthSessions()

    expect(revokeOtherSessionsMock).toHaveBeenCalledOnce()
    expect(state.authSessions.value).toEqual([sessions[0]])
    expect(state.authSessionsActionError.value).toBe('')
    expect(successMock).toHaveBeenCalledWith('1 台其他设备已退出')
  })

  it('keeps sessions visible and exposes a persistent error when bulk revocation fails', async () => {
    listSessionsMock.mockResolvedValue(sessions)
    revokeOtherSessionsMock.mockRejectedValue(new Error('网络不可用'))
    const { useProfileSessions } = await import('./useProfileSessions')
    const state = useProfileSessions()
    await state.loadAuthSessions()

    await state.revokeOtherAuthSessions()

    expect(state.authSessions.value).toEqual(sessions)
    expect(state.authSessionsActionError.value).toBe('网络不可用')
    expect(state.revokingOtherSessions.value).toBe(false)
    expect(successMock).not.toHaveBeenCalled()
  })

  it('does not call the API when only the current session exists', async () => {
    listSessionsMock.mockResolvedValue([sessions[0]])
    const { useProfileSessions } = await import('./useProfileSessions')
    const state = useProfileSessions()
    await state.loadAuthSessions()

    await state.revokeOtherAuthSessions()

    expect(revokeOtherSessionsMock).not.toHaveBeenCalled()
  })
})
