import request from '@/utils/request'
import type { IdLike } from '@/api/types'

type AnyRecord = Record<string, unknown>

export const cardApi = {
  listCards(projectId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/cards`)
  },
  getCard(projectId: IdLike, cardId: IdLike) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/cards/${cardId}`)
  },
  createCard(projectId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/cards?operatorId=${operatorId}`, payload)
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

