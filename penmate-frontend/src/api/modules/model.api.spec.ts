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

  it('should_call_user_model_config_list_endpoint_when_list_user_model_configs_invoked', async () => {
    getMock.mockResolvedValue([])

    await modelApi.listUserModelConfigs(99)

    expect(getMock).toHaveBeenCalledWith('/v1/model/configs?userId=99')
  })

  it('should_keep_only_string_provider_id_entries_without_falling_back_to_physical_id', async () => {
    getMock.mockResolvedValue([
      { providerId: '', displayName: 'Invalid Empty' },
      { providerId: '   ', displayName: 'Invalid Blank' },
      { id: 1, displayName: 'OpenAI Legacy' },
      { providerId: 'provider-qwen-9007199254740993', displayName: 'Qwen', id: 20002 },
    ])

    const result = await modelApi.listProviders()

    expect(getMock).toHaveBeenCalledWith('/v1/model/providers')
    expect(result).toEqual([{ providerId: 'provider-qwen-9007199254740993', displayName: 'Qwen' }])
    expect(result[0]).not.toHaveProperty('id')
  })

  it('should_use_string_business_model_config_id_in_update_and_delete_endpoints_without_leaking_physical_or_duplicate_ids_in_payload', async () => {
    putMock.mockResolvedValue('ok')
    deleteMock.mockResolvedValue('deleted')

    const businessId = 'mcfg-900719925474099312345'
    const physicalId = 77

    await modelApi.updateUserModelConfig(12, businessId, 7, {
      id: physicalId,
      modelConfigId: businessId,
      modelName: '  qwen-plus  ',
    })
    await modelApi.deleteUserModelConfig(12, businessId, 7)

    expect(putMock).toHaveBeenCalledWith('/v1/model/configs/mcfg-900719925474099312345?userId=12&operatorId=7', {
      modelName: 'qwen-plus',
    })
    expect(putMock.mock.calls[0][1]).not.toHaveProperty('id')
    expect(putMock.mock.calls[0][1]).not.toHaveProperty('modelConfigId')
    expect(deleteMock).toHaveBeenCalledWith('/v1/model/configs/mcfg-900719925474099312345?userId=12&operatorId=7')
  })

  it('should_call_user_model_config_create_endpoint_with_string_business_ids_and_direct_key_value_payload', async () => {
    postMock.mockResolvedValue('ok')

    await modelApi.createUserModelConfig(99, 7, {
      providerId: 'provider-openai-900719925474099312345',
      modelName: '  gpt-4.1  ',
      baseUrl: '  https://api.example.com  ',
      modelCategory: '  USER_MODEL  ',
      apiKey: '  sk-direct-user-key  ',
      officialKeyId: 'ok-9001',
      userKeyId: 'uk-8001',
      keyName: 'legacy-name',
      selectedKeyId: '9001',
      status: '  active  ',
    })

    expect(postMock).toHaveBeenCalledWith('/v1/model/configs?userId=99&operatorId=7', {
      providerId: 'provider-openai-900719925474099312345',
      modelName: 'gpt-4.1',
      baseUrl: 'https://api.example.com',
      modelCategory: 'USER_MODEL',
      apiKey: 'sk-direct-user-key',
      status: 'active',
    })
    expect(postMock.mock.calls[0][1]).not.toHaveProperty('userKeyId')
    expect(postMock.mock.calls[0][1]).not.toHaveProperty('officialKeyId')
    expect(postMock.mock.calls[0][1]).not.toHaveProperty('keyName')
    expect(postMock.mock.calls[0][1]).not.toHaveProperty('selectedKeyId')
  })

  it('should_map_legacy_key_source_type_to_model_category_when_create_user_model_config_called_with_string_provider_id', async () => {
    postMock.mockResolvedValue('ok')

    await modelApi.createUserModelConfig(99, 7, {
      providerId: 'provider-openai-900719925474099312345',
      modelName: '  gpt-4.1  ',
      keySourceType: 'OFFICIAL_KEY',
      apiKey: '  sk-direct-official-key  ',
    })

    expect(postMock).toHaveBeenCalledWith('/v1/model/configs?userId=99&operatorId=7', {
      providerId: 'provider-openai-900719925474099312345',
      modelName: 'gpt-4.1',
      modelCategory: 'OFFICIAL_MODEL',
      apiKey: 'sk-direct-official-key',
    })
    expect(postMock.mock.calls[0][1]).not.toHaveProperty('keySourceType')
  })

  it('should_call_user_model_config_update_endpoint_with_string_business_ids_and_direct_key_value_payload', async () => {
    putMock.mockResolvedValue('ok')

    await modelApi.updateUserModelConfig(12, 'mcfg-3', 7, {
      providerId: 'provider-qwen-3',
      modelName: '  qwen-max  ',
      baseUrl: '  ',
      modelCategory: '  OFFICIAL_MODEL ',
      apiKey: '  sk-direct-official-key  ',
      userKeyId: '0',
      officialKeyId: '9002',
      keyName: 'Official Config',
    })

    expect(putMock).toHaveBeenCalledWith('/v1/model/configs/mcfg-3?userId=12&operatorId=7', {
      providerId: 'provider-qwen-3',
      modelName: 'qwen-max',
      baseUrl: undefined,
      modelCategory: 'OFFICIAL_MODEL',
      apiKey: 'sk-direct-official-key',
    })
    expect(putMock.mock.calls[0][1]).not.toHaveProperty('userKeyId')
    expect(putMock.mock.calls[0][1]).not.toHaveProperty('officialKeyId')
    expect(putMock.mock.calls[0][1]).not.toHaveProperty('keyName')
  })

  it('should_not_silently_drop_non_blank_string_provider_id_when_create_or_update_user_model_config', async () => {
    postMock.mockResolvedValue('ok')
    putMock.mockResolvedValue('ok')

    await modelApi.createUserModelConfig(99, 7, {
      providerId: 'provider-zero-like',
      modelName: 'gpt-4.1',
      modelCategory: 'USER_MODEL',
      apiKey: 'sk-create',
    })
    await modelApi.updateUserModelConfig(99, 'mcfg-9001', 7, {
      providerId: 'provider-negative-like',
      modelName: 'gpt-4.1',
      modelCategory: 'OFFICIAL_MODEL',
      apiKey: 'sk-update',
    })

    expect(postMock).toHaveBeenCalledWith('/v1/model/configs?userId=99&operatorId=7', {
      providerId: 'provider-zero-like',
      modelName: 'gpt-4.1',
      modelCategory: 'USER_MODEL',
      apiKey: 'sk-create',
    })
    expect(putMock).toHaveBeenCalledWith('/v1/model/configs/mcfg-9001?userId=99&operatorId=7', {
      providerId: 'provider-negative-like',
      modelName: 'gpt-4.1',
      modelCategory: 'OFFICIAL_MODEL',
      apiKey: 'sk-update',
    })
  })

  it('should_call_user_model_config_delete_endpoint_when_delete_user_model_config_invoked_with_string_business_id', async () => {
    deleteMock.mockResolvedValue('deleted')

    await modelApi.deleteUserModelConfig(12, 'mcfg-3', 7)

    expect(deleteMock).toHaveBeenCalledWith('/v1/model/configs/mcfg-3?userId=12&operatorId=7')
  })

  it('should_call_user_model_preferences_detail_endpoint_when_get_user_model_preferences_invoked', async () => {
    getMock.mockResolvedValue({
      mainAgentModelConfigId: 'mcfg-9001',
      dirtyWorkAgentModelConfigId: 'mcfg-9002',
      candidateConfigs: [],
    })

    await modelApi.getUserModelPreferences(1001)

    expect(getMock).toHaveBeenCalledWith('/v1/model/preferences?userId=1001')
  })

  it('should_call_user_model_preferences_endpoint_with_string_assignment_fields_even_when_one_side_is_empty', async () => {
    postMock.mockResolvedValue('updated')

    await modelApi.saveUserModelPreferences(1001, 1001, {
      mainAgentModelConfigId: 'mcfg-900719925474099312345',
      dirtyWorkAgentModelConfigId: undefined,
      extraField: 'legacy',
    })

    expect(postMock).toHaveBeenCalledWith('/v1/model/preferences?userId=1001&operatorId=1001', {
      mainAgentModelConfigId: 'mcfg-900719925474099312345',
      dirtyWorkAgentModelConfigId: null,
    })
  })

  it('should_preserve_oversized_string_business_ids_in_model_preference_payload_without_numeric_coercion', async () => {
    postMock.mockResolvedValue('updated')

    await modelApi.saveUserModelPreferences(1001, 1001, {
      mainAgentModelConfigId: '205172327654749798400000-main',
      dirtyWorkAgentModelConfigId: '205172327654749798400000-dirty',
    })

    expect(postMock).toHaveBeenCalledWith('/v1/model/preferences?userId=1001&operatorId=1001', {
      mainAgentModelConfigId: '205172327654749798400000-main',
      dirtyWorkAgentModelConfigId: '205172327654749798400000-dirty',
    })
  })
})
