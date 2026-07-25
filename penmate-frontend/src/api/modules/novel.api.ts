import request from '@/utils/request'
import type { NovelCoverCrop } from '@/entities/novel/model'

export type { NovelCoverCrop } from '@/entities/novel/model'

export type AnyRecord = Record<string, unknown>
export type NovelExportFormat = 'txt' | 'markdown' | 'docx'
export interface NovelCoverState extends AnyRecord {
  coverUrl?: string
  thumbnailUrl?: string
  originalUrl?: string
  uploadId?: string
  status?: string
  errorMessage?: string
  crop?: NovelCoverCrop
}

export interface NovelDirectoryState {
  structureRevision: number
  volumes: AnyRecord[]
  chapters: AnyRecord[]
}

export interface MoveNovelDirectoryItemPayload {
  nodeType: 'VOLUME' | 'CHAPTER'
  nodeId: string
  targetVolumeId?: string
  sortOrder: number
  expectedStructureRevision: number
}

export interface NovelImportChapter {
  title: string
  content: string
}

export interface NovelImportVolume {
  title: string
  chapters: NovelImportChapter[]
}

export interface NovelImportDiagnostic {
  code: string
  severity: 'INFO' | 'WARNING' | 'ERROR'
  message: string
  volumeIndex?: number | null
  chapterIndex?: number | null
}

export interface NovelImportDraft {
  projectTitle: string
  sourceFormat?: 'TXT' | 'MARKDOWN' | 'DOCX'
  volumes: NovelImportVolume[]
  diagnostics?: NovelImportDiagnostic[]
}

export interface NovelImportPreview {
  sessionId: string
  draft: NovelImportDraft
}

export interface NovelImportSession {
  sessionId: string
  status: 'DRAFT' | 'READY' | 'QUEUED' | 'IMPORTING' | 'PAUSED' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  projectId?: string | null
  jobId?: string | null
  checkpointChapter?: number
  totalChapters?: number
  jobStatus?: string | null
  progressCurrent?: number | null
  progressTotal?: number | null
  progressMessage?: string | null
  errorMessage?: string | null
}

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
  listDeletedProjects() {
    return request.get<AnyRecord[]>('/v1/novels/trash')
  },
  createProject(payload: AnyRecord) {
    return request.post<AnyRecord>('/v1/novels', normalizeProjectPayload(payload))
  },
  previewNovelImport(file: File) {
    const form = new FormData()
    form.append('file', file)
    return request.post<NovelImportPreview>('/v1/novels/imports/preview', form, { timeout: 60_000 })
  },
  confirmNovelImport(sessionId: string, payload: NovelImportDraft) {
    return request.post<NovelImportSession>(`/v1/novels/imports/${sessionId}/confirm`, payload, { timeout: 60_000 })
  },
  getNovelImport(sessionId: string) {
    return request.get<NovelImportSession>(`/v1/novels/imports/${sessionId}`)
  },
  pauseNovelImport(sessionId: string) {
    return request.post<NovelImportSession>(`/v1/novels/imports/${sessionId}/pause`)
  },
  resumeNovelImport(sessionId: string) {
    return request.post<NovelImportSession>(`/v1/novels/imports/${sessionId}/resume`)
  },
  cancelNovelImport(sessionId: string) {
    return request.post<NovelImportSession>(`/v1/novels/imports/${sessionId}/cancel`)
  },
  retryNovelImport(sessionId: string) {
    return request.post<NovelImportSession>(`/v1/novels/imports/${sessionId}/retry`)
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
  restoreProject(projectId: string) {
    return request.post<AnyRecord>(`/v1/novels/trash/${projectId}/restore`)
  },
  permanentlyDeleteProject(projectId: string, confirmationTitle: string) {
    return request.delete<string>(`/v1/novels/trash/${projectId}`, {
      data: { confirmationTitle },
    })
  },
  exportProject(projectId: string, format: NovelExportFormat) {
    return request.download(`/v1/novels/${projectId}/exports/${format}`)
  },
  getCover(projectId: string) {
    return request.get<NovelCoverState>(`/v1/novels/${projectId}/cover`)
  },
  initializeCoverUpload(projectId: string, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/cover/uploads`, payload)
  },
  completeCoverUpload(projectId: string, uploadId: string, uploadToken: string, crop: NovelCoverCrop) {
    return request.post<NovelCoverState>(`/v1/novels/${projectId}/cover/uploads/${uploadId}/complete`, {
      uploadToken,
      crop,
    })
  },
  async uploadCover(projectId: string, file: File, crop: NovelCoverCrop) {
    const digest = await crypto.subtle.digest('SHA-256', await file.arrayBuffer())
    const sha256 = Array.from(new Uint8Array(digest), (value) => value.toString(16).padStart(2, '0')).join('')
    const initialized = await this.initializeCoverUpload(projectId, {
      filename: file.name,
      mimeType: file.type,
      size: file.size,
      sha256,
    })
    const uploadId = String(initialized.uploadId ?? '')
    const uploadToken = String(initialized.uploadToken ?? '')
    const uploadUrl = String(initialized.uploadUrl ?? '')
    if (!uploadId || !uploadToken || !uploadUrl) throw new Error('封面上传初始化响应无效')
    const uploaded = await fetch(uploadUrl, {
      method: 'PUT',
      headers: { 'Content-Type': file.type },
      body: file,
    })
    if (!uploaded.ok) throw new Error(`封面上传失败（HTTP ${uploaded.status}）`)
    return this.completeCoverUpload(projectId, uploadId, uploadToken, crop)
  },
  recropCover(projectId: string, crop: NovelCoverCrop) {
    return request.post<NovelCoverState>(`/v1/novels/${projectId}/cover/crops`, { crop })
  },
  retryCover(projectId: string, uploadId: string) {
    return request.post<NovelCoverState>(`/v1/novels/${projectId}/cover/uploads/${uploadId}/retry`)
  },
  removeCover(projectId: string) {
    return request.delete<string>(`/v1/novels/${projectId}/cover`)
  },
  listVolumes(projectId: string) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/volumes`)
  },
  getDirectory(projectId: string) {
    return request.get<NovelDirectoryState>(`/v1/novels/${projectId}/directory`)
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
  moveDirectoryItem(projectId: string, payload: MoveNovelDirectoryItemPayload) {
    return request.patch<NovelDirectoryState>(`/v1/novels/${projectId}/directory/position`, payload)
  },
  deleteChapter(projectId: string, chapterId: string, _operatorId: string) {
    void _operatorId
    return request.delete<string>(`/v1/novels/${projectId}/chapters/${chapterId}`)
  },
}
