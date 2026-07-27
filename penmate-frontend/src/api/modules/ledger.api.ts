import request from '@/utils/request'
import type { ProjectLedger } from '@/entities/ledger/model'

export const ledgerApi = {
  list(projectId: string) {
    return request.get<ProjectLedger[]>(`/projects/${projectId}/ledgers`)
  },
  read(projectId: string, ledgerId: string, offset: number, limit = 20_000) {
    return request.get<ProjectLedger>(`/projects/${projectId}/ledgers/${ledgerId}`, { params: { offset, limit } })
  },
  create(projectId: string, title: string, content = '') {
    return request.post<ProjectLedger>(`/projects/${projectId}/ledgers`, { title, content })
  },
  update(projectId: string, ledgerId: string, payload: {
    expectedRevision: string
    title?: string
    start?: number
    end?: number
    replacement?: string
  }) {
    return request.put<ProjectLedger>(`/projects/${projectId}/ledgers/${ledgerId}`, payload)
  },
  delete(projectId: string, ledgerId: string, expectedRevision: string) {
    return request.delete<string>(`/projects/${projectId}/ledgers/${ledgerId}`, { params: { expectedRevision } })
  },
}
