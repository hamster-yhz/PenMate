export interface ChapterAiUndoOperation {
  operationId: string
  runId: string
  chapterId: string
  chapterTitle: string
  status: string
  sequenceNo: number
  createdAt?: string | null
  expiresAt?: string | null
  undoneAt?: string | null
}
