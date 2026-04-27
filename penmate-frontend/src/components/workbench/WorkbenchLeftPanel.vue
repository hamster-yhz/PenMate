<template>
  <aside class="panel panel-left glass-panel" :class="{ collapsed }">
    <div class="panel-toggle" @click="emit('toggle-collapse')">
      {{ collapsed ? '▸' : '◂' }}
    </div>

    <div v-show="!collapsed" class="panel-content">
      <div class="left-tabs">
        <button
          v-for="tab in leftTabs"
          :key="tab.key"
          class="ltab"
          :class="{ active: activeLeftTab === tab.key }"
          @click="emit('update:active-left-tab', tab.key)"
        >
          <img :src="tab.icon" alt="" class="ltab-icon" />
          <span>{{ tab.label }}</span>
        </button>
      </div>

      <div v-if="activeLeftTab === 'outline'" class="tab-content">
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

      <div v-if="activeLeftTab === 'characters'" class="tab-content">
        <CharacterCardList
          :cards="characterCards"
          @create-card="emit('create-character-card')"
          @toggle-expand="emit('toggle-card-expand', $event)"
          @update:card="emit('update-card-draft', $event)"
          @save="emit('save-card', $event)"
          @delete="emit('delete-card', $event)"
        />
      </div>

      <div v-if="activeLeftTab === 'world'" class="tab-content">
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

defineProps<{
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
  border-bottom: 1px solid var(--border-subtle);
  flex-shrink: 0;
}

.ltab {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 10px 0;
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  font-size: 0.78rem;
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.3s;
  letter-spacing: 0.05em;

  &:hover {
    color: var(--text-secondary);
  }

  &.active {
    color: var(--amber-gold);
    border-bottom-color: var(--amber-gold);
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
