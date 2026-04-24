import request from '@/utils/request'
import type { IdLike } from '@/api/types'

export type AnyRecord = Record<string, unknown>

const normalizeProjectPayload = (payload: AnyRecord) => {
  const next: AnyRecord = { ...payload }

  if (next.ownerUserId == null && next.ownerId != null) {
    next.ownerUserId = next.ownerId
  }
  delete next.ownerId

  if ((next.summary == null || next.summary === '') && typeof next.description === 'string') {
    next.summary = next.description
  }
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
  getProject(projectId: IdLike) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}`)
  },
  updateProject(projectId: IdLike, payload: AnyRecord) {
    return request.put<AnyRecord>(`/v1/novels/${projectId}`, normalizeProjectPayload(payload))
  },
  deleteProject(projectId: IdLike, operatorId: IdLike) {
    return request.delete<string>(`/v1/novels/${projectId}?operatorId=${operatorId}`)
  },
  listVolumes(projectId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/volumes`)
  },
  createVolume(projectId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/volumes?operatorId=${operatorId}`, payload)
  },
  updateVolume(projectId: IdLike, volumeId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.put<AnyRecord>(`/v1/novels/${projectId}/volumes/${volumeId}?operatorId=${operatorId}`, payload)
  },
  deleteVolume(projectId: IdLike, volumeId: IdLike, operatorId: IdLike) {
    return request.delete<string>(`/v1/novels/${projectId}/volumes/${volumeId}?operatorId=${operatorId}`)
  },
  listMembers(projectId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/members`)
  },
  addMember(projectId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/members?operatorId=${operatorId}`, payload)
  },
  updateMember(projectId: IdLike, userId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.patch<AnyRecord>(`/v1/novels/${projectId}/members/${userId}?operatorId=${operatorId}`, payload)
  },
  removeMember(projectId: IdLike, userId: IdLike, operatorId: IdLike) {
    return request.delete<string>(`/v1/novels/${projectId}/members/${userId}?operatorId=${operatorId}`)
  },
  listChapters(projectId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/chapters`)
  },
  createChapter(projectId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/chapters?operatorId=${operatorId}`, payload)
  },
  getChapter(projectId: IdLike, chapterId: IdLike) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/chapters/${chapterId}`)
  },
  updateChapter(projectId: IdLike, chapterId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.put<AnyRecord>(`/v1/novels/${projectId}/chapters/${chapterId}?operatorId=${operatorId}`, payload)
  },
  deleteChapter(projectId: IdLike, chapterId: IdLike, operatorId: IdLike) {
    return request.delete<string>(`/v1/novels/${projectId}/chapters/${chapterId}?operatorId=${operatorId}`)
  },
  publishChapter(projectId: IdLike, chapterId: IdLike, operatorId: IdLike) {
    return request.post<string>(`/v1/novels/${projectId}/chapters/${chapterId}/publish?operatorId=${operatorId}`)
  },
  listChapterVersions(projectId: IdLike, chapterId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/chapters/${chapterId}/versions`)
  },
  createChapterVersion(projectId: IdLike, chapterId: IdLike, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/chapters/${chapterId}/versions`, payload)
  },
  getChapterVersion(projectId: IdLike, chapterId: IdLike, versionNo: IdLike) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/chapters/${chapterId}/versions/${versionNo}`)
  },
  restoreChapterVersion(projectId: IdLike, chapterId: IdLike, versionNo: IdLike, operatorId: IdLike) {
    return request.post<AnyRecord>(
      `/v1/novels/${projectId}/chapters/${chapterId}/versions/${versionNo}/restore?operatorId=${operatorId}`
    )
  },
  getChapterContentUrl(projectId: IdLike, chapterId: IdLike) {
    return request.get<Record<string, string>>(`/v1/novels/${projectId}/chapters/${chapterId}/content-url`)
  },
  getChapterContentUploadUrl(projectId: IdLike, chapterId: IdLike) {
    return request.post<Record<string, string>>(`/v1/novels/${projectId}/chapters/${chapterId}/content-upload-url`)
  },
  commitChapterContent(projectId: IdLike, chapterId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/chapters/${chapterId}/content-commit?operatorId=${operatorId}`, payload)
  },
  getChapterVersionSnapshotUrl(projectId: IdLike, chapterId: IdLike, versionNo: IdLike) {
    return request.get<Record<string, string>>(
      `/v1/novels/${projectId}/chapters/${chapterId}/versions/${versionNo}/snapshot-url`
    )
  },
  listOutlineTree(projectId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/outlines/tree`)
  },
  createOutlineNode(projectId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/outlines/nodes?operatorId=${operatorId}`, payload)
  },
  updateOutlineNode(projectId: IdLike, nodeId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.put<AnyRecord>(`/v1/novels/${projectId}/outlines/nodes/${nodeId}?operatorId=${operatorId}`, payload)
  },
  moveOutlineNode(projectId: IdLike, nodeId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.patch<string>(`/v1/novels/${projectId}/outlines/nodes/${nodeId}/move?operatorId=${operatorId}`, payload)
  },
  deleteOutlineNode(projectId: IdLike, nodeId: IdLike, operatorId: IdLike) {
    return request.delete<string>(`/v1/novels/${projectId}/outlines/nodes/${nodeId}?operatorId=${operatorId}`)
  },
  listCards(projectId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/cards`)
  },
  createCard(projectId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/cards?operatorId=${operatorId}`, payload)
  },
  getCard(projectId: IdLike, cardId: IdLike) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/cards/${cardId}`)
  },
  updateCard(projectId: IdLike, cardId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.put<AnyRecord>(`/v1/novels/${projectId}/cards/${cardId}?operatorId=${operatorId}`, payload)
  },
  deleteCard(projectId: IdLike, cardId: IdLike, operatorId: IdLike) {
    return request.delete<string>(`/v1/novels/${projectId}/cards/${cardId}?operatorId=${operatorId}`)
  },
  listCardRelations(projectId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/card-relations`)
  },
  createCardRelation(projectId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/card-relations?operatorId=${operatorId}`, payload)
  },
  deleteCardRelation(projectId: IdLike, relationId: IdLike, operatorId: IdLike) {
    return request.delete<string>(`/v1/novels/${projectId}/card-relations/${relationId}?operatorId=${operatorId}`)
  }
}

