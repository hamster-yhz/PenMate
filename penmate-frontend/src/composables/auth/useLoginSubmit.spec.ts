import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { loginMock, meMock } = vi.hoisted(() => ({
  loginMock: vi.fn(),
  meMock: vi.fn(),
}))

vi.mock('@/api/modules/auth.api', () => ({
  authApi: {
    login: loginMock,
    me: meMock,
  },
}))

import { clearSession, getSession } from '@/stores/session'
import { useLoginSubmit } from './useLoginSubmit'

describe('useLoginSubmit', () => {
  beforeEach(() => {
    loginMock.mockReset()
    meMock.mockReset()
    localStorage.clear()
    clearSession()
  })

  it('returns_a_field_error_when_username_or_password_is_missing', async () => {
    const { isLoading, submitLogin } = useLoginSubmit()

    const result = await submitLogin({
      username: '   ',
      password: '   ',
    })

    expect(result).toEqual({ success: false, error: '请输入邮箱和密码' })
    expect(loginMock).not.toHaveBeenCalled()
    expect(isLoading.value).toBe(false)
  })

  it('stores_session_and_returns_success_when_login_and_profile_succeed', async () => {
    loginMock.mockResolvedValue({
      accessToken: 'access-1',
    })
    meMock.mockResolvedValue({
      userId: 8,
      email: 'writer@example.com',
      username: 'Writer',
    })

    const { submitLogin } = useLoginSubmit()

    const result = await submitLogin({
      username: ' writer@example.com ',
      password: '  pass123  ',
    })

    expect(result).toEqual({ success: true })
    expect(loginMock).toHaveBeenCalledWith({
      email: 'writer@example.com',
      password: '  pass123  ',
    })
    expect(getSession()).toMatchObject({
      accessToken: 'access-1',
      userId: '8',
      userEmail: 'writer@example.com',
      userName: 'Writer',
    })
  })

  it('rolls_back_session_and_resets_loading_when_profile_request_fails', async () => {
    let resolveLogin: ((value: { accessToken: string }) => void) | undefined
    loginMock.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveLogin = resolve
        }),
    )
    meMock.mockRejectedValue(new Error('profile failed'))

    const { isLoading, submitLogin } = useLoginSubmit()
    const pending = submitLogin({
      username: 'writer@example.com',
      password: 'pass123',
    })

    expect(isLoading.value).toBe(true)

    resolveLogin?.({ accessToken: 'access-2' })
    const result = await pending
    await nextTick()

    expect(result).toEqual({ success: false, error: 'profile failed' })
    expect(getSession()).toEqual({
      accessToken: '',
      userId: undefined,
      userName: undefined,
      userEmail: undefined,
    })
    expect(isLoading.value).toBe(false)
  })
})
