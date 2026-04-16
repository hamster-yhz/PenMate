import request from '@/utils/request'
import type { IdLike } from '@/api/types'

type AnyRecord = Record<string, unknown>

export const outlineApi = {
  listOutlineTree(projectId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/outlines/tree`)
  },
  createNode(projectId: IdLike, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/outlines/nodes`, payload)
  },
  updateNode(projectId: IdLike, nodeId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.put<AnyRecord>(`/v1/novels/${projectId}/outlines/nodes/${nodeId}?operatorId=${operatorId}`, payload)
  },
  moveNode(projectId: IdLike, nodeId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.patch<string>(`/v1/novels/${projectId}/outlines/nodes/${nodeId}/move?operatorId=${operatorId}`, payload)
  },
  deleteNode(projectId: IdLike, nodeId: IdLike, operatorId: IdLike) {
    return request.delete<string>(`/v1/novels/${projectId}/outlines/nodes/${nodeId}?operatorId=${operatorId}`)
  }
}

