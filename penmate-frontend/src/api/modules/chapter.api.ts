import request from '@/utils/request'

type AnyRecord = Record<string, unknown>

export const chapterApi = {
  getChapter(projectId: string, chapterId: string) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/chapters/${chapterId}`)
  },
  updateChapter(projectId: string, chapterId: string, operatorId: string, payload: AnyRecord) {
    return request.put<AnyRecord>(`/v1/novels/${projectId}/chapters/${chapterId}?operatorId=${operatorId}`, payload)
  },
  listVersions(projectId: string, chapterId: string) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/chapters/${chapterId}/versions`)
  },
  createVersion(projectId: string, chapterId: string, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/chapters/${chapterId}/versions`, payload)
  },
  getVersion(projectId: string, chapterId: string, versionNo: string) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/chapters/${chapterId}/versions/${versionNo}`)
  },
  restoreVersion(projectId: string, chapterId: string, versionNo: string, operatorId: string) {
    return request.post<AnyRecord>(
      `/v1/novels/${projectId}/chapters/${chapterId}/versions/${versionNo}/restore?operatorId=${operatorId}`
    )
  },
  getVersionSnapshotUrl(projectId: string, chapterId: string, versionNo: string) {
    return request.get<Record<string, string>>(
      `/v1/novels/${projectId}/chapters/${chapterId}/versions/${versionNo}/snapshot-url`
    )
  },
  publishChapter(projectId: string, chapterId: string, operatorId: string) {
    return request.post<string>(`/v1/novels/${projectId}/chapters/${chapterId}/publish?operatorId=${operatorId}`)
  },
  getContentUploadUrl(projectId: string, chapterId: string) {
    return request.post<Record<string, string>>(`/v1/novels/${projectId}/chapters/${chapterId}/content-upload-url`)
  },
  commitContent(projectId: string, chapterId: string, operatorId: string, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/chapters/${chapterId}/content-commit?operatorId=${operatorId}`, payload)
  },
  getContentUrl(projectId: string, chapterId: string) {
    return request.get<Record<string, string>>(`/v1/novels/${projectId}/chapters/${chapterId}/content-url`)
  }
}
