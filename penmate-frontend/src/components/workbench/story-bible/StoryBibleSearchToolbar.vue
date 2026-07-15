<template>
  <div class="sb-search-toolbar">
    <SearchOutlined aria-hidden="true" />
    <input
      :value="query"
      type="search"
      placeholder="搜索节点、别名或摘要"
      @input="emit('update:query', ($event.target as HTMLInputElement).value)"
    />
    <select :value="status" aria-label="正史状态" @change="emit('update:status', ($event.target as HTMLSelectElement).value)">
      <option value="">全部状态</option>
      <option value="CANON">正史</option>
      <option value="DRAFT">草稿</option>
      <option value="ARCHIVED">已归档</option>
    </select>
    <button type="button" class="icon-button" title="新建节点" @click="emit('create')">
      <PlusOutlined />
    </button>
  </div>
</template>

<script setup lang="ts">
import { PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'

defineProps<{ query: string; status: string }>()
const emit = defineEmits<{
  (event: 'update:query', value: string): void
  (event: 'update:status', value: string): void
  (event: 'create'): void
}>()
</script>

<style scoped lang="less">
.sb-search-toolbar {
  display: grid;
  grid-template-columns: 18px minmax(120px, 1fr) 92px 32px;
  align-items: center;
  gap: 6px;
  height: 44px;
  padding: 6px 8px;
  border-bottom: 1px solid var(--border-subtle);
  color: var(--text-muted);
  background: rgba(11, 17, 32, 0.72);
}

input,
select {
  min-width: 0;
  height: 30px;
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
  color: var(--text-primary);
  background: rgba(17, 24, 39, 0.92);
  outline: none;
}

input { padding: 0 8px; }
select { padding: 0 4px; }

.icon-button {
  width: 32px;
  height: 32px;
  border: 1px solid var(--border-gold);
  border-radius: 4px;
  color: var(--amber-gold);
  background: rgba(201, 169, 110, 0.08);
  cursor: pointer;
}
</style>
