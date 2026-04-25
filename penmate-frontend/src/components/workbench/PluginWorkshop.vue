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
                :disabled="plugin.loading"
                @click="togglePlugin(plugin)"
              >
                {{ plugin.loading ? '处理中...' : plugin.installed ? (plugin.enabled ? '已启用' : '已停用') : '安装' }}
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
import { ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import iconPlugin from '@/assets/images/feature-plugin.png'
import { pluginApi } from '@/api/modules/plugin.api'
import { getSession } from '@/stores/session'

const props = defineProps<{ visible: boolean }>()
defineEmits(['close'])
const route = useRoute()
const session = getSession()

interface Plugin {
  id: string | number
  code: string
  emoji: string
  name: string
  author: string
  desc: string
  tags: string[]
  installed: boolean
  enabled?: boolean
  loading?: boolean
}

const plugins = ref<Plugin[]>([])

const installedCount = computed(() => plugins.value.filter(p => p.installed).length)

const getProjectId = () => Number(route.query.projectId || 0)
const pickCatalogPluginId = (item: Record<string, unknown>, fallback: string) => String(item.pluginId ?? fallback)
const getOperatorId = () => {
  if (typeof session.userId === 'number' && session.userId > 0) return session.userId
  const fromQuery = Number(route.query.operatorId || 0)
  return fromQuery > 0 ? fromQuery : null
}

const toTagList = (value: unknown): string[] => {
  if (Array.isArray(value)) {
    return value.map((item) => String(item)).filter(Boolean)
  }
  const text = String(value || '')
  return text
    .split(/[，,\s]+/)
    .map((item) => item.trim())
    .filter(Boolean)
}

const loadPlugins = async () => {
  const projectId = getProjectId()
  if (!projectId) return
  try {
    const [catalogResp, installsResp] = await Promise.all([
      pluginApi.listCatalog(),
      pluginApi.listProjectPlugins(projectId)
    ])
    const catalog = (catalogResp || []) as Array<Record<string, unknown>>
    const installs = (installsResp || []) as Array<Record<string, unknown>>
    const installMap = new Map<string, Record<string, unknown>>()
    installs.forEach((item) => {
      const code = String(item.pluginCode || '')
      if (code) installMap.set(code, item)
    })

    plugins.value = catalog.map((item, idx) => {
      const code = String(item.pluginCode || item.code || '')
      const installed = installMap.get(code)
      return {
        id: pickCatalogPluginId(item, code || String(idx)),
        code,
        emoji: String(item.icon || '🧩'),
        name: String(item.name || code || '未命名插件'),
        author: String(item.vendor || item.author || 'PenMate'),
        desc: String(item.description || item.desc || ''),
        tags: toTagList(item.tags),
        installed: Boolean(installed),
        enabled: Boolean(installed?.enabled)
      }
    })
  } catch (error: any) {
    message.warning(error?.message || '加载插件列表失败')
  }
}

const togglePlugin = async (plugin: Plugin) => {
  const projectId = getProjectId()
  const operatorId = getOperatorId()
  if (!projectId || !operatorId) {
    message.warning('缺少 projectId/operatorId，无法操作插件')
    return
  }
  plugin.loading = true
  try {
    if (!plugin.installed) {
      await pluginApi.installPlugin(projectId, operatorId, {
        pluginCode: plugin.code,
        version: 'latest',
        configJson: '{}'
      })
      plugin.installed = true
      plugin.enabled = true
      message.success('插件已安装')
    } else {
      if (plugin.enabled) {
        await pluginApi.updateInstall(projectId, plugin.code, operatorId, { enabled: false })
        plugin.enabled = false
        message.success('插件已停用')
      } else {
        await pluginApi.updateInstall(projectId, plugin.code, operatorId, { enabled: true })
        plugin.enabled = true
        message.success('插件已启用')
      }
    }
  } catch (error: any) {
    message.warning(error?.message || '插件操作失败')
  } finally {
    plugin.loading = false
  }
}

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      void loadPlugins()
    }
  }
)
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
