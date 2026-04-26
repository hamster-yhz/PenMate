import { describe, it, expect, vi, beforeEach } from 'vitest'

const { postMock, getMock } = vi.hoisted(() => ({
  postMock: vi.fn(),
  getMock: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  default: {
    post: postMock,
    get: getMock,
  },
}))

import { authApi, parseAuthErrorMessage } from './auth.api'

describe('auth.api', () => {
  beforeEach(() => {
    postMock.mockReset()
    getMock.mockReset()
  })

  it('should_call_login_endpoint_when_login_invoked', async () => {
    postMock.mockResolvedValue({ data: { accessToken: 't' } })

    await authApi.login({ email: 'u@x.com', password: 'p' })

    expect(postMock).toHaveBeenCalledWith('/v1/auth/login', {
      email: 'u@x.com',
      password: 'p',
    })
  })

  it('should_throw_error_when_request_post_rejected', async () => {
    const error = new Error('network failed')
    postMock.mockRejectedValue(error)

    await expect(authApi.login({ email: 'u@x.com', password: 'p' })).rejects.toThrow(
      'network failed'
    )
  })

  it('should_call_refresh_endpoint_when_refresh_invoked', async () => {
    postMock.mockResolvedValue({ data: { accessToken: 't2' } })

    await authApi.refresh({ refreshToken: 'r1' })

    expect(postMock).toHaveBeenCalledWith('/v1/auth/refresh', {
      refreshToken: 'r1',
    })
  })

  it('should_call_logout_endpoint_when_logout_invoked', async () => {
    postMock.mockResolvedValue('ok')

    await authApi.logout()

    expect(postMock).toHaveBeenCalledWith('/v1/auth/logout')
  })

  it('should_call_me_endpoint_when_me_invoked', async () => {
    getMock.mockResolvedValue({ id: 1, name: 'u' })

    await authApi.me()

    expect(getMock).toHaveBeenCalledWith('/v1/auth/me')
  })

  it('should_return_message_field_when_parse_auth_error_message_with_response_data_message', () => {
    const message = parseAuthErrorMessage({
      response: {
        data: {
          message: 'invalid credentials',
        },
      },
    })

    expect(message).toBe('invalid credentials')
  })

  it('should_trim_response_message_when_parse_auth_error_message_with_padded_message', () => {
    const message = parseAuthErrorMessage({
      response: {
        data: {
          message: '  invalid credentials  ',
        },
      },
    })

    expect(message).toBe('invalid credentials')
  })

  it('should_return_error_message_when_parse_auth_error_message_with_error_instance', () => {
    const message = parseAuthErrorMessage(new Error('timeout'))

    expect(message).toBe('timeout')
  })

  it('should_return_default_message_when_parse_auth_error_message_with_unknown_error', () => {
    const message = parseAuthErrorMessage({ reason: 'unknown' })

    expect(message).toBe('Authentication request failed')
  })
})
