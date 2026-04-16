import request from '@/utils/request'
import type { IdLike } from '@/api/types'

type AnyRecord = Record<string, unknown>

export const chapterApi = {
  getChapter(projectId: IdLike, chapterId: IdLike) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/chapters/${chapterId}`)
  },
  updateChapter(projectId: IdLike, chapterId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.put<AnyRecord>(`/v1/novels/${projectId}/chapters/${chapterId}?operatorId=${operatorId}`, payload)
  },
  listVersions(projectId: IdLike, chapterId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/chapters/${chapterId}/versions`)
  },
  publishChapter(projectId: IdLike, chapterId: IdLike, operatorId: IdLike) {
    return request.post<string>(`/v1/novels/${projectId}/chapters/${chapterId}/publish?operatorId=${operatorId}`)
  },
  getContentUrl(projectId: IdLike, chapterId: IdLike) {
    return request.get<Record<string, string>>(`/v1/novels/${projectId}/chapters/${chapterId}/content-url`)
  }
}

