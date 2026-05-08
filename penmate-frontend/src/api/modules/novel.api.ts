import request from '@/utils/request'

export type AnyRecord = Record<string, unknown>

const normalizeProjectPayload = (payload: AnyRecord) => {
  const next: AnyRecord = { ...payload }

  delete next.ownerId
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
  deleteProject(projectId: string, operatorId: string) {
    return request.delete<string>(`/v1/novels/${projectId}?operatorId=${operatorId}`)
  },
  listVolumes(projectId: string) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/volumes`)
  },
  createVolume(projectId: string, operatorId: string, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/volumes?operatorId=${operatorId}`, payload)
  },
  updateVolume(projectId: string, volumeId: string, operatorId: string, payload: AnyRecord) {
    return request.put<AnyRecord>(`/v1/novels/${projectId}/volumes/${volumeId}?operatorId=${operatorId}`, payload)
  },
  deleteVolume(projectId: string, volumeId: string, operatorId: string) {
    return request.delete<string>(`/v1/novels/${projectId}/volumes/${volumeId}?operatorId=${operatorId}`)
  },
  listChapters(projectId: string) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/chapters`)
  },
  createChapter(projectId: string, operatorId: string, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/chapters?operatorId=${operatorId}`, payload)
  },
  getChapter(projectId: string, chapterId: string) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/chapters/${chapterId}`)
  },
  updateChapter(projectId: string, chapterId: string, operatorId: string, payload: AnyRecord) {
    return request.put<AnyRecord>(`/v1/novels/${projectId}/chapters/${chapterId}?operatorId=${operatorId}`, payload)
  },
  deleteChapter(projectId: string, chapterId: string, operatorId: string) {
    return request.delete<string>(`/v1/novels/${projectId}/chapters/${chapterId}?operatorId=${operatorId}`)
  },
  publishChapter(projectId: string, chapterId: string, operatorId: string) {
    return request.post<string>(`/v1/novels/${projectId}/chapters/${chapterId}/publish?operatorId=${operatorId}`)
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
  restoreChapterVersion(projectId: string, chapterId: string, versionNo: number, operatorId: string) {
    return request.post<AnyRecord>(
      `/v1/novels/${projectId}/chapters/${chapterId}/versions/${versionNo}/restore?operatorId=${operatorId}`
    )
  },
  getChapterContentUrl(projectId: string, chapterId: string) {
    return request.get<Record<string, string>>(`/v1/novels/${projectId}/chapters/${chapterId}/content-url`)
  },
  getChapterContentUploadUrl(projectId: string, chapterId: string) {
    return request.post<Record<string, string>>(`/v1/novels/${projectId}/chapters/${chapterId}/content-upload-url`)
  },
  commitChapterContent(projectId: string, chapterId: string, operatorId: string, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/chapters/${chapterId}/content-commit?operatorId=${operatorId}`, payload)
  },
  getChapterVersionSnapshotUrl(projectId: string, chapterId: string, versionNo: number) {
    return request.get<Record<string, string>>(
      `/v1/novels/${projectId}/chapters/${chapterId}/versions/${versionNo}/snapshot-url`
    )
  },
  listOutlineTree(projectId: string) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/outlines/tree`)
  },
  createOutlineNode(projectId: string, operatorId: string, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/outlines/nodes?operatorId=${operatorId}`, payload)
  },
  updateOutlineNode(projectId: string, nodeId: string, operatorId: string, payload: AnyRecord) {
    return request.put<AnyRecord>(`/v1/novels/${projectId}/outlines/nodes/${nodeId}?operatorId=${operatorId}`, payload)
  },
  moveOutlineNode(projectId: string, nodeId: string, operatorId: string, payload: AnyRecord) {
    return request.patch<string>(`/v1/novels/${projectId}/outlines/nodes/${nodeId}/move?operatorId=${operatorId}`, payload)
  },
  deleteOutlineNode(projectId: string, nodeId: string, operatorId: string) {
    return request.delete<string>(`/v1/novels/${projectId}/outlines/nodes/${nodeId}?operatorId=${operatorId}`)
  },
  listCards(projectId: string) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/cards`)
  },
  createCard(projectId: string, operatorId: string, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/cards?operatorId=${operatorId}`, payload)
  },
  getCard(projectId: string, cardId: string) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/cards/${cardId}`)
  },
  updateCard(projectId: string, cardId: string, operatorId: string, payload: AnyRecord) {
    return request.put<AnyRecord>(`/v1/novels/${projectId}/cards/${cardId}?operatorId=${operatorId}`, payload)
  },
  deleteCard(projectId: string, cardId: string, operatorId: string) {
    return request.delete<string>(`/v1/novels/${projectId}/cards/${cardId}?operatorId=${operatorId}`)
  },
  listCardRelations(projectId: string) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/card-relations`)
  },
  createCardRelation(projectId: string, operatorId: string, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/card-relations?operatorId=${operatorId}`, payload)
  },
  deleteCardRelation(projectId: string, relationId: string, operatorId: string) {
    return request.delete<string>(`/v1/novels/${projectId}/card-relations/${relationId}?operatorId=${operatorId}`)
  }
}

