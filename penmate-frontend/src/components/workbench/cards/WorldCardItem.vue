<script setup lang="ts">
import { ref, watch } from 'vue'

type WorldCard = {
  cardId: number
  cardType: 'WORLD'
  name: string
  summary: string
  detailJson: string
  expanded: boolean
}

const props = defineProps<{
  card: WorldCard
}>()

const emit = defineEmits<{
  'toggle-expand': [{ cardId: number; expanded: boolean }]
  'update:card': [WorldCard]
  save: [WorldCard]
  delete: [WorldCard]
}>()

const cloneCard = (card: WorldCard): WorldCard => ({
  ...card,
})

const cardDraft = ref<WorldCard>(cloneCard(props.card))

watch(
  () => props.card,
  (value) => {
    cardDraft.value = cloneCard(value)
  },
  { deep: true },
)

const updateCard = (patch: Partial<WorldCard>) => {
  cardDraft.value = {
    ...cardDraft.value,
    ...patch,
  }

  emit('update:card', cardDraft.value)
}

const toggleExpand = () => {
  emit('toggle-expand', {
    cardId: cardDraft.value.cardId,
    expanded: !cardDraft.value.expanded,
  })
}

const saveCard = () => {
  emit('save', cardDraft.value)
}

const deleteCard = () => {
  emit('delete', cardDraft.value)
}
</script>

<template>
  <div class="world-card-item" :class="{ expanded: card.expanded }">
    <div class="world-header" data-testid="world-card-header" @click="toggleExpand">
      <span class="world-icon">🌍</span>
      <span class="world-name">{{ String(card.name || '未命名设定') }}</span>
      <div class="world-actions" @click.stop>
        <button type="button" class="tree-act-btn" data-testid="world-card-save" @click="saveCard">💾</button>
        <button type="button" class="tree-act-btn danger" data-testid="world-card-delete" @click="deleteCard">✕</button>
      </div>
      <span class="world-toggle">{{ card.expanded ? '▾' : '▸' }}</span>
    </div>

    <div v-if="card.expanded" class="world-body" data-testid="world-card-body">
      <div class="world-edit-field">
        <label>名称</label>
        <input
          :value="cardDraft.name"
          class="cf-input"
          data-testid="world-card-name-input"
          @input="updateCard({ name: ($event.target as HTMLInputElement).value })"
        />
      </div>
      <div class="world-edit-field">
        <label>摘要</label>
        <input
          :value="cardDraft.summary"
          class="cf-input"
          data-testid="world-card-summary-input"
          @input="updateCard({ summary: ($event.target as HTMLInputElement).value })"
        />
      </div>
      <div class="world-edit-field">
        <label>详情(JSON)</label>
        <textarea
          :value="cardDraft.detailJson"
          class="cf-input cf-textarea"
          data-testid="world-card-detail-input"
          rows="3"
          @input="updateCard({ detailJson: ($event.target as HTMLTextAreaElement).value })"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.world-card-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.world-header {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.world-actions,
.world-body,
.world-edit-field {
  display: flex;
}

.world-actions {
  gap: 6px;
  margin-left: auto;
}

.world-body,
.world-edit-field {
  flex-direction: column;
  gap: 6px;
}
</style>
