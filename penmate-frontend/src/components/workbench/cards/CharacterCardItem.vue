<script setup lang="ts">
import { ref, watch } from 'vue'

type CharacterCard = {
  cardId: number
  cardType: 'CHARACTER'
  name: string
  summary: string
  detailJson: string
  expanded: boolean
}

const props = defineProps<{
  card: CharacterCard
}>()

const emit = defineEmits<{
  'toggle-expand': [{ cardId: number; expanded: boolean }]
  'update:card': [CharacterCard]
  save: [CharacterCard]
  delete: [CharacterCard]
}>()

const cloneCard = (card: CharacterCard): CharacterCard => ({
  ...card,
})

const cardDraft = ref<CharacterCard>(cloneCard(props.card))

watch(
  () => props.card,
  (value) => {
    cardDraft.value = cloneCard(value)
  },
  { deep: true },
)

const updateCard = (patch: Partial<CharacterCard>) => {
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
  <div class="character-card-item" :class="{ expanded: card.expanded }" :data-testid="`character-card-${card.cardId}`">
    <div class="char-header" data-testid="character-card-header" @click="toggleExpand">
      <span class="char-avatar">{{ String(card.name || '角').charAt(0) }}</span>
      <div class="char-meta">
        <span class="char-name">{{ String(card.name || '未命名角色') }}</span>
        <span class="char-role">{{ String(card.summary || '角色卡') }}</span>
      </div>
      <div class="char-actions" @click.stop>
        <button type="button" class="tree-act-btn" data-testid="character-card-save" @click="saveCard">💾</button>
        <button type="button" class="tree-act-btn danger" data-testid="character-card-delete" @click="deleteCard">✕</button>
      </div>
      <span class="char-toggle">{{ card.expanded ? '▾' : '▸' }}</span>
    </div>

    <div v-if="card.expanded" class="char-details" data-testid="character-card-details">
      <div class="char-field-edit">
        <span class="cf-label">名字</span>
        <input
          :value="card.name"
          class="cf-input"
          data-testid="character-card-name-input"
          @input="updateCard({ name: ($event.target as HTMLInputElement).value })"
        />
      </div>
      <div class="char-field-edit">
        <span class="cf-label">身份</span>
        <input
          :value="card.summary"
          class="cf-input"
          data-testid="character-card-summary-input"
          @input="updateCard({ summary: ($event.target as HTMLInputElement).value })"
        />
      </div>
      <div class="char-field-edit">
        <span class="cf-label">性格</span>
        <input
          :value="card.detailJson"
          class="cf-input"
          data-testid="character-card-detail-input"
          @input="updateCard({ detailJson: ($event.target as HTMLInputElement).value })"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.character-card-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.char-header {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.char-meta {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.char-actions {
  display: flex;
  gap: 6px;
}

.char-details {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.char-field-edit {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
/* 样式最小化，行为由测试驱动 */
</style>
