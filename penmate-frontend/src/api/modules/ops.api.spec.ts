import { describe, it, expect, vi, beforeEach } from 'vitest'

const { getMock, postMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  default: {
    get: getMock,
    post: postMock,
  },
}))

import { opsApi } from './ops.api'

describe('ops.api', () => {
  beforeEach(() => {
    getMock.mockReset()
    postMock.mockReset()
  })

  it('should_use_string_business_ids_for_ops_endpoints', async () => {
    getMock.mockResolvedValue({ ok: true })
    postMock.mockResolvedValue({ ok: true })

    await opsApi.getMigration('migration-1')
    await opsApi.getJob('job-1')
    await opsApi.retryJob('job-1', { operatorId: 'operator-1' })

    expect(getMock).toHaveBeenCalledWith('/v1/migrations/migration-1')
    expect(getMock).toHaveBeenCalledWith('/v1/jobs/job-1')
    expect(postMock).toHaveBeenCalledWith('/v1/jobs/job-1/retry', { operatorId: 'operator-1' })
  })

  it('should_reject_number_business_ids_at_compile_time', () => {
    // @ts-expect-error business IDs must be string-only
    const migrationId: string = 1
    // @ts-expect-error business IDs must be string-only
    const jobId: string = 1

    void opsApi.getMigration(migrationId)
    void opsApi.retryJob(jobId, {})
  })
})
