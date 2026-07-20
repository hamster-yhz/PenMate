import request from '@/utils/request'

type AnyRecord = Record<string, unknown>

export const outlineApi = {
  listOutlineTree(projectId: string) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/outlines/tree`)
  },
  createNode(projectId: string, _operatorId: string, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/outlines/nodes`, payload)
  },
  updateNode(projectId: string, nodeId: string, _operatorId: string, payload: AnyRecord) {
    return request.put<AnyRecord>(`/v1/novels/${projectId}/outlines/nodes/${nodeId}`, payload)
  },
  moveNode(projectId: string, nodeId: string, _operatorId: string, payload: AnyRecord) {
    return request.patch<string>(
      `/v1/novels/${projectId}/outlines/nodes/${nodeId}/move`,
      payload,
    )
  },
  deleteNode(projectId: string, nodeId: string, _operatorId: string) {
    void _operatorId
    return request.delete<string>(`/v1/novels/${projectId}/outlines/nodes/${nodeId}`)
  },
}
