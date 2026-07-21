<script setup lang="ts">
import {
  CloseOutlined,
  DeleteOutlined,
  EditOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
import { computed, nextTick, ref } from 'vue'
import type { ConversationItem } from '@/components/workbench/workbenchTypes'

const props = withDefaults(defineProps<{
  visible?: boolean
  loading?: boolean
  conversations?: ConversationItem[]
  deletedConversations?: ConversationItem[]
  currentConversationId?: string | null
}>(), { visible: false, loading: false, conversations: () => [], deletedConversations: () => [], currentConversationId: null })

const emit = defineEmits<{
  close: []
  'select-conversation': [conversationId: string]
  'load-deleted': []
  rename: [payload: { conversationId: string; title: string }]
  delete: [conversationId: string]
  restore: [conversationId: string]
}>()

const query = ref('')
const deletedMode = ref(false)
const editingId = ref('')
const editingTitle = ref('')
const deletingId = ref('')
const titleInput = ref<HTMLInputElement | null>(null)

const source = computed(() => deletedMode.value ? props.deletedConversations : props.conversations)
const filtered = computed(() => {
  const normalized = query.value.trim().toLowerCase()
  return source.value.filter((item) => !normalized || item.title.toLowerCase().includes(normalized))
})
const grouped = computed(() => {
  const groups = new Map<string, ConversationItem[]>()
  const now = new Date()
  for (const item of filtered.value) {
    const date = new Date(item.lastMessageAt || item.updatedAt)
    const days = Number.isNaN(date.getTime()) ? 999 : Math.floor((now.getTime() - date.getTime()) / 86_400_000)
    const label = days <= 0 ? '今天' : days === 1 ? '昨天' : days < 7 ? '最近 7 天' : '更早'
    groups.set(label, [...(groups.get(label) ?? []), item])
  }
  return [...groups.entries()]
})

const switchMode = (deleted: boolean) => {
  deletedMode.value = deleted
  query.value = ''
  editingId.value = ''
  deletingId.value = ''
  if (deleted) emit('load-deleted')
}
const startRename = async (item: ConversationItem) => {
  editingId.value = item.conversationId
  editingTitle.value = item.title
  await nextTick()
  const candidate = (Array.isArray(titleInput.value) ? titleInput.value[0] : titleInput.value) as HTMLInputElement | undefined
  candidate?.focus?.()
  candidate?.select?.()
}
const saveRename = () => {
  const title = editingTitle.value.trim()
  if (!editingId.value || !title || title.length > 80) return
  emit('rename', { conversationId: editingId.value, title })
  editingId.value = ''
}
const displayTime = (item: ConversationItem) => {
  const date = new Date(item.lastMessageAt || item.updatedAt)
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleString('zh-CN', { hour12: false, month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}
</script>

<template>
  <section v-if="visible" class="conversation-panel" aria-label="会话历史">
    <header class="history-header">
      <div><strong>会话历史</strong><span>{{ deletedMode ? '已删除会话' : `${conversations.length} 个会话` }}</span></div>
      <button type="button" title="关闭" aria-label="关闭会话历史" @click="emit('close')"><CloseOutlined /></button>
    </header>
    <div class="history-controls">
      <div class="history-tabs" role="tablist">
        <button type="button" :class="{ active: !deletedMode }" @click="switchMode(false)">进行中</button>
        <button type="button" :class="{ active: deletedMode }" @click="switchMode(true)">已删除</button>
      </div>
      <label class="history-search"><SearchOutlined /><input v-model="query" placeholder="搜索会话" aria-label="搜索会话" /></label>
    </div>
    <div class="history-body">
      <p v-if="loading" class="history-empty">正在加载...</p>
      <p v-else-if="!filtered.length" class="history-empty">{{ query ? '没有匹配的会话' : '暂无会话' }}</p>
      <section v-for="[label, items] in grouped" v-else :key="label" class="history-group">
        <h3>{{ label }}</h3>
        <div v-for="item in items" :key="item.conversationId" class="conversation-row" :class="{ active: currentConversationId === item.conversationId }">
          <div v-if="editingId === item.conversationId" class="rename-row">
            <input ref="titleInput" v-model="editingTitle" maxlength="80" @keydown.enter.prevent="saveRename" @keydown.esc="editingId = ''" />
            <button type="button" @click="saveRename">保存</button><button type="button" @click="editingId = ''">取消</button>
          </div>
          <template v-else>
            <button v-if="!deletedMode" type="button" class="conversation-main" @click="emit('select-conversation', item.conversationId)">
              <strong>{{ item.title || `会话 ${item.conversationId}` }}</strong>
              <span class="conversation-meta"><time>{{ displayTime(item) }}</time><em v-if="item.lastRunStatus">{{ item.lastRunStatus }}</em></span>
            </button>
            <div v-else class="conversation-main"><strong>{{ item.title }}</strong><span class="conversation-meta"><time>{{ displayTime(item) }}</time></span></div>
            <div class="row-actions">
              <button v-if="!deletedMode" type="button" title="重命名" aria-label="重命名会话" @click="startRename(item)"><EditOutlined /></button>
              <button v-if="!deletedMode" type="button" title="删除" aria-label="删除会话" @click="deletingId = item.conversationId"><DeleteOutlined /></button>
              <button v-else type="button" title="恢复" aria-label="恢复会话" @click="emit('restore', item.conversationId)"><ReloadOutlined /></button>
            </div>
          </template>
          <div v-if="deletingId === item.conversationId" class="delete-confirm" role="alert">
            <span>删除后可在“已删除”中恢复</span><button type="button" @click="emit('delete', item.conversationId); deletingId = ''">确认删除</button><button type="button" @click="deletingId = ''">取消</button>
          </div>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped lang="less">
.conversation-panel { position: absolute; inset: 0; z-index: 20; display: flex; flex-direction: column; background: #0b1120; }
.history-header { min-height: 64px; display: flex; align-items: center; justify-content: space-between; padding: 10px 14px; border-bottom: 1px solid var(--border-subtle); }
.history-header div { display: grid; gap: 2px; } .history-header strong { color: var(--text-primary); font-size: 14px; } .history-header span { color: var(--text-muted); font-size: 11px; }
.history-header button, .row-actions button { width: 32px; height: 32px; display: grid; place-items: center; border: 1px solid transparent; background: transparent; color: var(--text-muted); cursor: pointer; }
.history-header button:hover, .row-actions button:hover { color: var(--text-primary); border-color: var(--border-subtle); background: rgba(148, 163, 184, 0.08); }
.history-controls { display: grid; gap: 10px; padding: 12px 14px; border-bottom: 1px solid var(--border-subtle); }
.history-tabs { display: grid; grid-template-columns: 1fr 1fr; gap: 2px; padding: 2px; background: rgba(148, 163, 184, 0.08); }
.history-tabs button { min-height: 30px; border: 0; background: transparent; color: var(--text-muted); cursor: pointer; }
.history-tabs button.active { background: #182235; color: var(--text-primary); }
.history-search { display: flex; align-items: center; gap: 8px; height: 36px; padding: 0 10px; border: 1px solid var(--border-subtle); color: var(--text-muted); background: rgba(2, 6, 23, 0.32); }
.history-search:focus-within { border-color: rgba(105, 168, 207, 0.55); }
.history-search input { min-width: 0; flex: 1; border: 0; outline: 0; background: transparent; color: var(--text-primary); }
.history-body { flex: 1; overflow-y: auto; padding: 8px 14px 18px; }
.history-empty { padding: 24px 8px; color: var(--text-muted); text-align: center; }
.history-group h3 { margin: 14px 0 6px; color: var(--text-muted); font-size: 11px; font-weight: 600; }
.conversation-row { position: relative; display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; border-bottom: 1px solid rgba(148, 163, 184, 0.1); }
.conversation-row.active { background: rgba(78, 143, 181, 0.1); box-shadow: inset 2px 0 #69a8cf; }
.conversation-main { min-width: 0; display: grid; gap: 4px; padding: 11px 8px; border: 0; background: transparent; text-align: left; cursor: pointer; }
div.conversation-main { cursor: default; }
.conversation-main strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--text-secondary); font-size: 13px; }
.conversation-meta { display: flex; align-items: center; gap: 7px; color: var(--text-muted); font-size: 10px; }
.conversation-meta em { padding-left: 7px; border-left: 1px solid var(--border-subtle); color: #83b7d6; font-style: normal; }
.row-actions { display: flex; }
.rename-row { grid-column: 1 / -1; display: grid; grid-template-columns: minmax(0, 1fr) auto auto; gap: 6px; padding: 8px; }
.rename-row input { min-width: 0; padding: 7px 8px; border: 1px solid #69a8cf; outline: 0; background: #111827; color: var(--text-primary); }
.rename-row button, .delete-confirm button { border: 1px solid var(--border-subtle); background: transparent; color: var(--text-secondary); cursor: pointer; }
.delete-confirm { grid-column: 1 / -1; display: grid; grid-template-columns: minmax(0, 1fr) auto auto; gap: 7px; align-items: center; padding: 8px; border-left: 2px solid #d66b61; background: rgba(214, 107, 97, 0.08); color: #e6aaa4; font-size: 11px; }
@media (max-width: 480px) { .delete-confirm { grid-template-columns: 1fr 1fr; } .delete-confirm span { grid-column: 1 / -1; } }
</style>
