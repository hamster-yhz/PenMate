import request from '@/utils/request'
import type { IdLike } from '@/api/types'

type AnyRecord = Record<string, unknown>

export const opsApi = {
  createMigration(payload: AnyRecord) {
    return request.post<AnyRecord>('/v1/migrations/content-to-object-storage', payload)
  },
  getMigration(migrationId: IdLike) {
    return request.get<AnyRecord>(`/v1/migrations/${migrationId}`)
  },
  listJobs() {
    return request.get<AnyRecord[]>('/v1/jobs')
  },
  getJob(jobId: IdLike) {
    return request.get<AnyRecord>(`/v1/jobs/${jobId}`)
  },
  retryJob(jobId: IdLike, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/jobs/${jobId}/retry`, payload)
  }
}

