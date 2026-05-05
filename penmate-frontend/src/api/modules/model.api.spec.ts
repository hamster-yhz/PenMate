import { describe, it, expect, vi, beforeEach } from 'vitest'

const { getMock, postMock, putMock, patchMock, deleteMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  putMock: vi.fn(),
  patchMock: vi.fn(),
  deleteMock: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  default: {
    get: getMock,
    post: postMock,
    put: putMock,
    patch: patchMock,
    delete: deleteMock,
  },
}))

import { modelApi } from './model.api'

describe('model.api', () => {
  beforeEach(() => {
    getMock.mockReset()
    postMock.mockReset()
    putMock.mockReset()
    patchMock.mockReset()
    deleteMock.mockReset()
  })

  it('should_call_policy_list_endpoint_when_list_policies_invoked', async () => {
    getMock.mockResolvedValue([])

    await modelApi.listPolicies(99)

    expect(getMock).toHaveBeenCalledWith('/v1/novels/99/model-policies')
  })

  it('should_call_policy_create_endpoint_with_mapped_payload_when_create_policy_invoked', async () => {
    postMock.mockResolvedValue('ok')

    await modelApi.createPolicy(99, 7, {
      configName: '  default-policy  ',
      modelInput: 'gpt-4.1',
      providerModelId: 0,
      baseUrl: '  ',
    })

    expect(postMock).toHaveBeenCalledWith('/v1/novels/99/model-policies?operatorId=7', {
      configName: '  default-policy  ',
      policyName: '  default-policy  ',
      modelInput: 'gpt-4.1',
      modelName: 'gpt-4.1',
      providerModelId: undefined,
      baseUrl: undefined,
      scene: 'write',
    })
  })

  it('should_trim_model_name_and_base_url_when_create_config_invoked', async () => {
    postMock.mockResolvedValue('ok')

    await modelApi.createConfig(12, 7, {
      modelName: '  gpt-4.1-mini  ',
      baseUrl: '  https://api.example.com  ',
      scene: '',
    })

    expect(postMock).toHaveBeenCalledWith('/v1/novels/12/model-configs?operatorId=7', {
      modelName: 'gpt-4.1-mini',
      baseUrl: 'https://api.example.com',
      scene: 'write',
    })
  })

  it('should_normalize_payload_when_update_config_invoked', async () => {
    putMock.mockResolvedValue('ok')

    await modelApi.updateConfig(12, 3, 7, {
      modelInput: '  qwen-max  ',
      providerModelId: 0,
      baseUrl: '  ',
    })

    expect(putMock).toHaveBeenCalledWith('/v1/novels/12/model-configs/3?operatorId=7', {
      modelInput: '  qwen-max  ',
      modelName: 'qwen-max',
      providerModelId: undefined,
      baseUrl: undefined,
      scene: 'write',
    })
  })

  it('should_call_policy_update_endpoint_with_normalized_payload_when_update_policy_invoked', async () => {
    putMock.mockResolvedValue('ok')

    await modelApi.updatePolicy(12, 5, 7, {
      modelName: '  glm-4  ',
      baseUrl: '  ',
    })

    expect(putMock).toHaveBeenCalledWith('/v1/novels/12/model-policies/5?operatorId=7', {
      modelName: 'glm-4',
      baseUrl: undefined,
      scene: 'write',
    })
  })

  it('should_call_policy_default_endpoint_when_set_default_policy_invoked', async () => {
    postMock.mockResolvedValue('ok')

    await modelApi.setDefaultPolicy(99, 101, 7)

    expect(postMock).toHaveBeenCalledWith('/v1/novels/99/model-policies/101/set-default?operatorId=7')
  })

  it('should_throw_error_when_request_get_rejected', async () => {
    const error = new Error('network failed')
    getMock.mockRejectedValue(error)

    await expect(modelApi.listPolicies(99)).rejects.toThrow('network failed')
  })

  it('should_keep_endpoint_and_payload_mapping_when_create_policy_rejected', async () => {
    const error = new Error('create failed')
    postMock.mockRejectedValue(error)

    await expect(
      modelApi.createPolicy(99, 7, {
        configName: 'policy-b',
        modelInput: 'gpt-4.1',
      })
    ).rejects.toThrow('create failed')

    expect(postMock).toHaveBeenCalledWith('/v1/novels/99/model-policies?operatorId=7', {
      configName: 'policy-b',
      policyName: 'policy-b',
      modelInput: 'gpt-4.1',
      modelName: 'gpt-4.1',
      scene: 'write',
    })
  })

  it('should_call_user_model_preferences_detail_endpoint_when_get_user_model_preferences_invoked', async () => {
    getMock.mockResolvedValue({
      mainAgentModelConfigId: 9001,
      dirtyWorkAgentModelConfigId: 9002,
      candidateConfigs: [],
    })

    await modelApi.getUserModelPreferences(1001)

    expect(getMock).toHaveBeenCalledWith('/v1/model/preferences?userId=1001')
  })

  it('should_call_user_model_preferences_endpoint_when_save_user_model_preferences_invoked', async () => {
    postMock.mockResolvedValue('updated')

    await modelApi.saveUserModelPreferences(1001, 1001, {
      mainAgentModelConfigId: 9001,
      dirtyWorkAgentModelConfigId: 9002,
    })

    expect(postMock).toHaveBeenCalledWith('/v1/model/preferences?userId=1001&operatorId=1001', {
      mainAgentModelConfigId: 9001,
      dirtyWorkAgentModelConfigId: 9002,
    })
  })
})

