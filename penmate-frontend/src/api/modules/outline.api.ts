import request from '@/utils/request'

type AnyRecord = Record<string, unknown>

export const outlineApi = {
  listOutlineTree(projectId: string) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/outlines/tree`)
  },
  createNode(projectId: string, operatorId: string, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/outlines/nodes?operatorId=${operatorId}`, payload)
  },
  updateNode(projectId: string, nodeId: string, operatorId: string, payload: AnyRecord) {
    return request.put<AnyRecord>(`/v1/novels/${projectId}/outlines/nodes/${nodeId}?operatorId=${operatorId}`, payload)
  },
  moveNode(projectId: string, nodeId: string, operatorId: string, payload: AnyRecord) {
    return request.patch<string>(
      `/v1/novels/${projectId}/outlines/nodes/${nodeId}/move?operatorId=${operatorId}`,
      payload,
    )
  },
  deleteNode(projectId: string, nodeId: string, operatorId: string) {
    return request.delete<string>(`/v1/novels/${projectId}/outlines/nodes/${nodeId}?operatorId=${operatorId}`)
  },
}
