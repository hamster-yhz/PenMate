import { ref } from 'vue'
import { chapterApi } from '@/api/modules/chapter.api'
import { getErrorMessage } from '@/utils/errors'

const SAVE_DELAY_MS = 1000
const MAX_SAVE_DELAY_MS = 5000
const RENEW_INTERVAL_MS = 20_000
const LEASE_STORAGE_PREFIX = 'penmate.chapter-lease'

type ChapterEditingSessionOptions = {
  onSaved?: (projectId: string, chapterId: string, content: string) => void | Promise<void>
}

export const useChapterEditingSession = (options: ChapterEditingSessionOptions = {}) => {
  const editable = ref(false)
  const lockReason = ref('')
  const leaseToken = ref('')
  const leaseOwnerType = ref('')
  const contentRevision = ref(1)
  const saveStatus = ref('')

  let projectId = ''
  let chapterId = ''
  let pendingContent = ''
  let savedContent = ''
  let dirty = false
  let debounceTimer: ReturnType<typeof setTimeout> | null = null
  let maxTimer: ReturnType<typeof setTimeout> | null = null
  let renewTimer: ReturnType<typeof setInterval> | null = null
  let savePromise: Promise<void> | null = null
  let online = typeof navigator === 'undefined' || navigator.onLine

  const leaseStorageKey = (targetProjectId = projectId, targetChapterId = chapterId) =>
    `${LEASE_STORAGE_PREFIX}:${targetProjectId}:${targetChapterId}`

  const readStoredLeaseToken = (targetProjectId: string, targetChapterId: string) => {
    if (typeof sessionStorage === 'undefined') return ''
    return sessionStorage.getItem(leaseStorageKey(targetProjectId, targetChapterId)) || ''
  }

  const storeLeaseToken = (token: string) => {
    if (typeof sessionStorage === 'undefined' || !projectId || !chapterId || !token) return
    sessionStorage.setItem(leaseStorageKey(), token)
  }

  const clearStoredLeaseToken = () => {
    if (typeof sessionStorage === 'undefined' || !projectId || !chapterId) return
    sessionStorage.removeItem(leaseStorageKey())
  }

  const clearSaveTimers = () => {
    if (debounceTimer) clearTimeout(debounceTimer)
    if (maxTimer) clearTimeout(maxTimer)
    debounceTimer = null
    maxTimer = null
  }

  const stopRenewal = () => {
    if (renewTimer) clearInterval(renewTimer)
    renewTimer = null
  }

  const renew = async () => {
    if (!online || !editable.value || !projectId || !chapterId || !leaseToken.value) return
    try {
      await chapterApi.renewLease(projectId, chapterId, leaseToken.value)
    } catch (error: unknown) {
      editable.value = false
      lockReason.value = getErrorMessage(error, '编辑权限已失效')
      saveStatus.value = '保存失败'
      stopRenewal()
    }
  }

  const startRenewal = () => {
    stopRenewal()
    renewTimer = setInterval(() => void renew(), RENEW_INTERVAL_MS)
  }

  const runSave = async () => {
    clearSaveTimers()
    if (!dirty || !editable.value || !leaseToken.value || !projectId || !chapterId) return
    if (!online) {
      saveStatus.value = '离线'
      return
    }
    if (savePromise) return savePromise

    savePromise = (async () => {
      while (dirty && editable.value) {
        dirty = false
        const content = pendingContent
        const revision = contentRevision.value
        saveStatus.value = '正在同步'
        try {
          const saved = await chapterApi.saveContent(projectId, chapterId, {
            leaseToken: leaseToken.value,
            expectedRevision: revision,
            content,
          })
          contentRevision.value = Number(saved.contentRevision ?? revision + 1)
          savedContent = content
          saveStatus.value = '已保存'
          await options.onSaved?.(projectId, chapterId, content)
        } catch (error: unknown) {
          dirty = true
          saveStatus.value = '保存失败'
          lockReason.value = getErrorMessage(error, '章节保存失败')
          break
        }
      }
    })().finally(() => {
      savePromise = null
    })
    return savePromise
  }

  const scheduleSave = (content: string) => {
    pendingContent = content
    dirty = content !== savedContent
    if (!dirty) {
      clearSaveTimers()
      return
    }
    if (!editable.value) return
    if (!online) {
      clearSaveTimers()
      saveStatus.value = '离线'
      return
    }
    saveStatus.value = '本地暂存'
    if (debounceTimer) clearTimeout(debounceTimer)
    debounceTimer = setTimeout(() => void runSave(), SAVE_DELAY_MS)
    if (!maxTimer) maxTimer = setTimeout(() => void runSave(), MAX_SAVE_DELAY_MS)
  }

  const flush = async (content?: string) => {
    if (content !== undefined) {
      pendingContent = content
      dirty = content !== savedContent
    }
    await runSave()
  }

  const release = async () => {
    clearSaveTimers()
    stopRenewal()
    if (editable.value && projectId && chapterId && leaseToken.value) {
      try {
        await chapterApi.releaseLease(projectId, chapterId, leaseToken.value)
        clearStoredLeaseToken()
      } catch {
        // Keep the tab-scoped token when the server may still own the lease.
      }
    }
    editable.value = false
    leaseToken.value = ''
    leaseOwnerType.value = ''
  }

  const open = async (nextProjectId: string, nextChapterId: string, force = false) => {
    if (projectId && chapterId && (projectId !== nextProjectId || chapterId !== nextChapterId)) {
      await flush()
      await release()
    }
    projectId = nextProjectId
    chapterId = nextChapterId
    saveStatus.value = ''
    lockReason.value = ''
    const storedLeaseToken = force ? '' : readStoredLeaseToken(projectId, chapterId)
    let lease: Awaited<ReturnType<typeof chapterApi.acquireLease>> | undefined
    if (storedLeaseToken) {
      try {
        lease = await chapterApi.renewLease(projectId, chapterId, storedLeaseToken)
      } catch {
        // A network failure does not prove that the server-side lease is gone.
      }
    }
    lease ??= await chapterApi.acquireLease(projectId, chapterId, force)
    editable.value = Boolean(lease.editable)
    leaseOwnerType.value = String(lease.ownerType || (editable.value ? 'USER' : ''))
    leaseToken.value = editable.value ? String(lease.leaseToken || '') : ''
    contentRevision.value = Math.max(1, Number(lease.contentRevision ?? 1))
    pendingContent = String(lease.content ?? '')
    savedContent = pendingContent
    dirty = false
    if (editable.value) {
      storeLeaseToken(leaseToken.value)
      startRenewal()
    } else {
      lockReason.value = String(lease.reason || (lease.ownerType === 'AI' ? 'AI 正在编辑当前章节' : '此章节已在其他窗口编辑'))
    }
    return { content: pendingContent, editable: editable.value }
  }

  const takeover = () => projectId && chapterId ? open(projectId, chapterId, true) : Promise.resolve({ content: '', editable: false })
  const setOnline = (nextOnline: boolean) => {
    online = nextOnline
    if (!online) {
      clearSaveTimers()
      if (dirty) saveStatus.value = '离线'
      return
    }
    if (dirty) void runSave()
  }
  const dispose = async () => {
    await flush()
    await release()
  }

  return { editable, lockReason, leaseOwnerType, contentRevision, saveStatus, open, takeover, scheduleSave, flush, release, setOnline, dispose }
}
