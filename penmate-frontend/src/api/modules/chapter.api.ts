import request from '@/utils/request'

type AnyRecord = Record<string, unknown>

export const chapterApi = {
  getChapter(projectId: string, chapterId: string) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/chapters/${chapterId}`)
  },
  updateChapter(projectId: string, chapterId: string, _operatorId: string, payload: AnyRecord) {
    return request.put<AnyRecord>(`/v1/novels/${projectId}/chapters/${chapterId}`, payload)
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
  restoreVersion(projectId: string, chapterId: string, versionNo: string, _operatorId: string) {
    void _operatorId
    return request.post<AnyRecord>(
      `/v1/novels/${projectId}/chapters/${chapterId}/versions/${versionNo}/restore`,
    )
  },
  getVersionSnapshotUrl(projectId: string, chapterId: string, versionNo: string) {
    return request.get<Record<string, string>>(
      `/v1/novels/${projectId}/chapters/${chapterId}/versions/${versionNo}/snapshot-url`,
    )
  },
  publishChapter(projectId: string, chapterId: string, _operatorId: string) {
    void _operatorId
    return request.post<string>(`/v1/novels/${projectId}/chapters/${chapterId}/publish`)
  },
  getContentUploadUrl(projectId: string, chapterId: string) {
    return request.post<Record<string, string>>(`/v1/novels/${projectId}/chapters/${chapterId}/content-upload-url`)
  },
  commitContent(projectId: string, chapterId: string, _operatorId: string, payload: AnyRecord) {
    return request.post<AnyRecord>(
      `/v1/novels/${projectId}/chapters/${chapterId}/content-commit`,
      payload,
    )
  },
  getContentUrl(projectId: string, chapterId: string) {
    return request.get<Record<string, string>>(`/v1/novels/${projectId}/chapters/${chapterId}/content-url`)
  },
}
