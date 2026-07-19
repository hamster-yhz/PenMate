<template>
  <nav class="sb-navigator">
    <div class="nav-section">
      <div class="section-title">语义层</div>
      <button
        v-for="family in families"
        :key="family.key"
        type="button"
        :class="{ active: selectedFamily === family.key }"
        @click="emit('update:selectedFamily', selectedFamily === family.key ? '' : family.key)"
      >
        <component :is="family.icon" />
        <span>{{ family.label }}</span>
      </button>
    </div>

    <div class="nav-section types">
      <div class="section-title">
        <span>节点类型</span>
        <button class="mini-button" type="button" title="管理类型" @click="emit('manageTypes')">
          <SettingOutlined />
        </button>
      </div>
      <button
        v-for="type in nodeTypes"
        :key="type.typeId"
        type="button"
        :class="{ active: selectedTypeId === type.typeId }"
        @click="emit('update:selectedTypeId', selectedTypeId === type.typeId ? '' : type.typeId)"
      >
        <span class="type-mark">{{ type.displayName.slice(0, 1) }}</span>
        <span>{{ type.displayName }}</span>
      </button>
    </div>

    <div class="nav-section">
      <div class="section-title">分类</div>
      <StoryBibleCategoryTree
        :categories="categories"
        :model-value="selectedCategoryId"
        @update:model-value="emit('update:selectedCategoryId', $event)"
      />
    </div>

    <div class="nav-section">
      <div class="section-title">标签</div>
      <div class="tag-list">
        <button
          v-for="tag in tags"
          :key="tag.tagId"
          type="button"
          class="tag-chip"
          :class="{ active: selectedTagId === tag.tagId }"
          @click="emit('update:selectedTagId', selectedTagId === tag.tagId ? '' : tag.tagId)"
        >
          <span class="tag-color" :style="{ backgroundColor: tag.color || '#8b93a7' }"></span>{{ tag.name }}
        </button>
      </div>
    </div>
  </nav>
</template>

<script setup lang="ts">
import {
  BookOutlined,
  EnvironmentOutlined,
  FlagOutlined,
  GlobalOutlined,
  SettingOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import type { StoryBibleCategory, StoryBibleNodeType, StoryBibleTag } from '@/api/modules/storyBible.api'
import StoryBibleCategoryTree from './StoryBibleCategoryTree.vue'

defineProps<{
  nodeTypes: StoryBibleNodeType[]
  categories: StoryBibleCategory[]
  tags: StoryBibleTag[]
  selectedFamily: string
  selectedTypeId: string
  selectedCategoryId: string
  selectedTagId: string
}>()

const emit = defineEmits<{
  (event: 'update:selectedFamily', value: string): void
  (event: 'update:selectedTypeId', value: string): void
  (event: 'update:selectedCategoryId', value: string): void
  (event: 'update:selectedTagId', value: string): void
  (event: 'manageTypes'): void
}>()

const families = [
  { key: 'CORE', label: '故事核心', icon: BookOutlined },
  { key: 'CHARACTER', label: '角色', icon: UserOutlined },
  { key: 'WORLD', label: '世界', icon: GlobalOutlined },
  { key: 'THING', label: '事物', icon: EnvironmentOutlined },
  { key: 'NARRATIVE', label: '叙事', icon: FlagOutlined },
  { key: 'TIMELINE', label: '时间线', icon: SettingOutlined },
]
</script>

<style scoped lang="less">
.sb-navigator {
  min-width: 0;
  overflow-y: auto;
  border-right: 1px solid var(--border-subtle);
  background: rgba(11, 17, 32, 0.72);
}
.nav-section {
  padding: 8px 0;
  border-bottom: 1px solid var(--border-subtle);
}
.section-title {
  min-height: 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 10px;
  color: var(--text-muted);
  font-size: 0.68rem;
  text-transform: uppercase;
}
.nav-section > button:not(.mini-button),
.types > button {
  width: 100%;
  min-height: 32px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 10px;
  border: 0;
  color: var(--text-secondary);
  background: transparent;
  text-align: left;
  cursor: pointer;
}
.nav-section > button:hover,
.nav-section > button.active {
  color: var(--amber-gold);
  background: rgba(201, 169, 110, 0.08);
}
.type-mark {
  width: 20px;
  height: 20px;
  display: grid;
  place-items: center;
  border: 1px solid var(--border-subtle);
  border-radius: 3px;
  font-size: 0.65rem;
}
.mini-button {
  width: 26px;
  height: 26px;
  border: 0;
  color: var(--text-muted);
  background: transparent;
  cursor: pointer;
}
.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  padding: 2px 8px 8px;
}
.tag-chip {
  min-height: 26px;
  display: flex;
  align-items: center;
  gap: 5px;
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
  color: var(--text-secondary);
  background: rgba(17, 24, 39, 0.7);
  cursor: pointer;
}
.tag-chip.active {
  border-color: var(--border-gold);
  color: var(--amber-gold);
}
.tag-color {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}
</style>
