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

<style scoped>
.world-card-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
