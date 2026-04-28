<script setup lang="ts">
import WorldCardItem from './WorldCardItem.vue'
import type { WorldCard } from '@/components/workbench/workbenchTypes'

const props = defineProps<{
  cards: WorldCard[]
}>()

const emit = defineEmits<{
  'create-card': []
  'toggle-expand': [{ cardId: number; expanded: boolean }]
  'update:card': [WorldCard]
  save: [WorldCard]
  delete: [WorldCard]
}>()
</script>

<template>
  <div class="world-card-list">
    <div class="tree-actions">
      <button type="button" class="tree-btn" data-testid="create-world-card" @click="emit('create-card')">+ 新世界观卡</button>
    </div>

    <div v-if="props.cards.length" class="world-list">
      <WorldCardItem
        v-for="card in props.cards"
        :key="String(card.cardId)"
        :card="card"
        @toggle-expand="emit('toggle-expand', $event)"
        @update:card="emit('update:card', $event)"
        @save="emit('save', $event)"
        @delete="emit('delete', $event)"
      />
    </div>

    <div v-else class="empty-hint" data-testid="world-empty-hint">暂无资料卡，先创建角色卡或世界观卡。</div>
  </div>
</template>

<style scoped lang="less">
.world-card-list {
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
