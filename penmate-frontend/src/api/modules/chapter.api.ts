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
  createVersion(projectId: IdLike, chapterId: IdLike, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/chapters/${chapterId}/versions`, payload)
  },
  getVersion(projectId: IdLike, chapterId: IdLike, versionNo: IdLike) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/chapters/${chapterId}/versions/${versionNo}`)
  },
  restoreVersion(projectId: IdLike, chapterId: IdLike, versionNo: IdLike, operatorId: IdLike) {
    return request.post<AnyRecord>(
      `/v1/novels/${projectId}/chapters/${chapterId}/versions/${versionNo}/restore?operatorId=${operatorId}`
    )
  },
  getVersionSnapshotUrl(projectId: IdLike, chapterId: IdLike, versionNo: IdLike) {
    return request.get<Record<string, string>>(
      `/v1/novels/${projectId}/chapters/${chapterId}/versions/${versionNo}/snapshot-url`
    )
  },
  publishChapter(projectId: IdLike, chapterId: IdLike, operatorId: IdLike) {
    return request.post<string>(`/v1/novels/${projectId}/chapters/${chapterId}/publish?operatorId=${operatorId}`)
  },
  getContentUploadUrl(projectId: IdLike, chapterId: IdLike) {
    return request.post<Record<string, string>>(`/v1/novels/${projectId}/chapters/${chapterId}/content-upload-url`)
  },
  commitContent(projectId: IdLike, chapterId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/chapters/${chapterId}/content-commit?operatorId=${operatorId}`, payload)
  },
  getContentUrl(projectId: IdLike, chapterId: IdLike) {
    return request.get<Record<string, string>>(`/v1/novels/${projectId}/chapters/${chapterId}/content-url`)
  }
}

