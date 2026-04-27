<script setup lang="ts">
defineEmits<{
  create: []
}>()
</script>

<template>
  <div class="action-bar" data-testid="book-action-bar">
    <h2 class="page-title">我 的 书 架</h2>
    <button type="button" class="btn-new-book" data-testid="create-book-button" @click="$emit('create')">
      <span>+ 创建新书</span>
    </button>
  </div>
</template>

<style scoped lang="less">
.action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-title {
  font-family: var(--font-heading);
  font-size: 1.5rem;
  color: var(--xuan-paper);
  letter-spacing: 0.25em;
}

.btn-new-book {
  padding: 10px 24px;
  font-family: var(--font-heading);
  font-size: 0.95rem;
  letter-spacing: 0.15em;
  color: var(--amber-gold);
  background: linear-gradient(135deg, rgba(201, 169, 110, 0.15), rgba(201, 169, 110, 0.05));
  border: 1px solid var(--border-gold);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;

  &:hover {
    box-shadow: var(--shadow-gold);
    color: var(--xuan-paper);
    transform: translateY(-2px);
  }
}
</style>
