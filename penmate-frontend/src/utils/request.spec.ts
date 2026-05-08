import { beforeEach, describe, expect, it, vi } from 'vitest'

type AxiosHandler = (url: string, data?: unknown, config?: unknown) => Promise<{ data: unknown }>
type AxiosFactory = {
  get: ReturnType<typeof vi.fn<AxiosHandler>>
  post: ReturnType<typeof vi.fn<AxiosHandler>>
  put: ReturnType<typeof vi.fn<AxiosHandler>>
  patch: ReturnType<typeof vi.fn<AxiosHandler>>
  delete: ReturnType<typeof vi.fn<AxiosHandler>>
  interceptors: {
    request: { use: ReturnType<typeof vi.fn> }
    response: { use: ReturnType<typeof vi.fn> }
  }
  defaults: {
    transformResponse?: Array<(data: unknown) => unknown>
  }
}

const axiosInstance: AxiosFactory = {
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  patch: vi.fn(),
  delete: vi.fn(),
  interceptors: {
    request: { use: vi.fn() },
    response: { use: vi.fn() },
  },
  defaults: {},
}

const axiosCreate = vi.fn((config?: { transformResponse?: Array<(data: unknown) => unknown> }) => {
  axiosInstance.defaults.transformResponse = config?.transformResponse
  return axiosInstance
})

vi.mock('axios', () => ({
  default: {
    create: axiosCreate,
  },
  create: axiosCreate,
}))

vi.mock('@/stores/session', () => ({
  clearSession: vi.fn(),
  getSession: vi.fn(() => ({ accessToken: '', refreshToken: '' })),
  setSession: vi.fn(),
}))

const applyTransformResponse = async (payload: unknown) => {
  const request = (await import('./request')).default
  void request
  const transformResponse = axiosInstance.defaults.transformResponse || []
  return transformResponse.reduce<unknown>((current, transform) => transform(current), payload)
}

describe('request', () => {
  beforeEach(() => {
    vi.resetModules()
    axiosCreate.mockClear()
    axiosInstance.get.mockReset()
    axiosInstance.post.mockReset()
    axiosInstance.put.mockReset()
    axiosInstance.patch.mockReset()
    axiosInstance.delete.mockReset()
    axiosInstance.interceptors.request.use.mockReset()
    axiosInstance.interceptors.response.use.mockReset()
    axiosInstance.defaults = {}
  })

  it('keeps_oversized_session_id_precise_when_unwrapping_api_response', async () => {
    const oversizedSessionId = '2052639275832553472'
    const envelopeJson = `{"data":[{"sessionId":${oversizedSessionId},"title":"超大会话","updatedAt":"2026-05-08 14:00:00"}]}`
    axiosInstance.get.mockImplementation(async () => {
      const transformed = await applyTransformResponse(envelopeJson)
      return { data: transformed }
    })

    const request = (await import('./request')).default
    const result = await request.get<Array<Record<string, unknown>>>('/v1/novels/920001/agent/sessions')

    expect(axiosCreate).toHaveBeenCalledTimes(1)
    expect(Array.isArray(result)).toBe(true)
    expect(result).toEqual([
      {
        sessionId: oversizedSessionId,
        title: '超大会话',
        updatedAt: '2026-05-08 14:00:00',
      },
    ])
  })

  it('keeps_safe_integers_as_numbers_and_unsafe_integers_as_exact_strings', async () => {
    const transformed = await applyTransformResponse('{"data":{"safeId":920001,"unsafeId":2052639275832553472}}')

    expect(transformed).toEqual({
      data: {
        safeId: 920001,
        unsafeId: '2052639275832553472',
      },
    })
  })

  it('does_not_convert_quoted_business_ids_into_numbers', async () => {
    const transformed = await applyTransformResponse('{"data":{"sessionId":"2052639275832553472","taskId":"2052639275832553999","safeId":920001}}')

    expect(transformed).toEqual({
      data: {
        sessionId: '2052639275832553472',
        taskId: '2052639275832553999',
        safeId: 920001,
      },
    })
  })

  it('keeps_decimal_and_scientific_numbers_as_numbers_when_they_are_safe', async () => {
    const transformed = await applyTransformResponse('{"data":{"ratio":0.125,"scientific":1.23e5}}')

    expect(transformed).toEqual({
      data: {
        ratio: 0.125,
        scientific: 123000,
      },
    })
  })

  it('passes_through_blank_and_non_json_payloads_unchanged', async () => {
    await expect(applyTransformResponse('')).resolves.toBe('')
    await expect(applyTransformResponse('plain text payload')).resolves.toBe('plain text payload')
    await expect(applyTransformResponse(undefined)).resolves.toBe(undefined)
  })

  it('preserves_mixed_nested_numeric_types_inside_envelope_objects', async () => {
    const transformed = await applyTransformResponse(
      '{"data":{"session":{"sessionId":2052639275832553472,"title":"超大会话"},"activeTask":{"taskId":77,"version":3},"items":[{"messageId":2052639275832553999},{"messageId":12}]}}'
    )

    expect(transformed).toEqual({
      data: {
        session: {
          sessionId: '2052639275832553472',
          title: '超大会话',
        },
        activeTask: {
          taskId: 77,
          version: 3,
        },
        items: [
          { messageId: '2052639275832553999' },
          { messageId: 12 },
        ],
      },
    })
  })
})
