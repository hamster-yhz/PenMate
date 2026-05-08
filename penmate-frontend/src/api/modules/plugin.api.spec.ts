import { describe, it, expect, vi, beforeEach } from 'vitest'

const { getMock, postMock, patchMock, deleteMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  patchMock: vi.fn(),
  deleteMock: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  default: {
    get: getMock,
    post: postMock,
    patch: patchMock,
    delete: deleteMock,
  },
}))

import { pluginApi } from './plugin.api'

describe('plugin.api', () => {
  beforeEach(() => {
    getMock.mockReset()
    postMock.mockReset()
    patchMock.mockReset()
    deleteMock.mockReset()
  })

  it('should_use_string_business_ids_for_project_plugin_endpoints', async () => {
    getMock.mockResolvedValue([])
    postMock.mockResolvedValue({ ok: true })
    patchMock.mockResolvedValue({ ok: true })
    deleteMock.mockResolvedValue({ ok: true })

    await pluginApi.listProjectPlugins('project-1')
    await pluginApi.installPlugin('project-1', 'operator-1', { pluginCode: 'plugin-a' })
    await pluginApi.updateInstall('project-1', 'plugin-a', 'operator-1', { enabled: true })
    await pluginApi.deleteInstall('project-1', 'plugin-a', 'operator-1')

    expect(getMock).toHaveBeenCalledWith('/v1/novels/project-1/plugins')
    expect(postMock).toHaveBeenCalledWith('/v1/novels/project-1/plugins/install?operatorId=operator-1', { pluginCode: 'plugin-a' })
    expect(patchMock).toHaveBeenCalledWith('/v1/novels/project-1/plugins/plugin-a?operatorId=operator-1', { enabled: true })
    expect(deleteMock).toHaveBeenCalledWith('/v1/novels/project-1/plugins/plugin-a?operatorId=operator-1')
  })

  it('should_reject_number_business_ids_at_compile_time', () => {
    // @ts-expect-error business IDs must be string-only
    pluginApi.listProjectPlugins(1)
    // @ts-expect-error business IDs must be string-only
    pluginApi.installPlugin('project-1', 1, { pluginCode: 'plugin-a' })
  })
})
