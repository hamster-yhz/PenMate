<template>
  <div class="plugin-workshop" v-if="visible">
    <div class="pw-backdrop" @click="$emit('close')"></div>
    <div class="pw-modal glass-panel">
      <div class="pw-glow"></div>

      <div class="pw-header">
        <img :src="iconPlugin" alt="" class="pw-icon" />
        <h3>插件工坊</h3>
        <button class="pw-close" @click="$emit('close')">✕</button>
      </div>

      <div class="pw-body">
        <p class="pw-desc">为Agent增添神通，扩展AI工具链能力</p>

        <div class="plugin-grid">
          <div
            v-for="plugin in plugins"
            :key="plugin.id"
            class="plugin-card"
            :class="{ installed: plugin.installed }"
          >
            <div class="plugin-header">
              <span class="plugin-emoji">{{ plugin.emoji }}</span>
              <div class="plugin-meta">
                <h4 class="plugin-name">{{ plugin.name }}</h4>
                <span class="plugin-author">{{ plugin.author }}</span>
              </div>
            </div>
            <p class="plugin-desc">{{ plugin.desc }}</p>
            <div class="plugin-footer">
              <div class="plugin-tags">
                <span v-for="tag in plugin.tags" :key="tag" class="p-tag">{{ tag }}</span>
              </div>
              <button
                class="btn-install"
                :class="{ active: plugin.installed }"
                @click="togglePlugin(plugin)"
              >
                {{ plugin.installed ? '已挂载' : '安装' }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <div class="pw-footer">
        <span class="pw-count">已挂载 {{ installedCount }} 个插件</span>
        <button class="btn-done" @click="$emit('close')">完 成</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import iconPlugin from '@/assets/images/feature-plugin.png'

defineProps<{ visible: boolean }>()
defineEmits(['close'])

interface Plugin {
  id: string
  emoji: string
  name: string
  author: string
  desc: string
  tags: string[]
  installed: boolean
}

const plugins = ref<Plugin[]>([
  {
    id: 'hot-words',
    emoji: '🔥',
    name: '今日热网词获取',
    author: 'PenMate官方',
    desc: '使Agent可以搜索最近网络爆火语句、热梗词汇，让创作紧贴潮流。',
    tags: ['联网', '热词', '趋势'],
    installed: true
  },
  {
    id: 'history-search',
    emoji: '📚',
    name: '历史事件与时间查询',
    author: 'PenMate官方',
    desc: '帮助考究党实时确认某朝代的细节常识，支持联网搜索历史事实。',
    tags: ['联网', '历史', '考证'],
    installed: false
  },
  {
    id: 'name-gen',
    emoji: '🏷️',
    name: '网文起名助手',
    author: 'PenMate官方',
    desc: '挂接专业词典API，处理大量特殊名字需求。支持古风、现代、奇幻等多种风格命名。',
    tags: ['起名', '词典', 'API'],
    installed: true
  },
  {
    id: 'world-build',
    emoji: '🗺️',
    name: '世界观构建器',
    author: '社区贡献',
    desc: '帮助构建系统化的世界设定，自动检测设定冲突与逻辑不一致。',
    tags: ['世界观', '逻辑', '设定'],
    installed: false
  },
  {
    id: 'plot-twist',
    emoji: '🎭',
    name: '情节反转生成器',
    author: '社区贡献',
    desc: '基于当前剧情走向生成合理且出人意料的情节反转建议。',
    tags: ['剧情', '反转', '创意'],
    installed: false
  },
  {
    id: 'poetry',
    emoji: '🎋',
    name: '古诗词引用库',
    author: 'PenMate官方',
    desc: '在适当场景自动引用或化用古典诗词，提升文学性与意境感。',
    tags: ['诗词', '引用', '文学'],
    installed: false
  }
])

const installedCount = computed(() => plugins.value.filter(p => p.installed).length)

const togglePlugin = (plugin: Plugin) => {
  plugin.installed = !plugin.installed
}
</script>

<style lang="less" scoped>
.plugin-workshop {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pw-backdrop {
  position: absolute;
  inset: 0;
  background: rgba(0,0,0,0.6);
  backdrop-filter: blur(6px);
}

.pw-modal {
  position: relative;
  width: 720px;
  max-width: 92vw;
  max-height: 85vh;
  background: rgba(17, 24, 39, 0.92);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(201,169,110,0.2);
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  animation: fadeInUp 0.35s ease;
}

.pw-glow {
  position: absolute;
  top: 0;
  left: 15%;
  right: 15%;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--amber-gold), transparent);
  opacity: 0.4;
}

.pw-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 24px;
  border-bottom: 1px solid var(--border-subtle);

  .pw-icon {
    width: 32px;
    height: 32px;
    border-radius: 8px;
    object-fit: cover;
  }

  h3 {
    flex: 1;
    font-family: var(--font-heading);
    font-size: 1.2rem;
    color: var(--xuan-paper);
    letter-spacing: 0.15em;
  }

  .pw-close {
    background: none;
    border: none;
    color: var(--text-muted);
    font-size: 1.1rem;
    cursor: pointer;
    padding: 4px 8px;
    border-radius: 4px;
    transition: all 0.2s;

    &:hover {
      color: var(--amber-gold);
      background: rgba(201,169,110,0.1);
    }
  }
}

.pw-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}

.pw-desc {
  font-size: 0.88rem;
  color: var(--text-muted);
  margin-bottom: 20px;
  letter-spacing: 0.08em;
}

.plugin-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.plugin-card {
  padding: 18px;
  background: rgba(11,17,32,0.5);
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
  transition: all 0.35s;

  &:hover {
    border-color: var(--border-gold);
    background: rgba(17,24,39,0.7);
    box-shadow: 0 0 16px rgba(201,169,110,0.06);
  }

  &.installed {
    border-color: rgba(90, 158, 111, 0.3);
    background: rgba(90, 158, 111, 0.05);
  }
}

.plugin-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.plugin-emoji {
  font-size: 1.6rem;
}

.plugin-meta {
  display: flex;
  flex-direction: column;
}

.plugin-name {
  font-family: var(--font-heading);
  font-size: 1rem;
  color: var(--xuan-paper);
  letter-spacing: 0.08em;
}

.plugin-author {
  font-size: 0.72rem;
  color: var(--text-muted);
}

.plugin-desc {
  font-size: 0.82rem;
  color: var(--text-secondary);
  line-height: 1.6;
  margin-bottom: 12px;
}

.plugin-footer {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
}

.plugin-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.p-tag {
  padding: 2px 8px;
  font-size: 0.68rem;
  color: var(--text-muted);
  background: rgba(201,169,110,0.06);
  border: 1px solid rgba(201,169,110,0.1);
  border-radius: 8px;
}

.btn-install {
  padding: 5px 14px;
  font-size: 0.78rem;
  color: var(--amber-gold);
  background: rgba(201,169,110,0.08);
  border: 1px solid rgba(201,169,110,0.2);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s;
  white-space: nowrap;

  &:hover:not(.active) {
    background: rgba(201,169,110,0.15);
    border-color: var(--border-gold);
  }

  &.active {
    color: var(--jade-green);
    background: rgba(90,158,111,0.1);
    border-color: rgba(90,158,111,0.3);
    cursor: default;
  }
}

.pw-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  border-top: 1px solid var(--border-subtle);
}

.pw-count {
  font-size: 0.82rem;
  color: var(--text-muted);
  letter-spacing: 0.05em;
}

.btn-done {
  padding: 8px 24px;
  font-family: var(--font-heading);
  font-size: 0.95rem;
  letter-spacing: 0.2em;
  color: var(--amber-gold);
  background: linear-gradient(135deg, rgba(201,169,110,0.12), rgba(201,169,110,0.04));
  border: 1px solid var(--border-gold);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s;

  &:hover {
    box-shadow: var(--shadow-gold);
    color: var(--xuan-paper);
  }
}

@media (max-width: 640px) {
  .plugin-grid {
    grid-template-columns: 1fr;
  }
}
</style>
