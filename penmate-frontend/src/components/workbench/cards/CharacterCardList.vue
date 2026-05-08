<script setup lang="ts">
import CharacterCardItem from './CharacterCardItem.vue'
import type { CharacterCard } from '@/components/workbench/workbenchTypes'

const props = defineProps<{
  cards: CharacterCard[]
}>()

const emit = defineEmits<{
  'create-card': []
  'toggle-expand': [{ cardId: string; expanded: boolean }]
  'update:card': [CharacterCard]
  save: [CharacterCard]
  delete: [CharacterCard]
}>()
</script>

<template>
  <div class="character-card-list">
    <div class="tree-actions">
      <button type="button" class="tree-btn" data-testid="create-character-card" @click="emit('create-card')">+ 新角色卡</button>
    </div>

    <div v-if="props.cards.length" class="char-list">
      <CharacterCardItem
        v-for="card in props.cards"
        :key="String(card.cardId)"
        :card="card"
        @toggle-expand="emit('toggle-expand', $event)"
        @update:card="emit('update:card', $event)"
        @save="emit('save', $event)"
        @delete="emit('delete', $event)"
      />
    </div>

    <div v-else class="empty-hint" data-testid="character-empty-hint">暂无角色卡，点击“+ 新角色卡”创建。</div>
  </div>
</template>

<style scoped lang="less">
.character-card-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tree-actions {
  display: flex;
  align-items: center;
}

.tree-btn {
  min-height: 36px;
  padding: 0 14px;
  background: rgba(17, 24, 39, 0.72);
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
  color: var(--text-primary);
  font-size: 0.82rem;
  letter-spacing: 0.06em;
  cursor: pointer;
  transition: all 0.25s var(--ease-silk);

  &:hover {
    border-color: var(--border-gold);
    background: rgba(201, 169, 110, 0.08);
    color: var(--amber-gold);
    box-shadow: 0 0 12px rgba(201, 169, 110, 0.12);
  }
}

.empty-hint {
  color: var(--text-primary);
  line-height: 1.9;
}
</style>
