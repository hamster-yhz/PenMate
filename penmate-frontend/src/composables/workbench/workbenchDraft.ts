const DATABASE_NAME = 'penmate-writing'
const DATABASE_VERSION = 1
const STORE_NAME = 'chapter-drafts'

export type ChapterDraftRecord = {
  key: string
  projectId: string
  chapterId: string
  content: string
  updatedAt: number
  conflicted?: boolean
}

const fallbackDrafts = new Map<string, ChapterDraftRecord>()
let databasePromise: Promise<IDBDatabase | null> | null = null

export const getDraftStorageKey = (projectId: string, chapterId: string) =>
  `${projectId}:${chapterId}`

const openDatabase = () => {
  if (databasePromise) return databasePromise
  databasePromise = new Promise((resolve) => {
    if (typeof indexedDB === 'undefined') {
      resolve(null)
      return
    }
    const request = indexedDB.open(DATABASE_NAME, DATABASE_VERSION)
    request.onupgradeneeded = () => {
      if (!request.result.objectStoreNames.contains(STORE_NAME)) {
        request.result.createObjectStore(STORE_NAME, { keyPath: 'key' })
      }
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => resolve(null)
    request.onblocked = () => resolve(null)
  })
  return databasePromise
}

const execute = async <T>(
  mode: IDBTransactionMode,
  operation: (store: IDBObjectStore) => IDBRequest<T>,
): Promise<T | null> => {
  const database = await openDatabase()
  if (!database) return null
  return new Promise((resolve) => {
    const transaction = database.transaction(STORE_NAME, mode)
    const request = operation(transaction.objectStore(STORE_NAME))
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => resolve(null)
    transaction.onabort = () => resolve(null)
  })
}

export const saveChapterDraft = async (
  projectId: string,
  chapterId: string,
  content: string,
  conflicted = false,
) => {
  const key = getDraftStorageKey(projectId, chapterId)
  const record: ChapterDraftRecord = { key, projectId, chapterId, content, updatedAt: Date.now(), conflicted }
  fallbackDrafts.set(key, record)
  await execute('readwrite', (store) => store.put(record))
}

export const readChapterDraft = async (projectId: string, chapterId: string) => {
  const key = getDraftStorageKey(projectId, chapterId)
  const stored = await execute<ChapterDraftRecord>('readonly', (store) => store.get(key))
  return stored ?? fallbackDrafts.get(key) ?? null
}

export const clearChapterDraft = async (projectId: string, chapterId: string) => {
  const key = getDraftStorageKey(projectId, chapterId)
  fallbackDrafts.delete(key)
  await execute('readwrite', (store) => store.delete(key))
}
