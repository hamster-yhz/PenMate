import request from '@/utils/request'

type AnyRecord = Record<string, unknown>

export const ragApi = {
  listDocuments(projectId: string) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/rag/documents`)
  },
  getDocument(projectId: string, docId: string) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/rag/documents/${docId}`)
  },
  createDocument(projectId: string, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/rag/documents`, payload)
  },
  getUploadUrl(projectId: string, payload: AnyRecord) {
    return request.post<Record<string, string>>(`/v1/novels/${projectId}/rag/documents/upload-url`, payload)
  },
  parseDocument(projectId: string, docId: string, payload: AnyRecord) {
    return request.post<string>(`/v1/novels/${projectId}/rag/documents/${docId}/parse`, payload)
  },
  embedDocument(projectId: string, docId: string, payload: AnyRecord) {
    return request.post<string>(`/v1/novels/${projectId}/rag/documents/${docId}/embed`, payload)
  },
  indexStatus(projectId: string, docId: string) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/rag/documents/${docId}/index-status`)
  },
  retrievalLogs(projectId: string) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/rag/retrieval-logs`)
  }
}
