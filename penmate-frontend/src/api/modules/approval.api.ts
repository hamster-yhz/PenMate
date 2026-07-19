import request from '@/utils/request'

type AnyRecord = Record<string, unknown>

export const approvalApi = {
  listApprovals(projectId: string) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/approvals`)
  },
  getApproval(projectId: string, approvalId: string) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/approvals/${approvalId}`)
  },
  approve(projectId: string, approvalId: string, payload: AnyRecord) {
    return request.post<string>(`/v1/novels/${projectId}/approvals/${approvalId}/approve`, payload)
  },
  reject(projectId: string, approvalId: string, payload: AnyRecord) {
    return request.post<string>(`/v1/novels/${projectId}/approvals/${approvalId}/reject`, payload)
  },
}
