import request from '@/utils/request'
import type { IdLike } from '@/api/types'

type AnyRecord = Record<string, unknown>

export const ragApi = {
  listDocuments(projectId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/rag/documents`)
  },
  getDocument(projectId: IdLike, docId: IdLike) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/rag/documents/${docId}`)
  },
  createDocument(projectId: IdLike, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/rag/documents`, payload)
  },
  getUploadUrl(projectId: IdLike, payload: AnyRecord) {
    return request.post<Record<string, string>>(`/v1/novels/${projectId}/rag/documents/upload-url`, payload)
  },
  parseDocument(projectId: IdLike, docId: IdLike, payload: AnyRecord) {
    return request.post<string>(`/v1/novels/${projectId}/rag/documents/${docId}/parse`, payload)
  },
  embedDocument(projectId: IdLike, docId: IdLike, payload: AnyRecord) {
    return request.post<string>(`/v1/novels/${projectId}/rag/documents/${docId}/embed`, payload)
  },
  indexStatus(projectId: IdLike, docId: IdLike) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/rag/documents/${docId}/index-status`)
  },
  retrievalLogs(projectId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/rag/retrieval-logs`)
  }
}

