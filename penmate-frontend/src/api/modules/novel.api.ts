import request from '@/utils/request'
import type { IdLike } from '@/api/types'

export type AnyRecord = Record<string, unknown>

export const novelApi = {
  listProjects() {
    return request.get<AnyRecord[]>('/v1/novels')
  },
  createProject(payload: AnyRecord) {
    return request.post<AnyRecord>('/v1/novels', payload)
  },
  getProject(projectId: IdLike) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}`)
  },
  updateProject(projectId: IdLike, payload: AnyRecord) {
    return request.put<AnyRecord>(`/v1/novels/${projectId}`, payload)
  },
  deleteProject(projectId: IdLike, operatorId: IdLike) {
    return request.delete<string>(`/v1/novels/${projectId}?operatorId=${operatorId}`)
  },
  listVolumes(projectId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/volumes`)
  },
  listMembers(projectId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/members`)
  },
  listChapters(projectId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/chapters`)
  }
}

