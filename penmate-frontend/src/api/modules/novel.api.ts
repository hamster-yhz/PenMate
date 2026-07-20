import request from '@/utils/request'

export type AnyRecord = Record<string, unknown>

const normalizeProjectPayload = (payload: AnyRecord) => {
  const next: AnyRecord = { ...payload }

  delete next.ownerId
  delete next.ownerUserId
  delete next.description

  return next
}

export const novelApi = {
  listProjects() {
    return request.get<AnyRecord[]>('/v1/novels')
  },
  createProject(payload: AnyRecord) {
    return request.post<AnyRecord>('/v1/novels', normalizeProjectPayload(payload))
  },
  getProject(projectId: string) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}`)
  },
  updateProject(projectId: string, payload: AnyRecord) {
    return request.put<AnyRecord>(`/v1/novels/${projectId}`, normalizeProjectPayload(payload))
  },
  deleteProject(projectId: string, _operatorId: string) {
    void _operatorId
    return request.delete<string>(`/v1/novels/${projectId}`)
  },
  listVolumes(projectId: string) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/volumes`)
  },
  createVolume(projectId: string, _operatorId: string, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/volumes`, payload)
  },
  updateVolume(projectId: string, volumeId: string, _operatorId: string, payload: AnyRecord) {
    return request.put<AnyRecord>(`/v1/novels/${projectId}/volumes/${volumeId}`, payload)
  },
  deleteVolume(projectId: string, volumeId: string, _operatorId: string) {
    void _operatorId
    return request.delete<string>(`/v1/novels/${projectId}/volumes/${volumeId}`)
  },
  listChapters(projectId: string) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/chapters`)
  },
  createChapter(projectId: string, _operatorId: string, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/chapters`, payload)
  },
  getChapter(projectId: string, chapterId: string) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/chapters/${chapterId}`)
  },
  updateChapter(projectId: string, chapterId: string, _operatorId: string, payload: AnyRecord) {
    return request.put<AnyRecord>(`/v1/novels/${projectId}/chapters/${chapterId}`, payload)
  },
  moveChapter(projectId: string, chapterId: string, _operatorId: string, payload: AnyRecord) {
    return request.patch<AnyRecord>(
      `/v1/novels/${projectId}/chapters/${chapterId}/position`,
      payload,
    )
  },
  deleteChapter(projectId: string, chapterId: string, _operatorId: string) {
    void _operatorId
    return request.delete<string>(`/v1/novels/${projectId}/chapters/${chapterId}`)
  },
  publishChapter(projectId: string, chapterId: string, _operatorId: string) {
    void _operatorId
    return request.post<string>(`/v1/novels/${projectId}/chapters/${chapterId}/publish`)
  },
  listChapterVersions(projectId: string, chapterId: string) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/chapters/${chapterId}/versions`)
  },
  createChapterVersion(projectId: string, chapterId: string, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/chapters/${chapterId}/versions`, payload)
  },
  getChapterVersion(projectId: string, chapterId: string, versionNo: number) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/chapters/${chapterId}/versions/${versionNo}`)
  },
  restoreChapterVersion(projectId: string, chapterId: string, versionNo: number, _operatorId: string) {
    void _operatorId
    return request.post<AnyRecord>(
      `/v1/novels/${projectId}/chapters/${chapterId}/versions/${versionNo}/restore`,
    )
  },
  getChapterContentUrl(projectId: string, chapterId: string) {
    return request.get<Record<string, string>>(`/v1/novels/${projectId}/chapters/${chapterId}/content-url`)
  },
  getChapterContentUploadUrl(projectId: string, chapterId: string) {
    return request.post<Record<string, string>>(`/v1/novels/${projectId}/chapters/${chapterId}/content-upload-url`)
  },
  commitChapterContent(projectId: string, chapterId: string, _operatorId: string, payload: AnyRecord) {
    return request.post<AnyRecord>(
      `/v1/novels/${projectId}/chapters/${chapterId}/content-commit`,
      payload,
    )
  },
  getChapterVersionSnapshotUrl(projectId: string, chapterId: string, versionNo: number) {
    return request.get<Record<string, string>>(
      `/v1/novels/${projectId}/chapters/${chapterId}/versions/${versionNo}/snapshot-url`,
    )
  },
  listOutlineTree(projectId: string) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/outlines/tree`)
  },
  createOutlineNode(projectId: string, _operatorId: string, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/outlines/nodes`, payload)
  },
  updateOutlineNode(projectId: string, nodeId: string, _operatorId: string, payload: AnyRecord) {
    return request.put<AnyRecord>(`/v1/novels/${projectId}/outlines/nodes/${nodeId}`, payload)
  },
  moveOutlineNode(projectId: string, nodeId: string, _operatorId: string, payload: AnyRecord) {
    return request.patch<string>(
      `/v1/novels/${projectId}/outlines/nodes/${nodeId}/move`,
      payload,
    )
  },
  deleteOutlineNode(projectId: string, nodeId: string, _operatorId: string) {
    void _operatorId
    return request.delete<string>(`/v1/novels/${projectId}/outlines/nodes/${nodeId}`)
  },
}
