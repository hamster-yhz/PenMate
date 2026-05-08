import request from '@/utils/request'

type AnyRecord = Record<string, unknown>

export const opsApi = {
  createMigration(payload: AnyRecord) {
    return request.post<AnyRecord>('/v1/migrations/content-to-object-storage', payload)
  },
  getMigration(migrationId: string) {
    return request.get<AnyRecord>(`/v1/migrations/${migrationId}`)
  },
  listJobs() {
    return request.get<AnyRecord[]>('/v1/jobs')
  },
  getJob(jobId: string) {
    return request.get<AnyRecord>(`/v1/jobs/${jobId}`)
  },
  retryJob(jobId: string, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/jobs/${jobId}/retry`, payload)
  }
}

