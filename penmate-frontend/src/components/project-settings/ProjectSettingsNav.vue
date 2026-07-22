<template>
  <aside class="settings-nav">
    <div class="settings-heading">
      <span class="eyebrow">作品设置</span>
      <strong>{{ projectTitle || '加载中' }}</strong>
    </div>
    <nav aria-label="作品设置分区">
      <button
        v-for="item in sections"
        :key="item.key"
        type="button"
        :class="{ active: modelValue === item.key }"
        @click="emit('update:modelValue', item.key)"
      >
        <component :is="item.icon" />
        <span>{{ item.label }}</span>
      </button>
    </nav>
    <label class="mobile-section-select">
      <span>设置分区</span>
      <select
        data-testid="project-settings-mobile-section"
        aria-label="作品设置分区"
        :value="modelValue"
        @change="selectMobileSection"
      >
        <option v-for="item in sections" :key="`mobile-${item.key}`" :value="item.key">{{ item.label }}</option>
      </select>
    </label>
  </aside>
</template>

<script setup lang="ts">
import {
  DatabaseOutlined,
  DeleteOutlined,
  RobotOutlined,
  SettingOutlined,
} from '@ant-design/icons-vue'
import type { ProjectSettingsSection } from '@/features/project-settings/useProjectSettings'

defineProps<{
  modelValue: ProjectSettingsSection
  projectTitle: string
}>()

const emit = defineEmits<{ 'update:modelValue': [section: ProjectSettingsSection] }>()
const sections: Array<{ key: ProjectSettingsSection; label: string; icon: object }> = [
  { key: 'general', label: '基本信息', icon: SettingOutlined },
  { key: 'ai', label: 'AI 与上下文', icon: RobotOutlined },
  { key: 'index', label: '上下文索引', icon: DatabaseOutlined },
  { key: 'data', label: '数据管理', icon: DatabaseOutlined },
  { key: 'danger', label: '危险操作', icon: DeleteOutlined },
]
const selectMobileSection = (event: Event) => {
  emit('update:modelValue', (event.target as HTMLSelectElement).value as ProjectSettingsSection)
}
</script>
