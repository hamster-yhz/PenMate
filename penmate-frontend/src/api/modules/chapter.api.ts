import request from '@/utils/request'
import type { ChapterAiUndoOperation } from '@/entities/chapter/model'

export type { ChapterAiUndoOperation } from '@/entities/chapter/model'

type AnyRecord = Record<string, unknown>

export const chapterApi = {
  getChapter(projectId: string, chapterId: string) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/chapters/${chapterId}`)
  },
  updateChapter(projectId: string, chapterId: string, _operatorId: string, payload: AnyRecord) {
    return request.put<AnyRecord>(`/v1/novels/${projectId}/chapters/${chapterId}`, payload)
  },
  acquireLease(projectId: string, chapterId: string, force = false) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/chapters/${chapterId}/lease`, { force })
  },
  renewLease(projectId: string, chapterId: string, leaseToken: string) {
    return request.put<AnyRecord>(`/v1/novels/${projectId}/chapters/${chapterId}/lease/${encodeURIComponent(leaseToken)}`)
  },
  releaseLease(projectId: string, chapterId: string, leaseToken: string) {
    return request.delete<string>(`/v1/novels/${projectId}/chapters/${chapterId}/lease/${encodeURIComponent(leaseToken)}`)
  },
  saveContent(projectId: string, chapterId: string, payload: { leaseToken: string; expectedRevision: number; content: string }) {
    return request.put<AnyRecord>(`/v1/novels/${projectId}/chapters/${chapterId}/content`, payload)
  },
  listAiUndo(projectId: string, chapterId: string) {
    return request.get<ChapterAiUndoOperation[]>(`/v1/novels/${projectId}/chapters/${chapterId}/ai-undo`)
  },
  undoAiEdit(projectId: string, operationId: string) {
    return request.post<ChapterAiUndoOperation>(`/v1/novels/${projectId}/ai-edits/${operationId}/undo`)
  },
  undoRunAiEdits(projectId: string, runId: string) {
    return request.post<ChapterAiUndoOperation[]>(`/v1/novels/${projectId}/agent-runs/${runId}/chapter-edits/undo`)
  },
}
