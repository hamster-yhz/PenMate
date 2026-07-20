import request from '@/utils/request'

type AnyRecord = Record<string, unknown>

export const ragApi = {
  getConfiguration(projectId: string) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/rag/configuration`)
  },
  updateConfiguration(projectId: string, payload: AnyRecord) {
    return request.put<AnyRecord>(`/v1/novels/${projectId}/rag/configuration`, payload)
  },
  rebuild(projectId: string) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/rag/rebuild`)
  },
  listDocuments(projectId: string) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/rag/documents`)
  },
  getDocument(projectId: string, docId: string) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/rag/documents/${docId}`)
  },
  initializeUpload(projectId: string, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/rag/documents/uploads`, payload)
  },
  completeUpload(projectId: string, uploadId: string, uploadToken: string) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/rag/documents/uploads/${uploadId}/complete`, {
      uploadToken,
    })
  },
  async uploadDocument(projectId: string, file: File, title?: string) {
    const digest = await crypto.subtle.digest('SHA-256', await file.arrayBuffer())
    const sha256 = Array.from(new Uint8Array(digest), (value) => value.toString(16).padStart(2, '0')).join('')
    const initialized = await this.initializeUpload(projectId, {
      filename: file.name,
      title,
      mimeType: file.type || 'text/plain',
      size: file.size,
      sha256,
    })
    const uploadId = String(initialized.uploadId ?? '')
    const uploadToken = String(initialized.uploadToken ?? '')
    const uploadUrl = String(initialized.uploadUrl ?? '')
    if (!uploadId || !uploadToken || !uploadUrl) throw new Error('Invalid upload initialization response')
    const uploaded = await fetch(uploadUrl, { method: 'PUT', headers: { 'Content-Type': file.type || 'text/plain' }, body: file })
    if (!uploaded.ok) throw new Error(`Object upload failed with HTTP ${uploaded.status}`)
    return this.completeUpload(projectId, uploadId, uploadToken)
  },
  deleteDocument(projectId: string, docId: string) {
    return request.delete<string>(`/v1/novels/${projectId}/rag/documents/${docId}`)
  },
  parseDocument(projectId: string, docId: string) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/rag/documents/${docId}/parse`)
  },
  embedDocument(projectId: string, docId: string) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/rag/documents/${docId}/embed`)
  },
  indexStatus(projectId: string, docId: string) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/rag/documents/${docId}/index-status`)
  },
  retrievalLogs(projectId: string) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/rag/retrieval-logs`)
  },
}
