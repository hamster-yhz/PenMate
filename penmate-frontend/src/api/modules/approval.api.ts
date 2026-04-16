import request from '@/utils/request'
import type { IdLike } from '@/api/types'

type AnyRecord = Record<string, unknown>

export const approvalApi = {
  listApprovals(projectId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/approvals`)
  },
  getApproval(projectId: IdLike, approvalId: IdLike) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/approvals/${approvalId}`)
  },
  approve(projectId: IdLike, approvalId: IdLike, payload: AnyRecord) {
    return request.post<string>(`/v1/novels/${projectId}/approvals/${approvalId}/approve`, payload)
  },
  reject(projectId: IdLike, approvalId: IdLike, payload: AnyRecord) {
    return request.post<string>(`/v1/novels/${projectId}/approvals/${approvalId}/reject`, payload)
  }
}

