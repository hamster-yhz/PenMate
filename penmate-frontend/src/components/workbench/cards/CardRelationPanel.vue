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

<style scoped lang="less">
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

.relation-panel {
  gap: 12px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid rgba(201, 169, 110, 0.14);
}

.relation-title {
  color: var(--amber-gold);
  font-size: 0.82rem;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.relation-create {
  flex-wrap: wrap;
  gap: 8px;
}

.relation-select,
.cf-input {
  min-height: 36px;
  padding: 0 12px;
  background: rgba(17, 24, 39, 0.72);
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
  color: var(--text-primary);
  font-size: 0.82rem;
  transition: border-color 0.25s var(--ease-silk), box-shadow 0.25s var(--ease-silk), background 0.25s var(--ease-silk);
}

.relation-select {
  min-width: 112px;
  flex: 1 1 148px;
  appearance: none;
  -webkit-appearance: none;
  -moz-appearance: none;
  cursor: pointer;
}

.cf-input {
  flex: 1 1 180px;
}

.relation-select:focus,
.cf-input:focus {
  outline: none;
  border-color: var(--border-gold);
  box-shadow: 0 0 0 3px rgba(201, 169, 110, 0.14);
  background: rgba(17, 24, 39, 0.88);
}

.relation-select option {
  background: rgba(17, 24, 39, 0.98);
  color: var(--text-primary);
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

.relation-list {
  gap: 8px;
}

.relation-item {
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(17, 24, 39, 0.52);
  border: 1px solid rgba(201, 169, 110, 0.12);
  color: var(--text-primary);
}

.relation-item span {
  flex: 1;
  min-width: 0;
  line-height: 1.6;
  word-break: break-word;
}

.tree-act-btn {
  flex: 0 0 auto;
  min-width: 28px;
  height: 28px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(17, 24, 39, 0.72);
  border: 1px solid var(--border-subtle);
  border-radius: 8px;
  color: var(--text-primary);
  cursor: pointer;
  transition: all 0.25s var(--ease-silk);

  &:hover {
    border-color: var(--border-gold);
    color: var(--amber-gold);
    background: rgba(201, 169, 110, 0.08);
  }

  &.danger:hover {
    border-color: rgba(248, 113, 113, 0.55);
    color: #fca5a5;
    background: rgba(127, 29, 29, 0.32);
  }
}
</style>
