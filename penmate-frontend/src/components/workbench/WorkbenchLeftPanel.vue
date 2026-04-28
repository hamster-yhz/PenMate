<template>
  <aside class="panel panel-left glass-panel" :class="{ collapsed }">
    <div class="panel-toggle" @click="emit('toggle-collapse')">
      {{ collapsed ? '▸' : '◂' }}
    </div>

    <div v-show="!collapsed" class="panel-content">
      <div class="left-tabs" role="tablist" aria-label="工作台左侧标签导航">
        <button
          v-for="tab in leftTabs"
          :key="tab.key"
          type="button"
          class="ltab"
          :class="{ active: activeLeftTab === tab.key }"
          role="tab"
          :id="getTabId(tab.key)"
          :aria-controls="getPanelId(tab.key)"
          :aria-selected="activeLeftTab === tab.key"
          :data-active="String(activeLeftTab === tab.key)"
          :tabindex="activeLeftTab === tab.key ? 0 : -1"
          @click="emit('update:active-left-tab', tab.key)"
          @keydown="handleTabKeydown(tab.key, $event)"
        >
          <img :src="tab.icon" alt="" class="ltab-icon" />
          <span>{{ tab.label }}</span>
        </button>
      </div>

      <div
        v-show="activeLeftTab === 'outline'"
        :id="getPanelId('outline')"
        class="tab-content"
        role="tabpanel"
        :aria-labelledby="getTabId('outline')"
      >
        <OutlineTree
          :volumes="outlineData"
          :active-chapter-key="activeChapter"
          :busy="outlineOpBusy"
          @select-chapter="emit('select-chapter', $event)"
          @rename-node="emit('rename-node', $event)"
          @move-node="emit('move-node', $event)"
          @add-volume="emit('add-volume')"
          @add-chapter="emit('add-chapter', $event)"
          @delete-volume="emit('delete-volume', $event)"
          @delete-chapter="emit('delete-chapter', $event)"
        />
      </div>

      <div
        v-show="activeLeftTab === 'characters'"
        :id="getPanelId('characters')"
        class="tab-content"
        role="tabpanel"
        :aria-labelledby="getTabId('characters')"
      >
        <CharacterCardList
          :cards="characterCards"
          @create-card="emit('create-character-card')"
          @toggle-expand="emit('toggle-card-expand', $event)"
          @update:card="emit('update-card-draft', $event)"
          @save="emit('save-card', $event)"
          @delete="emit('delete-card', $event)"
        />
      </div>

      <div
        v-show="activeLeftTab === 'world'"
        :id="getPanelId('world')"
        class="tab-content"
        role="tabpanel"
        :aria-labelledby="getTabId('world')"
      >
        <WorldCardList
          :cards="worldCards"
          @create-card="emit('create-world-card')"
          @toggle-expand="emit('toggle-card-expand', $event)"
          @update:card="emit('update-card-draft', $event)"
          @save="emit('save-card', $event)"
          @delete="emit('delete-card', $event)"
        />

        <CardRelationPanel
          v-if="projectCards.length"
          :cards="projectCards"
          :relations="cardRelations"
          :relation-from-id="relationFromId"
          :relation-to-id="relationToId"
          :relation-type="relationType"
          :card-name-by-id="(idLike) => cardNameById(String(idLike))"
          @update:relation-from-id="emit('update:relation-from-id', $event)"
          @update:relation-to-id="emit('update:relation-to-id', $event)"
          @update:relation-type="emit('update:relation-type', $event)"
          @create-relation="emit('create-relation')"
          @delete-relation="emit('delete-relation', $event)"
        />
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { nextTick } from 'vue'
import OutlineTree from '@/components/workbench/outline/OutlineTree.vue'
import CharacterCardList from '@/components/workbench/cards/CharacterCardList.vue'
import WorldCardList from '@/components/workbench/cards/WorldCardList.vue'
import CardRelationPanel from '@/components/workbench/cards/CardRelationPanel.vue'
import type { CharacterCard, WorldCard, CardRelation, WorkbenchOutlineData } from '@/components/workbench/workbenchTypes'
import type { OutlineChapterNode } from '@/composables/workbench/workbenchOutline'

interface TabItem {
  key: string
  label: string
  icon: string
}

const props = defineProps<{
  collapsed: boolean
  leftTabs: TabItem[]
  activeLeftTab: string
  outlineData: WorkbenchOutlineData
  activeChapter: string
  outlineOpBusy: boolean
  characterCards: CharacterCard[]
  worldCards: WorldCard[]
  projectCards: Array<CharacterCard | WorldCard>
  cardRelations: CardRelation[]
  relationFromId: string
  relationToId: string
  relationType: string
  cardNameById: (cardId: string) => string
}>()

const emit = defineEmits<{
  (event: 'toggle-collapse'): void
  (event: 'update:active-left-tab', payload: string): void
  (event: 'select-chapter', payload: OutlineChapterNode): void
  (event: 'rename-node', payload: unknown): void
  (event: 'move-node', payload: unknown): void
  (event: 'add-volume'): void
  (event: 'add-chapter', payload: unknown): void
  (event: 'delete-volume', payload: unknown): void
  (event: 'delete-chapter', payload: unknown): void
  (event: 'create-character-card'): void
  (event: 'create-world-card'): void
  (event: 'toggle-card-expand', payload: unknown): void
  (event: 'update-card-draft', payload: unknown): void
  (event: 'save-card', payload: unknown): void
  (event: 'delete-card', payload: unknown): void
  (event: 'update:relation-from-id', payload: string): void
  (event: 'update:relation-to-id', payload: string): void
  (event: 'update:relation-type', payload: string): void
  (event: 'create-relation'): void
  (event: 'delete-relation', payload: unknown): void
}>()

const getTabId = (tabKey: string) => `workbench-left-tab-${tabKey}`
const getPanelId = (tabKey: string) => `workbench-left-panel-${tabKey}`

const focusTabByIndex = async (currentTarget: EventTarget | null, tabIndex: number) => {
  const currentButton = currentTarget instanceof HTMLButtonElement ? currentTarget : null
  const tabList = currentButton?.closest('.left-tabs')
  if (!tabList) return

  await nextTick()

  const tabs = Array.from(tabList.querySelectorAll<HTMLButtonElement>('[role="tab"]'))
  tabs[tabIndex]?.focus()
}

const handleTabKeydown = async (tabKey: string, event: KeyboardEvent) => {
  const currentIndex = props.leftTabs.findIndex((tab) => tab.key === tabKey)
  if (currentIndex === -1) return

  let nextIndex = currentIndex

  switch (event.key) {
    case 'ArrowRight':
    case 'Right':
      nextIndex = (currentIndex + 1) % props.leftTabs.length
      break
    case 'ArrowLeft':
    case 'Left':
      nextIndex = (currentIndex - 1 + props.leftTabs.length) % props.leftTabs.length
      break
    case 'Home':
      nextIndex = 0
      break
    case 'End':
      nextIndex = props.leftTabs.length - 1
      break
    default:
      return
  }

  event.preventDefault()
  const nextTabKey = props.leftTabs[nextIndex]?.key
  if (!nextTabKey) return

  emit('update:active-left-tab', nextTabKey)
  await focusTabByIndex(event.currentTarget, nextIndex)
}
</script>

<style lang="less" scoped>
.panel {
  position: relative;
  display: flex;
  flex-direction: column;
  transition: width 0.3s var(--ease-silk);
}

.panel-toggle {
  position: absolute;
  top: 50%;
  z-index: 10;
  width: 16px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(17, 24, 39, 0.9);
  border: 1px solid var(--border-subtle);
  color: var(--text-muted);
  font-size: 0.7rem;
  cursor: pointer;
  transition: all 0.3s;

  &:hover {
    color: var(--amber-gold);
    border-color: var(--border-gold);
  }
}

.panel-content {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.panel-left {
  width: clamp(248px, 20vw, 320px);
  min-width: 0;
  border-right: 1px solid var(--border-subtle);
  background: linear-gradient(180deg, rgba(17, 24, 39, 0.72), rgba(11, 17, 32, 0.58));
  box-shadow: var(--shadow-lg), var(--shadow-gold);

  &.collapsed {
    width: 0;
    border-right: none;

    .panel-toggle {
      right: -16px;
      border-radius: 0 4px 4px 0;
    }
  }

  .panel-toggle {
    right: 0;
    top: 50%;
    transform: translateY(-50%) translateX(100%);
    border-radius: 0 4px 4px 0;
    border-left: none;
  }
}

.left-tabs {
  display: flex;
  gap: 8px;
  padding: 10px 8px 8px;
  border-bottom: 1px solid var(--border-subtle);
  flex-shrink: 0;
}

.ltab {
  position: relative;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 10px 0;
  overflow: hidden;
  background: rgba(17, 24, 39, 0.72);
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
  font-size: 0.78rem;
  color: var(--text-primary);
  cursor: pointer;
  transition: all 0.3s;
  letter-spacing: 0.05em;

  &::after {
    content: '';
    position: absolute;
    left: 10px;
    right: 10px;
    bottom: 6px;
    height: 2px;
    border-radius: 999px;
    background: linear-gradient(90deg, rgba(201, 169, 110, 0.15), rgba(201, 169, 110, 0.85), rgba(201, 169, 110, 0.15));
    opacity: 0;
    transform: scaleX(0.4);
    transition: opacity 0.3s, transform 0.3s;
  }

  &:hover {
    background: rgba(201, 169, 110, 0.06);
    color: var(--text-primary);
    border-color: var(--border-gold);
  }

  &.active {
    background: rgba(201, 169, 110, 0.12);
    color: var(--amber-gold);
    border-color: var(--border-gold);
    box-shadow: 0 0 8px rgba(201, 169, 110, 0.1);
    transform: translateY(-1px);

    &::after {
      opacity: 1;
      transform: scaleX(1);
    }
  }
}

.ltab-icon {
  width: 16px;
  height: 16px;
  border-radius: 3px;
  object-fit: cover;
}

.tab-content {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

@media (max-width: 1360px) {
  .panel-left {
    width: 248px;
  }
}
</style>
