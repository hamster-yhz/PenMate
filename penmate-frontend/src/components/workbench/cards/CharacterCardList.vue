<script setup lang="ts">
import CharacterCardItem from './CharacterCardItem.vue'

type CharacterCard = {
  cardId: number
  cardType: 'CHARACTER'
  name: string
  summary: string
  detailJson: string
  expanded: boolean
}

const props = defineProps<{
  cards: CharacterCard[]
}>()

const emit = defineEmits<{
  'create-card': []
  'toggle-expand': [{ cardId: number; expanded: boolean }]
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

<style scoped>
.character-card-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
