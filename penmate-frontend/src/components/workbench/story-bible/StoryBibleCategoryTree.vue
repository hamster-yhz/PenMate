<template>
  <div class="category-tree">
    <button type="button" :class="{ active: !modelValue }" @click="emit('update:modelValue', '')">全部分类</button>
    <button
      v-for="category in orderedCategories"
      :key="category.categoryId"
      type="button"
      :class="{ active: category.categoryId === modelValue }"
      :style="{ paddingLeft: `${10 + depth(category) * 14}px` }"
      @click="emit('update:modelValue', category.categoryId)"
    >
      {{ category.name }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { StoryBibleCategory } from '@/entities/story-bible/model'

const props = defineProps<{ categories: StoryBibleCategory[]; modelValue: string }>()
const emit = defineEmits<{ (event: 'update:modelValue', value: string): void }>()
const orderedCategories = computed(() => [...props.categories].sort((a, b) => a.sortOrder - b.sortOrder))
const depth = (category: StoryBibleCategory) => {
  let count = 0
  let parentId = category.parentCategoryId
  while (parentId && count < 4) {
    count += 1
    parentId = props.categories.find((item) => item.categoryId === parentId)?.parentCategoryId
  }
  return count
}
</script>

<style scoped lang="less">
.category-tree {
  display: grid;
}
button {
  min-height: 30px;
  padding: 4px 10px;
  border: 0;
  color: var(--text-secondary);
  background: transparent;
  text-align: left;
  cursor: pointer;
}
button:hover,
button.active {
  color: var(--accent);
  background: var(--accent-soft);
}
</style>
