import request from '@/utils/request'
import type { IdLike } from '@/api/types'

type AnyRecord = Record<string, unknown>

export const styleApi = {
  listStyles(projectId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/styles`)
  },
  getStyle(projectId: IdLike, styleId: IdLike) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/styles/${styleId}`)
  },
  createStyle(projectId: IdLike, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/styles`, payload)
  },
  updateStyle(projectId: IdLike, styleId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.put<AnyRecord>(`/v1/novels/${projectId}/styles/${styleId}?operatorId=${operatorId}`, payload)
  },
  deleteStyle(projectId: IdLike, styleId: IdLike, operatorId: IdLike) {
    return request.delete<string>(`/v1/novels/${projectId}/styles/${styleId}?operatorId=${operatorId}`)
  },
  switchStyle(projectId: IdLike, payload: AnyRecord) {
    return request.post<string>(`/v1/novels/${projectId}/styles/switch`, payload)
  },
  analyzeSample(projectId: IdLike, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/styles/analyze-sample`, payload)
  }
}

