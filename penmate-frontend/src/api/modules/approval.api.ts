import request from '@/utils/request'

type AnyRecord = Record<string, unknown>

const reviewPayload = (payload: AnyRecord) => {
  const result = { ...payload }
  delete result.reviewedBy
  return result
}

export const approvalApi = {
  listApprovals(projectId: string) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/approvals`)
  },
  getApproval(projectId: string, approvalId: string) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/approvals/${approvalId}`)
  },
  approve(projectId: string, approvalId: string, payload: AnyRecord) {
    return request.post<string>(`/v1/novels/${projectId}/approvals/${approvalId}/approve`, reviewPayload(payload))
  },
  reject(projectId: string, approvalId: string, payload: AnyRecord) {
    return request.post<string>(`/v1/novels/${projectId}/approvals/${approvalId}/reject`, reviewPayload(payload))
  },
}
