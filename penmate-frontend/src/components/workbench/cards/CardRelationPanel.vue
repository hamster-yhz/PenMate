<script setup lang="ts">
type CardOption = {
  cardId?: number | string
  name?: string
  [key: string]: any
}

type CardRelation = {
  cardRelationId?: number | string
  fromCardId?: number | string
  toCardId?: number | string
  relationType?: string
  [key: string]: any
}

const props = defineProps<{
  cards: CardOption[]
  relations: CardRelation[]
  relationFromId: string
  relationToId: string
  relationType: string
  cardNameById: (idLike: string) => string
}>()

const emit = defineEmits<{
  'update:relationFromId': [string]
  'update:relationToId': [string]
  'update:relationType': [string]
  'create-relation': []
  'delete-relation': [CardRelation]
}>()
</script>

<template>
  <div class="relation-panel">
    <div class="relation-title">关系维护</div>
    <div class="relation-create">
      <select
        :value="relationFromId"
        class="relation-select"
        data-testid="relation-from-select"
        @change="emit('update:relationFromId', ($event.target as HTMLSelectElement).value)"
      >
        <option value="">来源卡片</option>
        <option v-for="card in cards" :key="`from-${String(card.cardId)}`" :value="String(card.cardId)">
          {{ String(card.name || `卡片#${String(card.cardId)}`) }}
        </option>
      </select>

      <select
        :value="relationToId"
        class="relation-select"
        data-testid="relation-to-select"
        @change="emit('update:relationToId', ($event.target as HTMLSelectElement).value)"
      >
        <option value="">目标卡片</option>
        <option v-for="card in cards" :key="`to-${String(card.cardId)}`" :value="String(card.cardId)">
          {{ String(card.name || `卡片#${String(card.cardId)}`) }}
        </option>
      </select>

      <input
        :value="relationType"
        class="cf-input"
        data-testid="relation-type-input"
        placeholder="关系类型，如：敌对/师徒"
        @input="emit('update:relationType', ($event.target as HTMLInputElement).value)"
      />

      <button type="button" class="tree-btn" data-testid="create-relation-button" @click="emit('create-relation')">+ 新建关系</button>
    </div>

    <div class="relation-list">
      <div
        v-for="relation in relations"
        :key="String(relation.cardRelationId)"
        class="relation-item"
        :data-testid="`relation-item-${String(relation.cardRelationId)}`"
      >
        <span>
          {{ props.cardNameById(String(relation.fromCardId || '')) }}
          →
          {{ props.cardNameById(String(relation.toCardId || '')) }}
          （{{ String(relation.relationType || '关联') }}）
        </span>
        <button
          type="button"
          class="tree-act-btn danger"
          :data-testid="`delete-relation-${String(relation.cardRelationId)}`"
          @click="emit('delete-relation', relation)"
        >
          ✕
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.relation-panel,
.relation-create,
.relation-list,
.relation-item {
  display: flex;
}

.relation-panel,
.relation-list {
  flex-direction: column;
}

.relation-create,
.relation-item {
  gap: 8px;
}

.relation-list {
  gap: 8px;
}

.relation-item {
  justify-content: space-between;
}
</style>
