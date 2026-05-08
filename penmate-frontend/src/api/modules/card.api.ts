import request from '@/utils/request'

type AnyRecord = Record<string, unknown>

export const cardApi = {
  listCards(projectId: string) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/cards`)
  },
  getCard(projectId: string, cardId: string) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/cards/${cardId}`)
  },
  createCard(projectId: string, operatorId: string, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/cards?operatorId=${operatorId}`, payload)
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
