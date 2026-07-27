import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Modal, message } from 'ant-design-vue'
import { ledgerApi } from '@/api/modules/ledger.api'
import type { ProjectLedger } from '@/entities/ledger/model'

export const useLedgerWorkspace = (getProjectId: () => string) => {
  const items = ref<ProjectLedger[]>([])
  const selectedId = ref('')
  const title = ref('')
  const content = ref('')
  const baseContent = ref('')
  const revision = ref('')
  const loadingList = ref(false)
  const loadingContent = ref(false)
  const busy = ref(false)
  const creating = ref(false)
  const newTitle = ref('')
  const conflict = ref(false)
  const statusText = ref('')
  let saveTimer: number | null = null
  let leasePollTimer: number | null = null

  const selected = computed(() => items.value.find((item) => item.ledgerId === selectedId.value) || null)
  const characterCount = computed(() => Array.from(content.value).length)
  const aiEditing = computed(() => {
    const expiresAt = selected.value?.leaseExpiresAt ? new Date(selected.value.leaseExpiresAt).getTime() : 0
    return selected.value?.leaseOwnerType === 'AI' && expiresAt > Date.now()
  })
  const draftKey = (ledgerId: string) => `penmate.ledger-draft.${getProjectId()}.${ledgerId}`

  const loadCompleteContent = async (ledgerId: string) => {
    let offset = 0
    let assembled = ''
    let latest: ProjectLedger
    do {
      latest = await ledgerApi.read(getProjectId(), ledgerId, offset)
      assembled += latest.content || ''
      offset = latest.end || offset
    } while (!latest.complete)
    return { latest, assembled }
  }

  const saveContent = async () => {
    if (!selectedId.value || busy.value || conflict.value || aiEditing.value || content.value === baseContent.value) return
    const left = Array.from(baseContent.value)
    const right = Array.from(content.value)
    let start = 0
    while (start < left.length && start < right.length && left[start] === right[start]) start += 1
    let suffix = 0
    while (suffix < left.length - start && suffix < right.length - start
      && left[left.length - 1 - suffix] === right[right.length - 1 - suffix]) suffix += 1
    const end = left.length - suffix
    const replacement = right.slice(start, right.length - suffix).join('')
    if (end - start > 20_000 || Array.from(replacement).length > 20_000) {
      statusText.value = '单次变更超过 20,000 字符'
      return
    }
    busy.value = true
    statusText.value = '正在保存'
    try {
      const updated = await ledgerApi.update(getProjectId(), selectedId.value, {
        expectedRevision: revision.value,
        start,
        end,
        replacement,
      })
      revision.value = updated.contentRevision
      baseContent.value = content.value
      localStorage.removeItem(draftKey(selectedId.value))
      statusText.value = '已保存'
      items.value = await ledgerApi.list(getProjectId())
    } catch (error) {
      conflict.value = true
      statusText.value = '版本冲突'
      message.warning(error instanceof Error ? error.message : '台账保存失败')
    } finally {
      busy.value = false
    }
  }

  const flushSave = async () => {
    if (saveTimer !== null) window.clearTimeout(saveTimer)
    saveTimer = null
    await saveContent()
  }

  const selectLedger = async (ledgerId: string) => {
    if (ledgerId === selectedId.value && revision.value) return
    await flushSave()
    selectedId.value = ledgerId
    loadingContent.value = true
    conflict.value = false
    try {
      const { latest, assembled } = await loadCompleteContent(ledgerId)
      if (!latest || selectedId.value !== ledgerId) return
      title.value = latest.title
      revision.value = latest.contentRevision
      baseContent.value = assembled
      const draft = localStorage.getItem(draftKey(ledgerId))
      content.value = draft == null ? assembled : draft
      statusText.value = draft == null ? '已同步' : '本地草稿待保存'
    } finally {
      loadingContent.value = false
    }
  }

  const loadList = async () => {
    loadingList.value = true
    try {
      items.value = await ledgerApi.list(getProjectId())
      if (!selectedId.value && items.value.length) await selectLedger(items.value[0].ledgerId)
    } finally {
      loadingList.value = false
    }
  }

  const createLedger = async () => {
    const value = newTitle.value.trim()
    if (!value || busy.value) return
    busy.value = true
    try {
      const created = await ledgerApi.create(getProjectId(), value)
      items.value = [created, ...items.value]
      creating.value = false
      newTitle.value = ''
      selectedId.value = ''
      await selectLedger(created.ledgerId)
    } finally {
      busy.value = false
    }
  }

  const scheduleSave = () => {
    if (!selectedId.value) return
    localStorage.setItem(draftKey(selectedId.value), content.value)
    statusText.value = '待保存'
    if (saveTimer !== null) window.clearTimeout(saveTimer)
    saveTimer = window.setTimeout(() => void saveContent(), 700)
  }

  const saveTitle = async () => {
    const next = title.value.trim()
    if (!selected.value || !next || next === selected.value.title || busy.value || aiEditing.value) return
    busy.value = true
    try {
      const updated = await ledgerApi.update(getProjectId(), selectedId.value, {
        expectedRevision: revision.value,
        title: next,
      })
      revision.value = updated.contentRevision
      selected.value.title = updated.title
      selected.value.contentRevision = updated.contentRevision
      statusText.value = '已保存'
    } catch (error) {
      conflict.value = true
      message.warning(error instanceof Error ? error.message : '台账标题保存失败')
    } finally {
      busy.value = false
    }
  }

  const reloadRemote = async () => {
    const ledgerId = selectedId.value
    if (!ledgerId) return
    localStorage.removeItem(draftKey(ledgerId))
    revision.value = ''
    selectedId.value = ''
    await selectLedger(ledgerId)
  }

  const confirmDelete = () => {
    if (!selected.value) return
    Modal.confirm({
      title: `删除台账“${selected.value.title}”？`,
      content: '删除后不可恢复。',
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      async onOk() {
        await ledgerApi.delete(getProjectId(), selectedId.value, revision.value)
        localStorage.removeItem(draftKey(selectedId.value))
        items.value = items.value.filter((item) => item.ledgerId !== selectedId.value)
        selectedId.value = ''
        revision.value = ''
        content.value = ''
        baseContent.value = ''
        if (items.value.length) await selectLedger(items.value[0].ledgerId)
      },
    })
  }

  onMounted(() => {
    void loadList()
    leasePollTimer = window.setInterval(() => void ledgerApi.list(getProjectId()).then((listed) => { items.value = listed }), 2_000)
  })
  onBeforeUnmount(() => {
    if (leasePollTimer !== null) window.clearInterval(leasePollTimer)
    void flushSave()
  })

  return {
    items, selectedId, title, content, revision, loadingList, loadingContent, busy, creating, newTitle,
    conflict, statusText, selected, characterCount, aiEditing, selectLedger, createLedger, scheduleSave,
    saveTitle, reloadRemote, confirmDelete,
  }
}
