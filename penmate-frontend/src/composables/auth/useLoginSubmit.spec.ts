import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { loginMock, meMock, successMock, warningMock, errorMock } = vi.hoisted(() => ({
  loginMock: vi.fn(),
  meMock: vi.fn(),
  successMock: vi.fn(),
  warningMock: vi.fn(),
  errorMock: vi.fn(),
}))

vi.mock('@/api/modules/auth.api', () => ({
  authApi: {
    login: loginMock,
    me: meMock,
  },
}))

vi.mock('ant-design-vue', () => ({
  message: {
    success: successMock,
    warning: warningMock,
    error: errorMock,
  },
}))

import { clearSession, getSession } from '@/stores/session'
import { useLoginSubmit } from './useLoginSubmit'

describe('useLoginSubmit', () => {
  beforeEach(() => {
    loginMock.mockReset()
    meMock.mockReset()
    successMock.mockReset()
    warningMock.mockReset()
    errorMock.mockReset()
    localStorage.clear()
    clearSession()
  })

  it('returns_false_and_warns_when_username_or_password_missing', async () => {
    const { isLoading, submitLogin } = useLoginSubmit()

    const result = await submitLogin({
      username: '   ',
      password: '   ',
      remember: false,
    })

    expect(result).toBe(false)
    expect(loginMock).not.toHaveBeenCalled()
    expect(warningMock).toHaveBeenCalledWith('请输入账号与密码')
    expect(isLoading.value).toBe(false)
  })

  it('stores_session_and_returns_true_when_login_and_profile_succeed', async () => {
    loginMock.mockResolvedValue({
      accessToken: 'access-1',
      refreshToken: 'refresh-1',
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
      remember: false,
    })

    expect(result).toBe(true)
    expect(loginMock).toHaveBeenCalledWith({
      email: 'writer@example.com',
      password: '  pass123  ',
    })
    expect(getSession()).toMatchObject({
      accessToken: 'access-1',
      refreshToken: 'refresh-1',
      userId: '8',
      userEmail: 'writer@example.com',
      userName: 'Writer',
    })
    expect(successMock).toHaveBeenCalledWith('登录成功')
  })

  it('rolls_back_session_and_resets_loading_when_profile_request_fails', async () => {
    let resolveLogin: ((value: { accessToken: string; refreshToken: string }) => void) | undefined
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
      remember: false,
    })

    expect(isLoading.value).toBe(true)

    resolveLogin?.({ accessToken: 'access-2', refreshToken: 'refresh-2' })
    const result = await pending
    await nextTick()

    expect(result).toBe(false)
    expect(errorMock).toHaveBeenCalledWith('profile failed')
    expect(getSession()).toEqual({
      accessToken: '',
      refreshToken: '',
    })
    expect(isLoading.value).toBe(false)
  })
})
