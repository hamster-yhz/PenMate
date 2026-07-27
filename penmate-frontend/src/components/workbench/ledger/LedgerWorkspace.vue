<template>
  <section class="ledger-workspace" aria-label="AI 台账">
    <aside class="ledger-nav">
      <div class="ledger-nav-header">
        <div><strong>AI 台账</strong><small>主要供 AI 使用的项目工作空间</small></div>
        <button class="icon-button" type="button" title="新建台账" aria-label="新建台账" @click="creating = true">
          <PlusOutlined />
        </button>
      </div>
      <form v-if="creating" class="ledger-create" @submit.prevent="createLedger">
        <input v-model="newTitle" maxlength="120" placeholder="台账标题" aria-label="台账标题" />
        <button type="submit" :disabled="busy || !newTitle.trim()"><CheckOutlined /></button>
        <button type="button" @click="creating = false"><CloseOutlined /></button>
      </form>
      <div v-if="loadingList" class="ledger-state">正在加载</div>
      <button
        v-for="ledger in items"
        :key="ledger.ledgerId"
        type="button"
        class="ledger-item"
        :class="{ active: ledger.ledgerId === selectedId }"
        @click="selectLedger(ledger.ledgerId)"
      >
        <span>{{ ledger.title }}</span><small>版本 {{ ledger.contentRevision }}</small>
      </button>
      <div v-if="!loadingList && !items.length" class="ledger-state">暂无台账</div>
    </aside>

    <main class="ledger-editor">
      <template v-if="selected">
        <header class="ledger-editor-header">
          <input
            v-model="title"
            class="ledger-title"
            maxlength="120"
            aria-label="台账标题"
            @change="saveTitle"
          />
          <span class="ledger-status" role="status">{{ aiEditing ? 'AI 正在编辑' : statusText }}</span>
          <button class="icon-button danger" type="button" title="删除台账" aria-label="删除台账" @click="confirmDelete">
            <DeleteOutlined />
          </button>
        </header>
        <div v-if="conflict" class="ledger-conflict" role="alert">
          <span>远端版本已变化，本地草稿仍保留。</span>
          <button type="button" @click="reloadRemote">重新加载远端</button>
        </div>
        <textarea
          v-model="content"
          class="ledger-content"
          :readonly="loadingContent || busy || aiEditing"
          spellcheck="true"
          aria-label="台账内容"
          @input="scheduleSave"
        ></textarea>
        <footer class="ledger-footer">
          <span>{{ characterCount.toLocaleString() }} / 200,000 字符</span>
          <span>版本 {{ revision || '-' }}</span>
        </footer>
      </template>
      <div v-else class="ledger-empty">
        <DatabaseOutlined /><strong>选择或新建台账</strong>
      </div>
    </main>
  </section>
</template>

<script setup lang="ts">
import { CheckOutlined, CloseOutlined, DatabaseOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { useLedgerWorkspace } from '@/features/workbench/useLedgerWorkspace'

const props = defineProps<{ projectId: string }>()
const {
  items, selectedId, title, content, revision, loadingList, loadingContent, busy, creating, newTitle,
  conflict, statusText, selected, characterCount, aiEditing, selectLedger, createLedger, scheduleSave,
  saveTitle, reloadRemote, confirmDelete,
} = useLedgerWorkspace(() => props.projectId)
</script>

<style scoped>
.ledger-workspace { flex: 1 1 auto; width: 0; height: 100%; display: grid; grid-template-columns: clamp(220px, 22%, 270px) minmax(0, 1fr); min-width: 0; min-height: 0; background: var(--bg-surface); border-right: 1px solid var(--border-subtle); }
.ledger-nav { min-width: 0; overflow: auto; border-right: 1px solid var(--border-subtle); background: var(--bg-subtle); }
.ledger-nav-header, .ledger-editor-header, .ledger-footer, .ledger-create { display: flex; align-items: center; }
.ledger-nav-header { justify-content: space-between; min-height: 62px; padding: 9px 12px; border-bottom: 1px solid var(--border-subtle); background: var(--bg-surface); }
.ledger-nav-header div { display: grid; min-width: 0; gap: 2px; }
.ledger-nav-header strong { font-size: 13px; }
.ledger-nav-header small, .ledger-item small, .ledger-status, .ledger-footer { color: var(--text-muted); font-size: 11px; }
.icon-button { display: grid; flex: 0 0 auto; width: 30px; height: 30px; place-items: center; padding: 0; color: var(--text-secondary); background: transparent; border: 0; border-radius: var(--radius-md); cursor: pointer; }
.icon-button:hover { background: var(--bg-hover); }
.icon-button.danger:hover { color: var(--danger); }
.ledger-create { gap: 4px; padding: 8px; border-bottom: 1px solid var(--border-subtle); }
.ledger-create input { min-width: 0; flex: 1; }
.ledger-create button { width: 28px; height: 28px; padding: 0; background: transparent; border: 0; cursor: pointer; }
.ledger-item { display: grid; width: 100%; min-height: 52px; gap: 4px; padding: 9px 13px; text-align: left; background: transparent; border: 0; border-bottom: 1px solid var(--border-subtle); cursor: pointer; }
.ledger-item span { overflow: hidden; color: var(--text-primary); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.ledger-item:hover { background: var(--bg-hover); }
.ledger-item.active { background: var(--accent-soft); box-shadow: inset 3px 0 var(--accent); }
.ledger-state { padding: 18px 12px; color: var(--text-muted); font-size: 12px; text-align: center; }
.ledger-editor { display: grid; grid-template-rows: auto auto minmax(0, 1fr) auto; min-width: 0; min-height: 0; }
.ledger-editor-header { min-height: 62px; gap: 12px; padding: 9px 16px; border-bottom: 1px solid var(--border-subtle); }
.ledger-title { min-width: 0; flex: 1; padding: 5px 6px; color: var(--text-primary); font-size: 15px; font-weight: 650; background: transparent; border: 1px solid transparent; border-radius: var(--radius-sm); }
.ledger-title:focus { background: var(--bg-subtle); border-color: var(--border-strong); outline: 0; }
.ledger-content { width: 100%; min-width: 0; min-height: 0; padding: 24px clamp(22px, 5%, 64px); resize: none; color: var(--text-primary); font: 14px/1.78 var(--font-body); background: var(--bg-surface); border: 0; outline: 0; }
.ledger-footer { justify-content: space-between; min-height: 30px; padding: 4px 12px; border-top: 1px solid var(--border-subtle); }
.ledger-conflict { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 7px 12px; color: var(--warning-text); font-size: 12px; background: var(--warning-bg); }
.ledger-conflict button { color: inherit; background: transparent; border: 0; text-decoration: underline; cursor: pointer; }
.ledger-empty { display: grid; place-content: center; gap: 8px; color: var(--text-muted); text-align: center; }
.ledger-empty :deep(.anticon) { font-size: 28px; }
@media (max-width: 720px) { .ledger-workspace { grid-template-columns: 140px minmax(0, 1fr); } .ledger-nav-header small { display: none; } .ledger-content { padding: 14px; } }
</style>
