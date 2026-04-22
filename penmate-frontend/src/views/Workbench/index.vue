<template>
  <div class="workbench-page">
    <!-- ===== Top Header Bar ===== -->
    <header class="wb-header">
      <div class="header-left">
        <img :src="logoImg" alt="PenMate" class="header-logo" @click="$router.push('/')" />
        <span class="header-brand">笔友</span>
        <div class="header-divider"></div>
        <span class="novel-title" contenteditable="true" @blur="updateTitle">{{ novelTitle }}</span>
      </div>
      <div class="header-center">
        <span class="word-count">
          <span class="wc-num">{{ wordCount }}</span> 字
        </span>
        <span class="save-hint" v-if="saveHint">{{ saveHint }}</span>
      </div>
      <div class="header-right">
        <button class="hdr-btn" @click="showStyleManager = true" title="文风设置">
          <img :src="iconStyle" alt="" class="hdr-icon" />
          <span>文风</span>
        </button>
        <button class="hdr-btn" @click="showPluginWorkshop = true" title="插件工坊">
          <img :src="iconPlugin" alt="" class="hdr-icon" />
          <span>插件</span>
        </button>
        <button class="hdr-btn" @click="showModelSettings = true" title="模型设置">
          <span class="hdr-emoji">🔑</span>
          <span>模型</span>
        </button>
        <div class="header-divider"></div>
        <!-- User Dropdown -->
        <div class="user-dropdown-wrap">
          <div class="user-avatar" @click="userMenuOpen = !userMenuOpen">
            <span>{{ username.charAt(0) }}</span>
          </div>
          <div class="user-dropdown" v-if="userMenuOpen" @mouseleave="userMenuOpen = false">
            <div class="ud-header">
              <span class="ud-name">{{ username }}</span>
              <span class="ud-email">{{ userEmail }}</span>
            </div>
            <div class="ud-sep"></div>
            <button class="ud-item" @click="$router.push('/profile'); userMenuOpen = false">👤 个人中心</button>
            <button class="ud-item" @click="$router.push('/mybooks'); userMenuOpen = false">📚 我的书架</button>
            <div class="ud-sep"></div>
            <button class="ud-item" @click="$router.push('/domain-console'); userMenuOpen = false">🧪 三域控台</button>
            <button class="ud-item danger" @click="handleLogout">🚪 退出登录</button>
          </div>
        </div>
      </div>
    </header>

    <!-- ===== Main Workspace ===== -->
    <div class="wb-main">
      <!-- === Left Panel: Resource Tree === -->
      <aside class="panel panel-left" :class="{ collapsed: leftCollapsed }">
        <div class="panel-toggle" @click="leftCollapsed = !leftCollapsed">
          {{ leftCollapsed ? '▸' : '◂' }}
        </div>

        <div class="panel-content" v-show="!leftCollapsed">
          <!-- Tabs -->
          <div class="left-tabs">
            <button
              v-for="tab in leftTabs"
              :key="tab.key"
              class="ltab"
              :class="{ active: activeLeftTab === tab.key }"
              @click="activeLeftTab = tab.key"
            >
              <img :src="tab.icon" alt="" class="ltab-icon" />
              <span>{{ tab.label }}</span>
            </button>
          </div>

          <!-- ======== Outline Tree ======== -->
          <div class="tab-content" v-if="activeLeftTab === 'outline'">
            <div class="tree-actions">
              <button class="tree-btn" :disabled="outlineOpBusy" @click="addVolume">+ 新卷</button>
              <button class="tree-btn" @click="addMemberQuick">+ 成员</button>
            </div>
            <div class="tree-root">
              <div v-for="(vol, vIdx) in outlineData" :key="vol.key" class="tree-node">
                <div
                  class="tree-item volume"
                  :class="{ expanded: vol.expanded }"
                  @click="vol.expanded = !vol.expanded"
                >
                  <span class="tree-arrow">{{ vol.expanded ? '▾' : '▸' }}</span>
                  <!-- Inline edit for volume title -->
                  <input
                    v-if="editingNodeKey === vol.key"
                    v-model="editingNodeValue"
                    class="tree-edit-input"
                    @blur="finishEditNode(vol)"
                    @keydown.enter="finishEditNode(vol)"
                    @keydown.escape="editingNodeKey = ''"
                    @click.stop
                  />
                  <span v-else class="tree-label">{{ vol.title }}</span>
                  <div class="tree-item-actions" @click.stop>
                    <button class="tree-act-btn" @click="startEditNode(vol)" title="重命名">✏️</button>
                    <button class="tree-act-btn" @click="addChapter(vol)" title="添加章节">+</button>
                    <button class="tree-act-btn" @click="moveVolume(vol, -1)" title="上移">↑</button>
                    <button class="tree-act-btn" @click="moveVolume(vol, 1)" title="下移">↓</button>
                    <button class="tree-act-btn danger" @click="deleteVolume(vIdx)" title="删除">✕</button>
                  </div>
                </div>
                <div v-if="vol.expanded" class="tree-children">
                  <div
                    v-for="(ch, cIdx) in vol.children"
                    :key="ch.key"
                    class="tree-item chapter"
                    :class="{ active: activeChapter === String(ch.chapterId || ch.key) }"
                    @click="selectChapter(ch)"
                  >
                    <span class="tree-dot">◇</span>
                    <input
                      v-if="editingNodeKey === ch.key"
                      v-model="editingNodeValue"
                      class="tree-edit-input"
                      @blur="finishEditNode(ch)"
                      @keydown.enter="finishEditNode(ch)"
                      @keydown.escape="editingNodeKey = ''"
                      @click.stop
                    />
                    <span v-else class="tree-label">{{ ch.title }}</span>
                    <div class="tree-item-actions" @click.stop>
                      <button class="tree-act-btn" @click="startEditNode(ch)" title="重命名">✏️</button>
                      <button class="tree-act-btn" @click="moveChapter(vol, cIdx, -1)" title="上移">↑</button>
                      <button class="tree-act-btn" @click="moveChapter(vol, cIdx, 1)" title="下移">↓</button>
                      <button class="tree-act-btn danger" @click="deleteChapter(vol, cIdx)" title="删除">✕</button>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div class="member-panel">
              <div class="member-title">项目成员</div>
              <div v-if="projectMembers.length" class="member-list">
                <div class="member-item" v-for="member in projectMembers" :key="String(member.userId || member.id)">
                  <span>
                    用户#{{ String(member.userId || '-') }} · 角色 {{ String(member.memberRole || '-') }}
                  </span>
                  <div class="member-actions">
                    <button class="tree-act-btn" @click="updateMemberRole(member)">改角色</button>
                    <button class="tree-act-btn danger" @click="removeMemberById(member)">移除</button>
                  </div>
                </div>
              </div>
              <div v-else class="empty-hint">暂无成员，点击“+ 成员”添加。</div>
            </div>
          </div>

          <!-- ======== Character Library ======== -->
          <div class="tab-content" v-if="activeLeftTab === 'characters'">
            <div class="tree-actions">
              <button class="tree-btn" @click="createCardQuick('CHARACTER')">+ 新角色卡</button>
            </div>
            <div class="char-list" v-if="projectCards.length">
              <div
                v-for="card in projectCards.filter((item) => String(item.cardType || '').toUpperCase() === 'CHARACTER')"
                :key="String(card.id)"
                class="char-card"
                :class="{ expanded: card.expanded }"
              >
                <div class="char-header" @click="card.expanded = !card.expanded">
                  <span class="char-avatar">{{ String(card.name || '角').charAt(0) }}</span>
                  <div class="char-meta">
                    <span class="char-name">{{ String(card.name || '未命名角色') }}</span>
                    <span class="char-role">{{ String(card.summary || '角色卡') }}</span>
                  </div>
                  <div class="char-actions" @click.stop>
                    <button class="tree-act-btn" @click="saveCard(card)" title="保存">💾</button>
                    <button class="tree-act-btn danger" @click="deleteCardById(card)" title="删除角色">✕</button>
                  </div>
                  <span class="char-toggle">{{ card.expanded ? '▾' : '▸' }}</span>
                </div>
                <div class="char-details" v-if="card.expanded">
                  <div class="char-field-edit">
                    <span class="cf-label">名字</span>
                    <input v-model="card.name" class="cf-input" />
                  </div>
                  <div class="char-field-edit">
                    <span class="cf-label">身份</span>
                    <input v-model="card.summary" class="cf-input" />
                  </div>
                  <div class="char-field-edit">
                    <span class="cf-label">性格</span>
                    <input v-model="card.detailJson" class="cf-input" placeholder="可填 JSON 或文本" />
                  </div>
                </div>
              </div>
            </div>
            <div v-else class="empty-hint">暂无角色卡，点击“+ 新角色卡”创建。</div>
          </div>

          <!-- ======== World Settings ======== -->
          <div class="tab-content" v-if="activeLeftTab === 'world'">
            <div class="tree-actions">
              <button class="tree-btn" @click="createCardQuick('WORLD')">+ 新世界观卡</button>
            </div>
            <div class="world-list" v-if="projectCards.length">
              <div
                v-for="card in projectCards.filter((item) => String(item.cardType || '').toUpperCase() === 'WORLD')"
                :key="`world-${String(card.id)}`"
                class="world-card"
              >
                <div class="world-header" @click="card.expanded = !card.expanded">
                  <span class="world-icon">🌍</span>
                  <span class="world-name">{{ String(card.name || '未命名设定') }}</span>
                  <div class="world-actions" @click.stop>
                    <button class="tree-act-btn" @click="saveCard(card)" title="保存">💾</button>
                    <button class="tree-act-btn danger" @click="deleteCardById(card)" title="删除">✕</button>
                  </div>
                  <span class="world-toggle">{{ card.expanded ? '▾' : '▸' }}</span>
                </div>
                <div class="world-body" v-if="card.expanded">
                  <div class="world-edit-field">
                    <label>名称</label>
                    <input v-model="card.name" class="cf-input" />
                  </div>
                  <div class="world-edit-field">
                    <label>摘要</label>
                    <input v-model="card.summary" class="cf-input" />
                  </div>
                  <div class="world-edit-field">
                    <label>详情(JSON)</label>
                    <textarea v-model="card.detailJson" class="cf-input cf-textarea" rows="3"></textarea>
                  </div>
                </div>
              </div>

              <div class="relation-panel">
                <div class="relation-title">关系维护</div>
                <div class="relation-create">
                  <select v-model="relationFromId" class="relation-select">
                    <option value="">来源卡片</option>
                    <option v-for="card in projectCards" :key="`from-${String(card.id)}`" :value="String(card.id)">
                      {{ String(card.name || `卡片#${String(card.id)}`) }}
                    </option>
                  </select>
                  <select v-model="relationToId" class="relation-select">
                    <option value="">目标卡片</option>
                    <option v-for="card in projectCards" :key="`to-${String(card.id)}`" :value="String(card.id)">
                      {{ String(card.name || `卡片#${String(card.id)}`) }}
                    </option>
                  </select>
                  <input v-model="relationType" class="cf-input" placeholder="关系类型，如：敌对/师徒" />
                  <button class="tree-btn" @click="createRelation">+ 新建关系</button>
                </div>
                <div class="relation-list">
                  <div class="relation-item" v-for="relation in cardRelations" :key="String(relation.id)">
                    <span>
                      {{ cardNameById(String(relation.fromCardId || '')) }}
                      →
                      {{ cardNameById(String(relation.toCardId || '')) }}
                      （{{ String(relation.relationType || '关联') }}）
                    </span>
                    <button class="tree-act-btn danger" @click="deleteRelationById(relation)">✕</button>
                  </div>
                </div>
              </div>
            </div>
            <div v-else class="empty-hint">暂无资料卡，先创建角色卡或世界观卡。</div>
          </div>
        </div>
      </aside>

      <!-- === Center: Editor === -->
      <main class="panel panel-center">
        <div class="editor-toolbar">
          <div class="toolbar-left">
            <button class="tb-btn" @click="saveContent" title="保存 (Ctrl+S)">💾</button>
            <button class="tb-btn" @click="editorUndo" title="撤销 (Ctrl+Z)">↩️</button>
            <button class="tb-btn" @click="editorRedo" title="重做 (Ctrl+Y)">↪️</button>
            <div class="tb-divider"></div>
            <button class="tb-btn" @click="wrapSelection('**','**')" title="加粗"><b>B</b></button>
            <button class="tb-btn" @click="wrapSelection('*','*')" title="斜体"><i>I</i></button>
            <button class="tb-btn" @click="insertPrefix('> ')" title="引用">❝</button>
          </div>
          <div class="toolbar-right">
            <span class="chapter-label">{{ currentChapterTitle }}</span>
            <select v-model="selectedVersionNo" class="version-select" :disabled="versionBusy || !getCurrentChapterVersions().length">
              <option value="">版本记录</option>
              <option
                v-for="version in getCurrentChapterVersions()"
                :key="String(version.id ?? version.versionNo)"
                :value="String(version.versionNo ?? '')"
              >
                v{{ version.versionNo ?? '-' }} · {{ String(version.changeReason ?? version.changeType ?? '无备注') }}
              </option>
            </select>
            <button class="tb-btn" :disabled="versionBusy || !selectedVersionNo" @click="restoreSelectedVersion">恢复版本</button>
            <button class="tb-btn" :disabled="versionBusy || !selectedVersionNo" @click="viewSelectedVersion">查看版本</button>
            <button class="tb-btn" :disabled="versionBusy || !activeChapter" @click="publishCurrentChapter">发布章节</button>
          </div>
        </div>

        <div class="editor-area">
          <textarea
            ref="editorRef"
            v-model="editorContent"
            class="main-editor"
            placeholder="在此处开始创作，或让AI为你执笔..."
            @input="onEditorInput"
            @keyup="updateCursorPos"
            @click="updateCursorPos"
            @keydown.ctrl.s.prevent="saveContent"
            @keydown.ctrl.z.prevent="editorUndo"
            @keydown.ctrl.y.prevent="editorRedo"
            @keydown.ctrl.b.prevent="wrapSelection('**','**')"
            @keydown.ctrl.i.prevent="wrapSelection('*','*')"
          ></textarea>
        </div>

        <div class="editor-statusbar">
          <span>{{ selectedText ? `已选 ${selectedText.length} 字` : '' }}</span>
          <span v-if="versionDiffSummary" class="diff-summary">{{ versionDiffSummary }}</span>
          <span>行 {{ currentLine }} · 列 {{ currentCol }}</span>
        </div>

        <div class="version-preview" v-if="selectedVersionContent">
          <div class="vp-title">版本对比预览（左：当前内容 / 右：所选版本）</div>
          <div class="vp-grid">
            <textarea class="vp-box" :value="editorContent" readonly></textarea>
            <textarea class="vp-box" :value="selectedVersionContent" readonly></textarea>
          </div>
        </div>
      </main>

      <!-- === Right Panel: Agent & Controls === -->
      <aside class="panel panel-right" :class="{ collapsed: rightCollapsed }">
        <div class="panel-toggle right-toggle" @click="rightCollapsed = !rightCollapsed">
          {{ rightCollapsed ? '◂' : '▸' }}
        </div>

        <div class="panel-content" v-show="!rightCollapsed">
          <!-- Agent Header -->
          <div class="agent-header">
            <img :src="iconAgent" alt="" class="agent-icon" />
            <span class="agent-title">AI会话</span>
            <div class="agent-model" :class="{ empty: !currentModelName }">
              {{ currentModelName || '未选择模型' }}
            </div>
            <div class="agent-status" :class="{ busy: isGenerating, failed: generationPhase === 'failed' }">
              <span class="status-dot"></span>
              <span>{{ generationStatusText }}</span>
            </div>
          </div>

          <!-- Chat Messages -->
          <div class="chat-messages" ref="chatRef">
            <div
              v-for="msg in messages"
              :key="msg.id"
              class="chat-msg"
              :class="[msg.role, { generating: msg.role === 'assistant' && msg.id === streamingAssistantMsgId && isGenerating }]"
            >
              <div class="msg-bubble">
                <div class="msg-text" v-html="msg.text"></div>
                <div
                  class="msg-inline-typing"
                  v-if="msg.role === 'assistant' && msg.id === streamingAssistantMsgId && isGenerating && !msg.text"
                >
                  <span class="t-dot"></span>
                  <span class="t-dot"></span>
                  <span class="t-dot"></span>
                  <span class="t-label">AI正在创作中...</span>
                </div>
                <div class="msg-actions" v-if="msg.role === 'assistant' && msg.text">
                  <button class="msg-btn" @click="mergeToEditor(msg)" title="合并至编辑器">
                    📥 合并
                  </button>
                  <button class="msg-btn" @click="replaceSelected(msg)" title="替换所选文本">
                    🔄 替换所选
                  </button>
                </div>
              </div>

              <!-- Approval Cards -->
              <ApprovalCard
                v-if="msg.approval"
                :card="msg.approval"
                :busy="isApprovalBusy(msg.approval.id)"
                @approve="handleApprove"
                @reject="handleReject"
              />
            </div>
          </div>

          <!-- Chat Input -->
          <div class="chat-input-area">
            <div v-if="!currentModelName" class="model-warning-inline">
              当前未选择模型，请先在模型设置中保存并切换一个可用模型。
              <button class="model-warning-btn" @click="showModelSettings = true">去选择</button>
            </div>
            <div class="input-plugins" v-if="activePlugins.length">
              <span class="ip-label">已挂载：</span>
              <span class="ip-tag" v-for="p in activePlugins" :key="p">{{ p }}</span>
            </div>
            <div class="input-model-line">
              <span class="input-model-label">当前模型：</span>
              <span :class="['input-model-value', { empty: !currentModelName }]">{{ currentModelName || '未选择模型' }}</span>
            </div>
            <div class="input-wrap">
              <textarea
                v-model="chatInput"
                class="chat-textarea"
                placeholder="输入指令，例如：开始写第三卷第二章..."
                rows="3"
                @keydown.enter.ctrl="sendMessage"
              ></textarea>
              <button
                class="btn-send"
                :disabled="!chatInput.trim() || isGenerating"
                @click="sendMessage"
              >
                <span v-if="!isGenerating">发送</span>
                <span v-else>⏳</span>
              </button>
            </div>
            <div class="input-hint">Ctrl + Enter 发送</div>
          </div>
        </div>
      </aside>
    </div>

    <!-- ===== Modals ===== -->
    <StyleManager :visible="showStyleManager" @close="showStyleManager = false" />
    <PluginWorkshop :visible="showPluginWorkshop" @close="showPluginWorkshop = false" />
    <ModelSettings :visible="showModelSettings" @close="showModelSettings = false" @saved="onModelConfigSaved" />
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted, onBeforeUnmount, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'

// Images
import logoImg from '@/assets/images/logo.png'
import iconStyle from '@/assets/images/icon-style.png'
import iconPlugin from '@/assets/images/feature-plugin.png'
import iconAgent from '@/assets/images/icon-agent.png'
import iconOutline from '@/assets/images/icon-outline.png'
import iconCharacter from '@/assets/images/icon-character.png'
import iconWorld from '@/assets/images/icon-world.png'

// Components
import StyleManager from '@/components/workbench/StyleManager.vue'
import PluginWorkshop from '@/components/workbench/PluginWorkshop.vue'
import ModelSettings from '@/components/workbench/ModelSettings.vue'
import ApprovalCard from '@/components/workbench/ApprovalCard.vue'
import type { ApprovalCardData } from '@/components/workbench/ApprovalCard.vue'
import { novelApi } from '@/api/modules/novel.api'
import { outlineApi } from '@/api/modules/outline.api'
import { chapterApi } from '@/api/modules/chapter.api'
import { cardApi } from '@/api/modules/card.api'
import { agentApi } from '@/api/modules/agent.api'
import { approvalApi } from '@/api/modules/approval.api'
import { pluginApi } from '@/api/modules/plugin.api'
import { modelApi } from '@/api/modules/model.api'
import { getSession, clearSession } from '@/stores/session'
import { authApi } from '@/api/modules/auth.api'

const router = useRouter()
const route = useRoute()
const session = getSession()

// --- State ---
const username = ref('墨客')
const userEmail = ref('moke@penmate.com')
const userMenuOpen = ref(false)
const novelTitle = ref('未命名小说')
const wordCount = ref(0)
const currentLine = ref(1)
const currentCol = ref(1)
const selectedText = ref('')
const isGenerating = ref(false)
const saveHint = ref('')

// Panel state
const leftCollapsed = ref(false)
const rightCollapsed = ref(false)
const showStyleManager = ref(false)
const showPluginWorkshop = ref(false)
const showModelSettings = ref(false)

// --- Per-chapter content storage ---
const chapterContents = ref<Record<string, string>>({})

// Editor
const editorRef = ref<HTMLTextAreaElement | null>(null)
const editorContent = ref('')
const currentChapterTitle = ref('')
const activeChapter = ref('')

// Undo/Redo stacks
const undoStack = ref<string[]>([])
const redoStack = ref<string[]>([])
let lastSnapshot = ''

// Inline editing for tree nodes
const editingNodeKey = ref('')
const editingNodeValue = ref('')

// Left panel tabs
const activeLeftTab = ref('outline')
const leftTabs = ref([
  { key: 'outline', label: '大纲', icon: iconOutline },
  { key: 'characters', label: '角色', icon: iconCharacter },
  { key: 'world', label: '世界', icon: iconWorld }
])

// Outline data
type OutlineChapterNode = { title: string; key: string; chapterId?: string }
type OutlineVolumeNode = { title: string; key: string; expanded: boolean; children: OutlineChapterNode[] }

const outlineData = ref<OutlineVolumeNode[]>([])
const outlineOpBusy = ref(false)

// Active plugins
const activePlugins = ref<string[]>([])
const activeModelConfigId = ref<number | null>(null)
const currentModelName = ref('')
const approvalBusyIds = ref<string[]>([])
type GenerationTaskStatus = 'pending' | 'running' | 'waiting_approval' | 'done' | 'applied' | 'failed' | 'cancelled'
const TERMINAL_GENERATION_STATUSES: GenerationTaskStatus[] = ['done', 'applied', 'failed', 'cancelled']
const ENABLE_POLLING_FALLBACK = String(import.meta.env.VITE_AGENT_POLLING_FALLBACK || 'false').toLowerCase() === 'true'
const LAST_PROJECT_ID_KEY = 'penmate.lastProjectId'
const LAST_OPERATOR_ID_KEY = 'penmate.lastOperatorId'
const generationPhase = ref<'idle' | 'preparing' | 'streaming' | 'waiting_approval' | 'failed'>('idle')
const generationTaskStatus = ref<GenerationTaskStatus | ''>('')
let generationStream: EventSource | null = null
const generationStatusText = computed(() => {
  if (isGenerating.value && generationTaskStatus.value) return `生成中 · ${generationTaskStatus.value}`
  if (generationPhase.value === 'preparing') return '准备中'
  if (generationPhase.value === 'streaming') return '流式生成中'
  if (generationPhase.value === 'waiting_approval') return '等待审批'
  if (generationPhase.value === 'failed') return '异常'
  return '就绪'
})

// Chat messages
interface ChatMessage {
  id: number
  role: 'user' | 'assistant' | 'system'
  text: string
  approval?: ApprovalCardData
}

const messages = ref<ChatMessage[]>([])

const chatInput = ref('')
const chatRef = ref<HTMLElement | null>(null)
let msgIdCounter = 1
const streamingAssistantMsgId = ref<number | null>(null)
const currentConversationId = ref<number | null>(null)
const chapterVersions = ref<Record<string, Array<Record<string, unknown>>>>({})
const selectedVersionNo = ref('')
const versionBusy = ref(false)
const selectedVersionContent = ref('')
const versionDiffSummary = ref('')
const projectCards = ref<Array<Record<string, any>>>([])
const cardRelations = ref<Array<Record<string, any>>>([])
const relationFromId = ref('')
const relationToId = ref('')
const relationType = ref('')
const projectMembers = ref<Array<Record<string, any>>>([])

const normalizeGenerationStatus = (raw: unknown): GenerationTaskStatus | '' => {
  const status = String(raw || '').trim().toLowerCase()
  return (['pending', 'running', 'waiting_approval', 'done', 'applied', 'failed', 'cancelled'] as const).includes(status as GenerationTaskStatus)
    ? (status as GenerationTaskStatus)
    : ''
}

const parseSseData = (event: MessageEvent<string>) => {
  try {
    return JSON.parse(event.data || '{}') as Record<string, unknown>
  } catch {
    return {} as Record<string, unknown>
  }
}

const escapeHtml = (value: string) => value
  .replaceAll('&', '&amp;')
  .replaceAll('<', '&lt;')
  .replaceAll('>', '&gt;')
  .replaceAll('"', '&quot;')
  .replaceAll("'", '&#39;')
  .replaceAll('\n', '<br/>')

const closeGenerationStream = () => {
  if (generationStream) {
    generationStream.close()
    generationStream = null
  }
}

const debugChatState = (stage: string, extra: Record<string, unknown> = {}) => {
  console.info('[agent-ui] chat-state', {
    stage,
    isGenerating: isGenerating.value,
    generationPhase: generationPhase.value,
    generationTaskStatus: generationTaskStatus.value,
    messageCount: messages.value.length,
    lastMessageRole: messages.value[messages.value.length - 1]?.role || '',
    lastMessageLength: messages.value[messages.value.length - 1]?.text?.length || 0,
    ...extra
  })
}

const pollGenerationAsFallback = async (projectId: number, taskId: number) => {
  let status: GenerationTaskStatus | '' = ''
  for (let i = 0; i < 12; i += 1) {
    await new Promise((resolve) => setTimeout(resolve, 1000))
    const latest = (await agentApi.getGeneration(projectId, taskId)) as Record<string, unknown>
    status = normalizeGenerationStatus(latest?.status)
    if (status) generationTaskStatus.value = status
    if (status && TERMINAL_GENERATION_STATUSES.includes(status)) break
  }
  return status
}

const consumeGenerationStream = (projectId: number, taskId: number, assistantMsg: ChatMessage) => new Promise<GenerationTaskStatus | ''>((resolve, reject) => {
  closeGenerationStream()
  console.info('[agent] opening SSE stream', { projectId, taskId })
  generationStream = agentApi.openGenerationStream(projectId, taskId)
  let reconnectCount = 0
  let settled = false
  let firstTokenAt = 0
  const streamOpenAt = Date.now()

  const settleResolve = (status: GenerationTaskStatus | '') => {
    if (settled) return
    settled = true
    closeGenerationStream()
    resolve(status)
  }

  const settleReject = (error: Error) => {
    if (settled) return
    settled = true
    closeGenerationStream()
    reject(error)
  }

  const startStatusCatchup = () => {
    let attempts = 0
    const timer = window.setInterval(async () => {
      if (settled) {
        window.clearInterval(timer)
        return
      }
      attempts += 1
      try {
        const latest = (await agentApi.getGeneration(projectId, taskId)) as Record<string, unknown>
        const latestStatus = normalizeGenerationStatus(latest?.status)
        if (latestStatus) generationTaskStatus.value = latestStatus
        if (latestStatus && TERMINAL_GENERATION_STATUSES.includes(latestStatus)) {
          console.info('[agent] catch-up status reached terminal', { projectId, taskId, latestStatus, attempts })
          if (latestStatus === 'failed' || latestStatus === 'cancelled') {
            settleReject(new Error(`生成任务结束：${latestStatus}`))
          } else {
            settleResolve(latestStatus)
          }
        }
      } catch {
        // 状态补拉失败时不中断主流程。
      }
      if (attempts >= 15) {
        window.clearInterval(timer)
      }
    }, 1000)
  }

  const bindListeners = () => {
    if (!generationStream) return
    agentApi.addStreamListener(generationStream, 'generation.started', () => {
      generationPhase.value = 'streaming'
      generationTaskStatus.value = 'running'
    })
    agentApi.addStreamListener(generationStream, 'generation.token', (event) => {
      const payload = parseSseData(event)
      const token = String(payload.token || '')
      if (token) {
        if (!firstTokenAt) {
          firstTokenAt = Date.now()
          console.info('[agent-ui] first-token-received', {
            projectId,
            taskId,
            firstTokenDelayMs: firstTokenAt - streamOpenAt,
            tokenLength: token.length
          })
        }
        assistantMsg.text += escapeHtml(token)
        debugChatState('append-token', { taskId, appendedTokenLength: token.length, assistantLength: assistantMsg.text.length })
        scrollChat()
      }
    })
    agentApi.addStreamListener(generationStream, 'generation.tool_call', (event) => {
      const payload = parseSseData(event)
      const pluginCode = String(payload.pluginCode || '')
      const toolName = String(payload.toolName || '')
      const status = String(payload.status || '')
      const output = String(payload.output || '')
      const errorMsg = String(payload.errorMsg || '')
      const toolMsg = status === 'failed'
        ? `工具调用失败：${pluginCode}/${toolName} - ${errorMsg || 'unknown error'}`
        : `工具调用完成：${pluginCode}/${toolName}${output ? ` -> ${output}` : ''}`
      messages.value.push({
        id: msgIdCounter++,
        role: 'system',
        text: escapeHtml(toolMsg)
      })
      scrollChat()
    })
    agentApi.addStreamListener(generationStream, 'generation.waiting_approval', (event) => {
      const payload = parseSseData(event)
      const approvalId = String(payload.approvalId || '')
      if (approvalId) {
        assistantMsg.approval = {
          id: approvalId,
          message: '检测到高风险结构化写入，需你确认后继续生成。',
          time: new Date().toLocaleTimeString('zh-CN', { hour12: false }),
          preview: {
            taskId: String(payload.taskId || taskId),
            type: String(payload.approvalType || 'WORLD_SETTING_CREATE')
          },
          resolved: false
        }
      }
      generationPhase.value = 'waiting_approval'
      generationTaskStatus.value = 'waiting_approval'
      scrollChat()
    })
    agentApi.addStreamListener(generationStream, 'generation.done', (event) => {
      const payload = parseSseData(event)
      const status = normalizeGenerationStatus(payload.status) || 'done'
      console.info('[agent] generation.done received', { projectId, taskId, status })
      generationTaskStatus.value = status
      settleResolve(status)
    })
    agentApi.addStreamListener(generationStream, 'generation.failed', (event) => {
      const payload = parseSseData(event)
      console.error('[agent] generation.failed received', { projectId, taskId, payload })
      generationTaskStatus.value = 'failed'
      settleReject(new Error(String(payload.errorMsg || payload.errorCode || '生成失败')))
    })
  }

  bindListeners()
  startStatusCatchup()

  generationStream.onerror = () => {
    if (settled) return
    console.warn('[agent] SSE onerror', { projectId, taskId, reconnectCount })
    if (reconnectCount < 1) {
      reconnectCount += 1
      closeGenerationStream()
      console.info('[agent] reopening SSE stream', { projectId, taskId, reconnectCount })
      generationStream = agentApi.openGenerationStream(projectId, taskId)
      bindListeners()
      return
    }
    settleReject(new Error('SSE 连接失败'))
  }
})

const resolveOperatorId = () => {
  if (typeof session.userId === 'number' && session.userId > 0) return session.userId
  const queryId = Number(route.query.operatorId || route.query.userId || 0)
  if (queryId > 0) return queryId
  const cachedId = Number(localStorage.getItem(LAST_OPERATOR_ID_KEY) || 0)
  return cachedId > 0 ? cachedId : null
}

const getCurrentProjectId = () => {
  const queryId = Number(route.query.bookId || route.query.projectId || 0)
  if (queryId > 0) return queryId
  const cachedId = Number(localStorage.getItem(LAST_PROJECT_ID_KEY) || 0)
  return cachedId > 0 ? cachedId : 0
}

const getContext = () => {
  const projectId = getCurrentProjectId()
  const operatorId = resolveOperatorId()
  if (projectId > 0) localStorage.setItem(LAST_PROJECT_ID_KEY, String(projectId))
  if (operatorId && operatorId > 0) localStorage.setItem(LAST_OPERATOR_ID_KEY, String(operatorId))
  return { projectId, operatorId }
}

const toPluginName = (item: Record<string, unknown>) => {
  const name = String(item.pluginName || item.name || item.pluginCode || '').trim()
  return name || '未命名插件'
}

const loadActivePlugins = async () => {
  const projectId = getCurrentProjectId()
  if (!projectId) {
    activePlugins.value = []
    return
  }
  try {
    const installs = (await pluginApi.listProjectPlugins(projectId)) as Array<Record<string, unknown>>
    activePlugins.value = installs
      .filter((item) => item.enabled !== false)
      .map(toPluginName)
  } catch {
    activePlugins.value = []
  }
}

const ensureConversationId = async (projectId: number, operatorId: number) => {
  if (currentConversationId.value) return currentConversationId.value
  const conversations = (await agentApi.listConversations(projectId)) as Array<Record<string, unknown>>
  const existing = conversations[0]
  if (existing?.id) {
    currentConversationId.value = Number(existing.id)
    return currentConversationId.value
  }
  const created = (await agentApi.createConversation(projectId, operatorId, {
    userId: operatorId,
    title: 'Workbench 会话',
    contextScopeJson: '{}',
    status: 'ACTIVE'
  })) as Record<string, unknown>
  currentConversationId.value = Number(created?.id || 0) || null
  return currentConversationId.value
}

const refreshActiveModelInfo = async (projectId: number) => {
  if (!projectId) {
    activeModelConfigId.value = null
    currentModelName.value = ''
    return null
  }
  try {
    const configs = (await modelApi.listConfigs(projectId)) as Array<Record<string, unknown>>
    const preferred = configs.find((item) => Boolean(item.isDefault)) || configs[0]
    const modelConfigId = Number(preferred?.id || 0)
    activeModelConfigId.value = modelConfigId > 0 ? modelConfigId : null
    currentModelName.value = String(preferred?.modelName || '').trim()
    return activeModelConfigId.value
  } catch {
    activeModelConfigId.value = null
    currentModelName.value = ''
    return null
  }
}

const ensureModelConfigId = async (projectId: number) => {
  // 完全显式模式：每次生成都携带模型配置ID；这里负责读取当前可用配置供发送时显式传参。
  return refreshActiveModelInfo(projectId)
}

const onModelConfigSaved = () => {
  // 模型设置变更后刷新当前生效模型信息。
  void refreshActiveModelInfo(Number(route.query.bookId || 0))
}

const cardNameById = (idLike: string) => {
  const hit = projectCards.value.find((item) => String(item.id) === String(idLike))
  return String(hit?.name || `卡片#${idLike}`)
}

const loadCardsAndRelations = async (projectId: number) => {
  if (!projectId) return
  try {
    const [cards, relations] = await Promise.all([cardApi.listCards(projectId), cardApi.listCardRelations(projectId)])
    projectCards.value = ((cards || []) as Array<Record<string, any>>).map((item) => ({ ...item, expanded: false }))
    cardRelations.value = (relations || []) as Array<Record<string, any>>
  } catch {
    projectCards.value = []
    cardRelations.value = []
  }
}

const loadProjectMembers = async (projectId: number) => {
  if (!projectId) return
  try {
    const list = (await novelApi.listMembers(projectId)) as Array<Record<string, unknown>>
    projectMembers.value = Array.isArray(list) ? (list as Array<Record<string, any>>) : []
  } catch {
    projectMembers.value = []
  }
}

const addMemberQuick = async () => {
  const { projectId, operatorId } = getContext()
  if (!projectId || !operatorId) {
    message.warning('缺少 projectId/operatorId，无法添加成员')
    return
  }
  const userIdText = window.prompt('请输入成员 userId（数字）', '')
  const userId = Number(userIdText || 0)
  if (!userId) {
    message.warning('userId 非法')
    return
  }
  const memberRole = String(window.prompt('请输入成员角色（如 EDITOR/OWNER/VIEWER）', 'EDITOR') || '').trim()
  if (!memberRole) {
    message.warning('成员角色不能为空')
    return
  }
  try {
    await novelApi.addMember(projectId, operatorId, { userId, memberRole })
    await loadProjectMembers(projectId)
    message.success('成员已添加')
  } catch (error: any) {
    message.warning(error?.message || '添加成员失败')
  }
}

const updateMemberRole = async (member: Record<string, any>) => {
  const { projectId, operatorId } = getContext()
  const userId = Number(member.userId || member.id || 0)
  if (!projectId || !operatorId || !userId) {
    message.warning('成员信息不完整，无法更新')
    return
  }
  const nextRole = String(window.prompt('请输入新的成员角色', String(member.memberRole || 'EDITOR')) || '').trim()
  if (!nextRole) {
    message.warning('成员角色不能为空')
    return
  }
  try {
    await novelApi.updateMember(projectId, userId, operatorId, { memberRole: nextRole })
    await loadProjectMembers(projectId)
    message.success('成员角色已更新')
  } catch (error: any) {
    message.warning(error?.message || '更新成员失败')
  }
}

const removeMemberById = async (member: Record<string, any>) => {
  const { projectId, operatorId } = getContext()
  const userId = Number(member.userId || member.id || 0)
  if (!projectId || !operatorId || !userId) {
    message.warning('成员信息不完整，无法移除')
    return
  }
  if (!window.confirm(`确认移除成员 userId=${userId} 吗？`)) return
  try {
    await novelApi.removeMember(projectId, userId, operatorId)
    await loadProjectMembers(projectId)
    message.success('成员已移除')
  } catch (error: any) {
    message.warning(error?.message || '移除成员失败')
  }
}

const normalizeCardType = (value: unknown) => {
  const normalized = String(value || '').trim().toUpperCase()
  return normalized === 'CHARACTER' || normalized === 'WORLD' ? normalized : ''
}

const normalizeDetailJsonInput = (value: unknown) => {
  const text = String(value ?? '').trim()
  if (!text) return ''
  try {
    return JSON.stringify(JSON.parse(text))
  } catch {
    return null
  }
}

const createCardQuick = async (cardType: 'CHARACTER' | 'WORLD') => {
  const { projectId, operatorId } = getContext()
  if (!projectId || !operatorId) {
    message.warning('缺少 projectId/operatorId，无法创建资料卡')
    return
  }
  const normalizedType = normalizeCardType(cardType)
  if (!normalizedType) {
    message.warning('卡片类型非法，仅支持 CHARACTER/WORLD')
    return
  }
  const defaultName = normalizedType === 'CHARACTER' ? '新角色' : '新世界设定'
  const enteredName = window.prompt('请输入卡片名称（必填）', defaultName)
  const trimmedName = String(enteredName ?? '').trim()
  if (!trimmedName) {
    message.warning('卡片名称不能为空')
    return
  }
  try {
    await cardApi.createCard(projectId, operatorId, {
      cardType: normalizedType,
      name: trimmedName,
      summary: '',
      detailJson: '{}'
    })
    await loadCardsAndRelations(projectId)
  } catch (error: any) {
    message.warning(error?.message || '创建资料卡失败')
  }
}

const saveCard = async (card: Record<string, any>) => {
  const { projectId, operatorId } = getContext()
  if (!projectId || !operatorId || !card?.id) return
  const cardType = normalizeCardType(card.cardType)
  if (!cardType) {
    message.warning('卡片类型非法，仅支持 CHARACTER/WORLD')
    return
  }
  const cardName = String(card.name || '').trim()
  if (!cardName) {
    message.warning('卡片名称不能为空')
    return
  }
  const detailJson = normalizeDetailJsonInput(card.detailJson)
  if (detailJson === null) {
    message.warning('详情(JSON)格式不合法，请输入合法 JSON')
    return
  }
  try {
    await cardApi.updateCard(projectId, Number(card.id), operatorId, {
      cardType,
      name: cardName,
      summary: card.summary,
      detailJson
    })
    message.success('资料卡已保存')
  } catch (error: any) {
    message.warning(error?.message || '保存资料卡失败')
  }
}

const deleteCardById = async (card: Record<string, any>) => {
  const { projectId, operatorId } = getContext()
  if (!projectId || !operatorId || !card?.id) return
  try {
    await cardApi.deleteCard(projectId, Number(card.id), operatorId)
    await loadCardsAndRelations(projectId)
  } catch (error: any) {
    message.warning(error?.message || '删除资料卡失败')
  }
}

const createRelation = async () => {
  const { projectId, operatorId } = getContext()
  const fromCardId = Number(relationFromId.value)
  const toCardId = Number(relationToId.value)
  const relationTypeValue = relationType.value.trim()
  if (!projectId || !operatorId || !fromCardId || !toCardId || !relationTypeValue) {
    message.warning('请补全来源/目标/关系类型')
    return
  }
  try {
    await cardApi.createCardRelation(projectId, operatorId, {
      fromCardId,
      toCardId,
      relationType: relationTypeValue,
      description: ''
    })
    relationType.value = ''
    await loadCardsAndRelations(projectId)
  } catch (error: any) {
    message.warning(error?.message || '创建关系失败')
  }
}

const deleteRelationById = async (relation: Record<string, any>) => {
  const { projectId, operatorId } = getContext()
  if (!projectId || !operatorId || !relation?.id) return
  try {
    await cardApi.deleteCardRelation(projectId, Number(relation.id), operatorId)
    await loadCardsAndRelations(projectId)
  } catch (error: any) {
    message.warning(error?.message || '删除关系失败')
  }
}

const toRecord = (value: unknown) => (value && typeof value === 'object' ? (value as Record<string, unknown>) : {})

const pickString = (obj: Record<string, unknown>, keys: string[]) => {
  for (const key of keys) {
    const value = obj[key]
    if (typeof value === 'string' && value.trim()) return value
  }
  return ''
}

const normalizeObjectStorageUrl = (rawUrl: string) => {
  const url = String(rawUrl || '').trim()
  if (!url) return ''
  if (/^[a-zA-Z][a-zA-Z\d+.-]*:/.test(url)) return url
  if (url.startsWith('//')) return `${window.location.protocol}${url}`
  if (url.startsWith('/')) return url
  const defaultProtocol = String(import.meta.env.VITE_STORAGE_URL_PROTOCOL || 'https').replace(/:$/, '')
  if (/^(localhost|127\.0\.0\.1|\[::1\]|[\w.-]+)(:\d+)?(\/|$)/i.test(url)) {
    return `${defaultProtocol}://${url}`
  }
  return url
}

const hasObjectKeyInStorageUrl = (rawUrl: string, marker: '/read/' | '/upload/') => {
  const url = String(rawUrl || '').trim()
  if (!url) return false
  try {
    const parsed = new URL(url, window.location.origin)
    const path = parsed.pathname || ''
    const idx = path.indexOf(marker)
    if (idx < 0) return true
    return path.slice(idx + marker.length).trim().length > 0
  } catch {
    if (url.endsWith(marker)) return false
    return true
  }
}

const loadChapterVersions = async (projectId: number, chapterId: string) => {
  const numericChapterId = Number(chapterId)
  if (!projectId || !numericChapterId) return
  try {
    const versions = await chapterApi.listVersions(projectId, numericChapterId)
    const versionList = Array.isArray(versions) ? (versions as Array<Record<string, unknown>>) : []
    chapterVersions.value[chapterId] = versionList
    if (chapterId === activeChapter.value) {
      const firstVersionNo = versionList[0]?.versionNo
      selectedVersionNo.value = firstVersionNo != null ? String(firstVersionNo) : ''
    }
  } catch {
    chapterVersions.value[chapterId] = []
    if (chapterId === activeChapter.value) {
      selectedVersionNo.value = ''
    }
  }
}

const getCurrentChapterVersions = () => chapterVersions.value[activeChapter.value] || []

const viewSelectedVersion = async () => {
  const projectId = getCurrentProjectId()
  const chapterId = Number(activeChapter.value)
  const versionNo = Number(selectedVersionNo.value)
  if (!projectId || !chapterId || !versionNo) {
    message.warning('请选择有效版本后再查看')
    return
  }
  versionBusy.value = true
  try {
    const snapshotResp = toRecord(await chapterApi.getVersionSnapshotUrl(projectId, chapterId, versionNo))
    const url = normalizeObjectStorageUrl(pickString(snapshotResp, ['downloadUrl', 'url', 'getUrl']))
    if (!url || !hasObjectKeyInStorageUrl(url, '/read/')) throw new Error('版本快照地址为空')
    const response = await fetch(url)
    if (!response.ok) throw new Error('读取版本快照失败')
    const text = await response.text()
    selectedVersionContent.value = text
    const currentLen = editorContent.value.length
    const versionLen = text.length
    const delta = versionLen - currentLen
    versionDiffSummary.value = `当前 ${currentLen} 字 / 版本 ${versionLen} 字 / 差值 ${delta >= 0 ? '+' : ''}${delta}`
  } catch (error: any) {
    selectedVersionContent.value = ''
    versionDiffSummary.value = ''
    message.warning(error?.message || '查看版本失败')
  } finally {
    versionBusy.value = false
  }
}

const getDraftStorageKey = (projectId: number, chapterId: string | number) => `penmate.chapterDraft.${projectId}.${chapterId}`

const saveChapterDraftLocal = (projectId: number, chapterId: string | number, content: string) => {
  try {
    localStorage.setItem(getDraftStorageKey(projectId, chapterId), content)
  } catch {
    // 忽略浏览器存储异常
  }
}

const readChapterDraftLocal = (projectId: number, chapterId: string | number) => {
  try {
    return localStorage.getItem(getDraftStorageKey(projectId, chapterId)) || ''
  } catch {
    return ''
  }
}

const refreshEditorFromRemote = async (projectId: number, chapterId: number) => {
  const contentResp = toRecord(await chapterApi.getContentUrl(projectId, chapterId))
  const downloadUrl = normalizeObjectStorageUrl(pickString(contentResp, ['downloadUrl', 'url', 'getUrl']))
  if (!downloadUrl || !hasObjectKeyInStorageUrl(downloadUrl, '/read/')) return false
  const response = await fetch(downloadUrl)
  if (!response.ok) return false
  const text = await response.text()
  editorContent.value = text
  chapterContents.value[activeChapter.value] = text
  wordCount.value = text.replace(/\s/g, '').length
  lastSnapshot = text
  saveChapterDraftLocal(projectId, chapterId, text)
  return true
}

const tryLoadChapterRemoteContent = async (chapterIdLike: string) => {
  const projectId = getCurrentProjectId()
  const chapterId = Number(chapterIdLike)
  if (!projectId || !chapterId) return
  try {
    const loaded = await refreshEditorFromRemote(projectId, chapterId)
    if (!loaded) {
      const localDraft = readChapterDraftLocal(projectId, chapterId)
      if (localDraft) {
        editorContent.value = localDraft
        chapterContents.value[String(chapterId)] = localDraft
        wordCount.value = localDraft.replace(/\s/g, '').length
        lastSnapshot = localDraft
      }
    }
  } catch {
    const localDraft = readChapterDraftLocal(projectId, chapterId)
    if (localDraft) {
      editorContent.value = localDraft
      chapterContents.value[String(chapterId)] = localDraft
      wordCount.value = localDraft.replace(/\s/g, '').length
      lastSnapshot = localDraft
    }
  }
}

const restoreSelectedVersion = async () => {
  const projectId = getCurrentProjectId()
  const chapterId = Number(activeChapter.value)
  const operatorId = resolveOperatorId()
  const versionNo = Number(selectedVersionNo.value)
  if (!projectId || !chapterId || !operatorId || !versionNo) {
    message.warning('缺少 projectId/chapterId/operatorId/versionNo，无法恢复版本')
    return
  }
  versionBusy.value = true
  try {
    await chapterApi.restoreVersion(projectId, chapterId, versionNo, operatorId)
    await refreshEditorFromRemote(projectId, chapterId)
    await loadChapterVersions(projectId, String(chapterId))
    selectedVersionContent.value = ''
    versionDiffSummary.value = ''
    message.success(`已恢复到版本 v${versionNo}`)
  } catch (error: any) {
    message.warning(error?.message || '恢复版本失败')
  } finally {
    versionBusy.value = false
  }
}

const publishCurrentChapter = async () => {
  const projectId = getCurrentProjectId()
  const chapterId = Number(activeChapter.value)
  const operatorId = resolveOperatorId()
  if (!projectId || !chapterId || !operatorId) {
    message.warning('缺少 projectId/chapterId/operatorId，无法发布章节')
    return
  }
  versionBusy.value = true
  try {
    // 发布前先执行一次“原保存接口流程”：上传正文并提交对象元数据。
    await uploadAndCommitContent(projectId, chapterId, editorContent.value, operatorId)
    await loadChapterVersions(projectId, String(chapterId))
    await chapterApi.publishChapter(projectId, chapterId, operatorId)
    message.success('章节已发布')
  } catch (error: any) {
    message.warning(error?.message || '发布章节失败')
  } finally {
    versionBusy.value = false
  }
}

const uploadAndCommitContent = async (projectId: number, chapterId: number, content: string, operatorId: number) => {
  const uploadResp = toRecord(await chapterApi.getContentUploadUrl(projectId, chapterId))
  const uploadUrl = normalizeObjectStorageUrl(pickString(uploadResp, ['uploadUrl', 'url', 'putUrl']))
  const objectKey = pickString(uploadResp, ['objectKey', 'key'])
  const storageProvider = pickString(uploadResp, ['storageProvider', 'provider']) || 's3'
  // 仅走前端直传：必须同时具备 objectKey + uploadUrl。
  if (!objectKey) {
    throw new Error('上传地址响应缺少 objectKey')
  }
  if (!uploadUrl) {
    throw new Error('上传地址响应缺少 uploadUrl')
  }

  const size = new Blob([content]).size
  let etag = ''
  let checksum = ''

  let uploadResponse: Response
  try {
    uploadResponse = await fetch(uploadUrl, {
      method: 'PUT',
      headers: {
        'Content-Type': 'text/plain; charset=utf-8'
      },
      body: content
    })
  } catch {
    throw new Error('直传 OSS 请求失败，请检查网络/CORS/预检配置')
  }

  if (!uploadResponse.ok) {
    throw new Error(`直传 OSS 失败(${uploadResponse.status})`)
  }

  etag = (uploadResponse.headers.get('etag') || '').replace(/"/g, '').trim()
  checksum = (uploadResponse.headers.get('x-amz-checksum-crc32') || '').trim()

  await chapterApi.commitContent(projectId, chapterId, operatorId, {
    objectKey,
    etag,
    size,
    checksum,
    storageProvider
  })

  await chapterApi.createVersion(projectId, chapterId, {
    changeType: 'MANUAL_SAVE',
    changeReason: '前端手动保存',
    createdBy: operatorId
  })
}

// ===================== METHODS =====================

const updateTitle = (e: Event) => {
  const target = e.target as HTMLElement
  const nextTitle = String(target.textContent || '').trim() || '未命名小说'
  novelTitle.value = nextTitle
  const projectId = getCurrentProjectId()
  if (!projectId) return
  void novelApi.updateProject(projectId, { title: nextTitle }).catch(() => undefined)
}

// --- Editor Input / Cursor ---
const onEditorInput = () => {
  chapterContents.value[activeChapter.value] = editorContent.value
  wordCount.value = editorContent.value.replace(/\s/g, '').length
  const projectId = getCurrentProjectId()
  if (projectId && activeChapter.value) {
    saveChapterDraftLocal(projectId, activeChapter.value, editorContent.value)
  }
  if (editorContent.value !== lastSnapshot) {
    undoStack.value.push(lastSnapshot)
    if (undoStack.value.length > 50) undoStack.value.shift()
    redoStack.value = []
    lastSnapshot = editorContent.value
  }
}

const updateCursorPos = () => {
  if (!editorRef.value) return
  const el = editorRef.value
  const pos = el.selectionStart
  const text = editorContent.value.slice(0, pos)
  const lines = text.split('\n')
  currentLine.value = lines.length
  currentCol.value = (lines[lines.length - 1]?.length || 0) + 1
  const start = el.selectionStart
  const end = el.selectionEnd
  selectedText.value = start !== end ? editorContent.value.slice(start, end) : ''
}

// --- Undo / Redo ---
const editorUndo = () => {
  if (undoStack.value.length === 0) return
  redoStack.value.push(editorContent.value)
  const prev = undoStack.value.pop()!
  editorContent.value = prev
  lastSnapshot = prev
  chapterContents.value[activeChapter.value] = prev
  wordCount.value = prev.replace(/\s/g, '').length
}

const editorRedo = () => {
  if (redoStack.value.length === 0) return
  undoStack.value.push(editorContent.value)
  const next = redoStack.value.pop()!
  editorContent.value = next
  lastSnapshot = next
  chapterContents.value[activeChapter.value] = next
  wordCount.value = next.replace(/\s/g, '').length
}

// --- Text formatting ---
const wrapSelection = (before: string, after: string) => {
  if (!editorRef.value) return
  const el = editorRef.value
  const start = el.selectionStart
  const end = el.selectionEnd
  const selected = editorContent.value.slice(start, end)
  const replacement = before + (selected || '文本') + after
  editorContent.value = editorContent.value.slice(0, start) + replacement + editorContent.value.slice(end)
  chapterContents.value[activeChapter.value] = editorContent.value
  wordCount.value = editorContent.value.replace(/\s/g, '').length
  nextTick(() => {
    el.focus()
    const newPos = start + before.length + (selected || '文本').length
    el.setSelectionRange(start + before.length, newPos)
  })
}

const insertPrefix = (prefix: string) => {
  if (!editorRef.value) return
  const el = editorRef.value
  const pos = el.selectionStart
  const lineStart = editorContent.value.lastIndexOf('\n', pos - 1) + 1
  editorContent.value = editorContent.value.slice(0, lineStart) + prefix + editorContent.value.slice(lineStart)
  chapterContents.value[activeChapter.value] = editorContent.value
  wordCount.value = editorContent.value.replace(/\s/g, '').length
  nextTick(() => {
    el.focus()
    el.setSelectionRange(pos + prefix.length, pos + prefix.length)
  })
}

// --- Chapter switching ---
const selectChapter = async (ch: { key: string; title: string; chapterId?: string }) => {
  const chapterKey = String(ch.chapterId || ch.key)
  const prevProjectId = getCurrentProjectId()
  if (prevProjectId && activeChapter.value) {
    saveChapterDraftLocal(prevProjectId, activeChapter.value, editorContent.value)
  }
  chapterContents.value[activeChapter.value] = editorContent.value
  activeChapter.value = chapterKey
  currentChapterTitle.value = ch.title
  const localDraft = getCurrentProjectId() ? readChapterDraftLocal(getCurrentProjectId(), chapterKey) : ''
  editorContent.value = chapterContents.value[chapterKey] || localDraft || ''
  wordCount.value = editorContent.value.replace(/\s/g, '').length
  undoStack.value = []
  redoStack.value = []
  lastSnapshot = editorContent.value
  loadChapterVersions(getCurrentProjectId(), chapterKey)
  await tryLoadChapterRemoteContent(chapterKey)
  nextTick(() => editorRef.value?.focus())
}

// --- Outline CRUD ---
const addVolume = async () => {
  if (outlineOpBusy.value) return
  const { projectId, operatorId } = getContext()
  if (!projectId || !operatorId) {
    message.warning('缺少 projectId/operatorId，无法新建分卷')
    return
  }
  const idx = outlineData.value.length
  const nums = ['一','二','三','四','五','六','七','八','九','十']
  const title = `第${nums[idx] || idx + 1}卷：新的篇章`
  outlineOpBusy.value = true
  try {
    await outlineApi.createNode(projectId, operatorId, {
      parentId: null,
      title,
      nodeType: 'VOLUME',
      sortOrder: idx + 1,
      content: ''
    })
    await loadWorkbenchData()
  } catch (error: any) {
    message.warning(error?.message || '新建分卷失败')
  } finally {
    outlineOpBusy.value = false
  }
}

const addChapter = async (vol: any) => {
  const { projectId, operatorId } = getContext()
  if (!projectId || !operatorId) {
    message.warning('缺少 projectId/operatorId，无法新建章节')
    return
  }
  const volumeNodeId = Number(vol?.key)
  if (!volumeNodeId) {
    message.warning('分卷节点ID异常，无法创建章节')
    return
  }
  const idx = vol.children.length
  const title = `第${idx + 1}章：未命名`
  try {
    const chapterNo = idx + 1
    const createdOutline = await outlineApi.createNode(projectId, operatorId, {
      parentId: volumeNodeId,
      title,
      nodeType: 'CHAPTER',
      sortOrder: idx + 1,
      content: ''
    }) as Record<string, any>

    await novelApi.createChapter(projectId, operatorId, {
      volumeId: null,
      outlineNodeId: Number(createdOutline.id ?? createdOutline.nodeId ?? 0) || null,
      title,
      chapterNo,
      status: 1,
      wordCount: 0,
      excerpt: ''
    })

    await loadWorkbenchData()
    message.success('章节已创建')
  } catch (error: any) {
    message.warning(error?.message || '新建章节失败')
  }
}

const deleteVolume = async (vIdx: number) => {
  const { projectId, operatorId } = getContext()
  const vol = outlineData.value[vIdx]
  if (projectId && operatorId && Number(vol?.key)) {
    try {
      await outlineApi.deleteNode(projectId, Number(vol.key), operatorId)
      await loadWorkbenchData()
      return
    } catch (error: any) {
      message.warning(error?.message || '删除分卷失败')
    }
  }
}

const deleteChapter = async (vol: any, cIdx: number) => {
  const { projectId, operatorId } = getContext()
  const ch = vol.children[cIdx]
  if (projectId && operatorId && Number(ch?.key)) {
    try {
      if (Number(ch?.chapterId)) {
        await novelApi.deleteChapter(projectId, Number(ch.chapterId), operatorId)
      }
      await outlineApi.deleteNode(projectId, Number(ch.key), operatorId)
      await loadWorkbenchData()
      return
    } catch (error: any) {
      message.warning(error?.message || '删除章节失败')
    }
  }
}

// --- Inline node rename ---
const startEditNode = (node: { key: string; title: string }) => {
  editingNodeKey.value = node.key
  editingNodeValue.value = node.title
}

const finishEditNode = (node: { key: string; title: string }) => {
  const nextTitle = editingNodeValue.value.trim()
  if (nextTitle) {
    node.title = nextTitle
  }
  editingNodeKey.value = ''
  if (node.key === activeChapter.value) {
    currentChapterTitle.value = node.title
  }
  const { projectId, operatorId } = getContext()
  if (projectId && operatorId && Number(node.key) && nextTitle) {
    outlineApi.updateNode(projectId, Number(node.key), operatorId, { title: nextTitle }).catch(() => undefined)
  }
}

const moveVolume = async (vol: any, direction: -1 | 1) => {
  const currentIdx = outlineData.value.findIndex((item: any) => item.key === vol.key)
  const targetIdx = currentIdx + direction
  if (currentIdx < 0 || targetIdx < 0 || targetIdx >= outlineData.value.length) return
  const { projectId, operatorId } = getContext()
  if (projectId && operatorId && Number(vol.key)) {
    await outlineApi.moveNode(projectId, Number(vol.key), operatorId, {
      parentId: null,
      sortOrder: targetIdx + 1
    }).catch(() => undefined)
  }
  const [item] = outlineData.value.splice(currentIdx, 1)
  outlineData.value.splice(targetIdx, 0, item)
}

const moveChapter = async (vol: any, cIdx: number, direction: -1 | 1) => {
  const targetIdx = cIdx + direction
  if (targetIdx < 0 || targetIdx >= vol.children.length) return
  const ch = vol.children[cIdx]
  const { projectId, operatorId } = getContext()
  if (projectId && operatorId && Number(ch?.key)) {
    await outlineApi.moveNode(projectId, Number(ch.key), operatorId, {
      parentId: Number(vol.key) || null,
      sortOrder: targetIdx + 1
    }).catch(() => undefined)
  }
  const [item] = vol.children.splice(cIdx, 1)
  vol.children.splice(targetIdx, 0, item)
}

// --- Save ---
const saveContent = async () => {
  chapterContents.value[activeChapter.value] = editorContent.value
  saveHint.value = '⌛ 保存中...'

  const projectId = getCurrentProjectId()
  const chapterId = Number(activeChapter.value)

  // 需求调整：保存仅落本地草稿，不触发服务端上传/提交。
  if (projectId && chapterId) {
    saveChapterDraftLocal(projectId, chapterId, editorContent.value)
  } else if (projectId && activeChapter.value) {
    saveChapterDraftLocal(projectId, activeChapter.value, editorContent.value)
  }
  saveHint.value = '✓ 已本地保存'
  setTimeout(() => { saveHint.value = '' }, 2000)
}

// --- Chat merge/replace ---
const mergeToEditor = (msg: ChatMessage) => {
  editorContent.value += '\n\n' + msg.text.replace(/<[^>]*>/g, '')
  chapterContents.value[activeChapter.value] = editorContent.value
  wordCount.value = editorContent.value.replace(/\s/g, '').length
}

const replaceSelected = (msg: ChatMessage) => {
  if (!editorRef.value) return
  const el = editorRef.value
  const start = el.selectionStart
  const end = el.selectionEnd
  if (start === end) return
  const plainText = msg.text.replace(/<[^>]*>/g, '')
  editorContent.value = editorContent.value.slice(0, start) + plainText + editorContent.value.slice(end)
  chapterContents.value[activeChapter.value] = editorContent.value
  wordCount.value = editorContent.value.replace(/\s/g, '').length
}

// --- Chat send ---
const revealAssistantText = async (assistantMsg: ChatMessage, rawText: string) => {
  const text = String(rawText || '')
  if (!text) {
    assistantMsg.text = ''
    return
  }
  assistantMsg.text = ''
  const chunkSize = Math.max(10, Math.floor(text.length / 120))
  for (let i = 0; i < text.length; i += chunkSize) {
    assistantMsg.text = escapeHtml(text.slice(0, i + chunkSize))
    await nextTick()
    scrollChat()
    await new Promise((resolve) => setTimeout(resolve, 16))
  }
}

const sendMessage = async () => {
  if (!chatInput.value.trim() || isGenerating.value) return
  const userText = chatInput.value.trim()
  const userMsg: ChatMessage = { id: msgIdCounter++, role: 'user', text: userText }
  messages.value.push(userMsg)
  chatInput.value = ''
  isGenerating.value = true
  generationPhase.value = 'preparing'
  generationTaskStatus.value = ''
  debugChatState('user-send-start', { userTextLength: userText.length })
  await nextTick()
  scrollChat()
  const { projectId, operatorId } = getContext()
  if (!projectId || !operatorId) {
    console.warn('[agent] missing context, skip backend calls', { projectId, operatorId, routeQuery: { ...route.query } })
    await new Promise(r => setTimeout(r, 800))
    messages.value.push({
      id: msgIdCounter++,
      role: 'assistant',
      text: '缺少 projectId/operatorId，当前仅可本地预览消息。'
    })
    isGenerating.value = false
    generationPhase.value = 'idle'
    generationTaskStatus.value = ''
    await nextTick()
    scrollChat()
    return
  }

  try {
    console.info('[agent] start send flow', { projectId, operatorId })
    const conversationId = await ensureConversationId(projectId, operatorId)
    if (!conversationId) throw new Error('会话初始化失败')
    const modelConfigId = await ensureModelConfigId(projectId)
    if (!modelConfigId) {
      showModelSettings.value = true
      throw new Error('未选择可用模型，请先在模型设置中保存并切换模型')
    }

    await agentApi.createMessage(projectId, conversationId, operatorId, {
      role: 'user',
      userMessageType: 'COMMAND',
      contentMd: userText,
      attachmentsJson: '[]',
      toolCallsJson: '[]'
    })

    const generation = (await agentApi.createGeneration(projectId, operatorId, {
      conversationId,
      chapterId: Number(activeChapter.value) || null,
      modelConfigId,
      taskType: 'WRITE',
      promptSnapshot: userText,
      styleProfileSnapshot: '',
      pluginSnapshot: JSON.stringify(activePlugins.value || [])
    })) as Record<string, unknown>

    const taskId = Number(generation?.id || 0)
    if (!taskId) throw new Error('任务创建失败，缺少 taskId')
    console.info('[agent] generation created', { projectId, conversationId, taskId, status: generation?.status })

    generationTaskStatus.value = normalizeGenerationStatus(generation?.status) || 'pending'
    generationPhase.value = 'streaming'

    const assistantMsg: ChatMessage = { id: msgIdCounter++, role: 'assistant', text: '' }
    messages.value.push(assistantMsg)
    streamingAssistantMsgId.value = assistantMsg.id
    debugChatState('assistant-placeholder-created', { assistantMsgId: assistantMsg.id })
    await nextTick()
    scrollChat()

    let finalStatus: GenerationTaskStatus | ''
    let streamedTokenReceived = false
    try {
      finalStatus = await consumeGenerationStream(projectId, taskId, assistantMsg)
      streamedTokenReceived = Boolean(assistantMsg.text)
    } catch (streamError: any) {
      if (!ENABLE_POLLING_FALLBACK) {
        throw streamError
      }
      finalStatus = await pollGenerationAsFallback(projectId, taskId)
      if (!assistantMsg.text) {
        const messageList = (await agentApi.listMessages(projectId, conversationId)) as Array<Record<string, unknown>>
        const latestAssistant = [...messageList].reverse().find((item) => String(item.role || '').toLowerCase() === 'assistant')
        const latestText = String(latestAssistant?.contentMd || '')
        if (latestText) {
          await revealAssistantText(assistantMsg, latestText)
        }
      }
    }

    if (!assistantMsg.text) {
      try {
        const messageList = (await agentApi.listMessages(projectId, conversationId)) as Array<Record<string, unknown>>
        const latestAssistant = [...messageList].reverse().find((item) => String(item.role || '').toLowerCase() === 'assistant')
        const latestText = String(latestAssistant?.contentMd || '')
        if (latestText) {
          if (streamedTokenReceived) {
            assistantMsg.text = escapeHtml(latestText)
          } else {
            await revealAssistantText(assistantMsg, latestText)
          }
        }
      } catch {
        // 消息补拉失败时使用后续兜底文案。
      }
    }

    if (!assistantMsg.text) {
      assistantMsg.text = `生成任务已完成，状态：${finalStatus || generationTaskStatus.value || 'unknown'}`
    }

    if (finalStatus === 'failed' || finalStatus === 'cancelled') {
      throw new Error(`生成任务结束：${finalStatus}`)
    }
  } catch (error: any) {
    generationPhase.value = 'failed'
    generationTaskStatus.value = 'failed'
    messages.value.push({
      id: msgIdCounter++,
      role: 'assistant',
      text: `生成失败：${String(error?.message || '未知错误')}`
    })
  } finally {
    closeGenerationStream()
    isGenerating.value = false
    streamingAssistantMsgId.value = null
    if (generationPhase.value !== 'failed') {
      generationPhase.value = 'idle'
      generationTaskStatus.value = ''
    }
    debugChatState('send-flow-finished')
    await nextTick()
    scrollChat()
  }
}

const scrollChat = () => {
  if (chatRef.value) chatRef.value.scrollTop = chatRef.value.scrollHeight
}

// --- Approval ---
const isApprovalBusy = (id: string) => approvalBusyIds.value.includes(id)

const handleApprove = async (id: string) => {
  if (isApprovalBusy(id)) return
  const msg = messages.value.find(m => m.approval?.id === id)
  if (!msg?.approval) return
  const { projectId, operatorId } = getContext()
  const approvalId = Number(id)
  if (!projectId || !operatorId || approvalId <= 0) {
    message.warning('缺少审批上下文，无法完成操作')
    return
  }
  approvalBusyIds.value.push(id)
  try {
    await approvalApi.approve(projectId, approvalId, { reviewedBy: operatorId, comment: '前端审批通过' })
    msg.approval.resolved = true
    msg.approval.resolvedAction = 'approved'
  } catch (error: any) {
    message.warning(error?.message || '审批通过失败')
  } finally {
    approvalBusyIds.value = approvalBusyIds.value.filter((item) => item !== id)
  }
}

const handleReject = async (id: string) => {
  if (isApprovalBusy(id)) return
  const msg = messages.value.find(m => m.approval?.id === id)
  if (!msg?.approval) return
  const { projectId, operatorId } = getContext()
  const approvalId = Number(id)
  if (!projectId || !operatorId || approvalId <= 0) {
    message.warning('缺少审批上下文，无法完成操作')
    return
  }
  approvalBusyIds.value.push(id)
  try {
    await approvalApi.reject(projectId, approvalId, { reviewedBy: operatorId, comment: '前端审批拒绝' })
    msg.approval.resolved = true
    msg.approval.resolvedAction = 'rejected'
  } catch (error: any) {
    message.warning(error?.message || '审批拒绝失败')
  } finally {
    approvalBusyIds.value = approvalBusyIds.value.filter((item) => item !== id)
  }
}

// --- Auth ---
const handleLogout = () => {
  userMenuOpen.value = false
  authApi.logout().catch(() => undefined).finally(() => {
    clearSession()
    router.push('/login')
  })
}

// --- Init ---
const mapOutlineTree = (
  nodes: Array<Record<string, any>>,
  chapterByOutlineNodeId: Record<string, string> = {}
) => {
  const volumeMap = new Map<string, {
    title: string
    key: string
    expanded: boolean
    children: Array<{ title: string; key: string; chapterId?: string }>
  }>()
  nodes.forEach((node) => {
    const key = String(node.id ?? node.nodeId ?? node.key ?? '')
    const title = String(node.title ?? node.name ?? '未命名')
    const nodeType = String(node.nodeType ?? node.type ?? '').toUpperCase()
    // 仅按明确的 VOLUME 节点构建卷，避免 parentId=0/null 边界导致章节被误判为卷。
    if (nodeType.includes('VOLUME')) {
      volumeMap.set(key, { title, key, expanded: true, children: [] })
    }
  })

  nodes.forEach((node) => {
    const key = String(node.id ?? node.nodeId ?? node.key ?? '')
    const title = String(node.title ?? node.name ?? '未命名章节')
    const parentId = node.parentId ?? node.parentNodeId
    if (parentId != null) {
      const pKey = String(parentId)
      const parent = volumeMap.get(pKey)
      if (parent) {
        parent.children.push({ title, key, chapterId: chapterByOutlineNodeId[key] })
      }
    }
  })

  const values = Array.from(volumeMap.values())
  return values.length ? values : outlineData.value
}

const loadWorkbenchData = async () => {
  const projectId = Number(route.query.bookId || 0)
  if (!projectId) return
  try {
    chapterContents.value = {}
    outlineData.value = []
    activeChapter.value = ''
    currentChapterTitle.value = ''
    editorContent.value = ''

    const [project, outlines, chapters] = await Promise.all([
      novelApi.getProject(projectId),
      outlineApi.listOutlineTree(projectId),
      novelApi.listChapters(projectId)
    ])

    novelTitle.value = String((project as Record<string, any>)?.title ?? novelTitle.value)

    const chapterList = (chapters || []) as Array<Record<string, any>>
    const chapterByOutlineNodeId: Record<string, string> = {}
    chapterList.forEach((chapter) => {
      const key = String(chapter.chapterId ?? chapter.id ?? chapter.key ?? '')
      if (!key) return
      // 正文应走 OSS content-url 获取；这里不再回填 chapter.content，避免把后端 HTML 占位内容灌进编辑器。
      const chapterText = ''
      const localDraft = readChapterDraftLocal(projectId, key)
      chapterContents.value[key] = chapterText || localDraft || ''
      const outlineNodeId = String(chapter.outlineNodeId ?? chapter.nodeId ?? '')
      if (outlineNodeId) {
        chapterByOutlineNodeId[outlineNodeId] = key
      }
    })

    outlineData.value = mapOutlineTree((outlines || []) as Array<Record<string, any>>, chapterByOutlineNodeId)
    await loadCardsAndRelations(projectId)
    await loadProjectMembers(projectId)
    const first = outlineData.value[0]?.children?.[0]
    if (first) {
      activeChapter.value = String(first.chapterId || first.key)
      currentChapterTitle.value = first.title
      editorContent.value = chapterContents.value[String(first.chapterId || first.key)] || ''
      wordCount.value = editorContent.value.replace(/\s/g, '').length
      lastSnapshot = editorContent.value
      await tryLoadChapterRemoteContent(String(first.chapterId || first.key))
      await loadChapterVersions(projectId, String(first.chapterId || first.key))
    }
  } catch (error: any) {
    message.warning(error?.message || '工作台数据加载失败')
  }
}

onMounted(() => {
  void refreshActiveModelInfo(Number(route.query.bookId || 0))
  if (session.userName) username.value = session.userName
  if (session.userEmail) userEmail.value = session.userEmail
  editorContent.value = chapterContents.value[activeChapter.value] || ''
  wordCount.value = editorContent.value.replace(/\s/g, '').length
  lastSnapshot = editorContent.value
  loadWorkbenchData()
  loadActivePlugins()
})

onBeforeUnmount(() => {
  closeGenerationStream()
})

watch(
  () => showPluginWorkshop.value,
  (visible, prevVisible) => {
    if (prevVisible && !visible) {
      void loadActivePlugins()
    }
  }
)
</script>

<style lang="less" scoped>
/* ============================================
   Workbench - Ancient IDE Layout
   ============================================ */

.workbench-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  background: var(--bg-primary);
}

/* ---------- Header ---------- */
.wb-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 48px;
  padding: 0 16px;
  background: rgba(11, 17, 32, 0.95);
  border-bottom: 1px solid var(--border-subtle);
  flex-shrink: 0;
  z-index: 50;
}

.header-left, .header-center, .header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-logo {
  width: 28px; height: 28px; border-radius: 50%;
  object-fit: cover; cursor: pointer; transition: transform 0.3s;
  &:hover { transform: scale(1.1); }
}

.header-brand {
  font-family: var(--font-heading); font-size: 1rem;
  color: var(--amber-gold); letter-spacing: 0.2em;
}

.header-divider { width: 1px; height: 20px; background: var(--border-subtle); }

.novel-title {
  font-family: var(--font-heading); font-size: 0.95rem;
  color: var(--text-primary); letter-spacing: 0.1em;
  padding: 2px 8px; border-radius: 4px; outline: none; transition: background 0.3s;
  &:hover, &:focus { background: rgba(201,169,110,0.06); }
}

.word-count { font-size: 0.78rem; color: var(--text-muted); letter-spacing: 0.05em;
  .wc-num { color: var(--amber-gold); font-weight: 500; }
}

.save-hint {
  font-size: 0.72rem; color: var(--jade-green);
  animation: fadeInUp 0.3s ease;
}

.hdr-btn {
  display: flex; align-items: center; gap: 4px;
  padding: 4px 10px; background: none; border: 1px solid transparent;
  border-radius: 4px; font-size: 0.78rem; color: var(--text-secondary);
  cursor: pointer; transition: all 0.3s; letter-spacing: 0.05em;
  &:hover { border-color: var(--border-gold); color: var(--amber-gold); background: rgba(201,169,110,0.06); }
  .hdr-icon { width: 18px; height: 18px; border-radius: 3px; object-fit: cover; }
  .hdr-emoji { font-size: 0.9rem; }
}

/* User Dropdown */
.user-dropdown-wrap { position: relative; }

.user-avatar {
  width: 30px; height: 30px; border-radius: 50%;
  background: linear-gradient(135deg, rgba(201,169,110,0.3), rgba(201,169,110,0.1));
  border: 1px solid var(--border-gold);
  display: flex; align-items: center; justify-content: center;
  font-family: var(--font-heading); font-size: 0.85rem; color: var(--amber-gold);
  cursor: pointer; transition: all 0.3s;
  &:hover { box-shadow: 0 0 12px rgba(201,169,110,0.2); }
}

.user-dropdown {
  position: absolute; top: 100%; right: 0; margin-top: 8px;
  width: 200px; background: rgba(17,24,39,0.95);
  backdrop-filter: blur(16px);
  border: 1px solid var(--border-gold);
  border-radius: 10px; overflow: hidden;
  box-shadow: 0 8px 32px rgba(0,0,0,0.4);
  animation: fadeInUp 0.2s ease;
  z-index: 999;
}

.ud-header { padding: 12px 14px; }
.ud-name { display: block; font-family: var(--font-heading); font-size: 0.92rem; color: var(--xuan-paper); letter-spacing: 0.1em; }
.ud-email { display: block; font-size: 0.72rem; color: var(--text-muted); margin-top: 2px; }
.ud-sep { height: 1px; background: var(--border-subtle); }

.ud-item {
  display: flex; align-items: center; gap: 8px;
  width: 100%; padding: 10px 14px; background: none; border: none;
  font-size: 0.82rem; color: var(--text-secondary); cursor: pointer;
  text-align: left; transition: all 0.2s;
  &:hover { background: rgba(201,169,110,0.06); color: var(--amber-gold); }
  &.danger { color: #e8a87c; &:hover { background: rgba(192,60,45,0.08); } }
}

/* ---------- Main Workspace ---------- */
.wb-main { flex: 1; display: flex; overflow: hidden; }

/* ---------- Panels ---------- */
.panel { position: relative; display: flex; flex-direction: column; transition: width 0.3s var(--ease-silk); }

.panel-toggle {
  position: absolute; top: 50%; z-index: 10;
  width: 16px; height: 40px;
  display: flex; align-items: center; justify-content: center;
  background: rgba(17,24,39,0.9); border: 1px solid var(--border-subtle);
  color: var(--text-muted); font-size: 0.7rem; cursor: pointer; transition: all 0.3s;
  &:hover { color: var(--amber-gold); border-color: var(--border-gold); }
}

.panel-content { flex: 1; overflow: hidden; display: flex; flex-direction: column; }

/* --- Left Panel --- */
.panel-left {
  width: clamp(248px, 20vw, 320px); min-width: 0;
  border-right: 1px solid var(--border-subtle);
  background: rgba(11,17,32,0.6);
  &.collapsed { width: 0; border-right: none;
    .panel-toggle { right: -16px; border-radius: 0 4px 4px 0; }
  }
  .panel-toggle {
    right: 0; top: 50%;
    transform: translateY(-50%) translateX(100%);
    border-radius: 0 4px 4px 0; border-left: none;
  }
}

.left-tabs { display: flex; border-bottom: 1px solid var(--border-subtle); flex-shrink: 0; }

.ltab {
  flex: 1; display: flex; align-items: center; justify-content: center; gap: 4px;
  padding: 10px 0; background: none; border: none;
  border-bottom: 2px solid transparent; font-size: 0.78rem;
  color: var(--text-muted); cursor: pointer; transition: all 0.3s; letter-spacing: 0.05em;
  &:hover { color: var(--text-secondary); }
  &.active { color: var(--amber-gold); border-bottom-color: var(--amber-gold); }
}

.ltab-icon { width: 16px; height: 16px; border-radius: 3px; object-fit: cover; }

.tab-content { flex: 1; overflow-y: auto; padding: 8px; }

.tree-actions { padding: 4px 0 8px; }

.member-panel {
  margin-top: 10px;
  padding: 8px;
  border: 1px solid var(--border-subtle);
  border-radius: 8px;
  background: rgba(11,17,32,0.35);
}

.member-title {
  font-size: 0.78rem;
  color: var(--amber-gold);
  margin-bottom: 6px;
  letter-spacing: 0.05em;
}

.member-list { display: flex; flex-direction: column; gap: 6px; }

.member-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 0.74rem;
  color: var(--text-secondary);
  padding: 6px 8px;
  border: 1px solid var(--border-subtle);
  border-radius: 6px;
}

.member-actions { display: flex; gap: 4px; }

.tree-btn {
  padding: 4px 12px; font-size: 0.75rem;
  color: var(--amber-gold); background: rgba(201,169,110,0.06);
  border: 1px solid rgba(201,169,110,0.15); border-radius: 4px;
  cursor: pointer; transition: all 0.3s;
  &:hover { background: rgba(201,169,110,0.12); border-color: var(--border-gold); }
}

/* Tree styles */
.tree-item {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 8px; border-radius: 4px;
  font-size: 0.82rem; color: var(--text-secondary);
  cursor: pointer; transition: all 0.2s;
  &:hover { background: rgba(201,169,110,0.06); color: var(--text-primary);
    .tree-item-actions { opacity: 1; }
  }
  &.active { background: rgba(201,169,110,0.1); color: var(--amber-gold); }
}

.tree-children { padding-left: 12px; }
.tree-arrow, .tree-dot { font-size: 0.65rem; color: var(--text-muted); min-width: 12px; }
.tree-label { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* Inline edit input */
.tree-edit-input {
  flex: 1; padding: 2px 6px;
  background: rgba(11,17,32,0.7);
  border: 1px solid var(--border-gold);
  border-radius: 3px; color: var(--text-primary);
  font-size: 0.82rem; outline: none;
}

/* Tree item action buttons */
.tree-item-actions {
  display: flex; gap: 2px; opacity: 0; transition: opacity 0.2s;
}

.tree-act-btn {
  padding: 1px 5px; background: none;
  border: 1px solid transparent; border-radius: 3px;
  font-size: 0.65rem; cursor: pointer; transition: all 0.2s;
  color: var(--text-muted);
  &:hover { color: var(--amber-gold); border-color: var(--border-subtle); background: rgba(201,169,110,0.06); }
  &.danger:hover { color: #e8a87c; border-color: rgba(192,60,45,0.3); background: rgba(192,60,45,0.08); }
}

/* Character cards */
.char-list { display: flex; flex-direction: column; gap: 6px; }

.char-card {
  border: 1px solid var(--border-subtle); border-radius: 8px; overflow: hidden;
  transition: border-color 0.3s;
  &:hover, &.expanded { border-color: rgba(201,169,110,0.2); }
}

.char-header { display: flex; align-items: center; gap: 8px; padding: 8px 10px; cursor: pointer; }

.char-avatar {
  width: 28px; height: 28px; border-radius: 50%;
  background: linear-gradient(135deg, rgba(201,169,110,0.2), rgba(90,158,111,0.15));
  border: 1px solid rgba(201,169,110,0.25);
  display: flex; align-items: center; justify-content: center;
  font-family: var(--font-heading); font-size: 0.82rem; color: var(--amber-gold);
}

.char-meta { flex: 1; display: flex; flex-direction: column; }
.char-name { font-size: 0.82rem; color: var(--text-primary); }
.char-role { font-size: 0.68rem; color: var(--text-muted); }
.char-toggle { font-size: 0.65rem; color: var(--text-muted); }

.char-actions {
  opacity: 0; transition: opacity 0.2s;
  .char-header:hover & { opacity: 1; }
}

.char-details { padding: 6px 10px 10px; display: flex; flex-direction: column; gap: 6px; }

/* Editable character fields */
.char-field-edit {
  display: flex; align-items: flex-start; gap: 6px; font-size: 0.78rem;
}

.cf-label {
  color: var(--amber-gold); min-width: 32px; flex-shrink: 0;
  padding-top: 4px;
  &::after { content: '：'; }
}

.cf-input {
  flex: 1; padding: 4px 8px;
  background: rgba(11,17,32,0.5);
  border: 1px solid var(--border-subtle);
  border-radius: 4px; color: var(--text-primary);
  font-family: var(--font-body); font-size: 0.78rem;
  outline: none; transition: border-color 0.3s;
  &:focus { border-color: var(--border-gold); }
  &::placeholder { color: var(--text-muted); }
}

.cf-textarea { resize: vertical; line-height: 1.5; }
.cf-icon { max-width: 60px; }

/* World settings */
.world-list { display: flex; flex-direction: column; gap: 6px; }

.world-card {
  border: 1px solid var(--border-subtle); border-radius: 8px; overflow: hidden;
  &:hover { border-color: rgba(201,169,110,0.2); }
}

.world-header {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 10px; cursor: pointer;
}

.world-icon { font-size: 1rem; }
.world-name { flex: 1; font-size: 0.82rem; color: var(--text-primary); }
.world-toggle { font-size: 0.65rem; color: var(--text-muted); }

.world-actions {
  opacity: 0; transition: opacity 0.2s;
  .world-header:hover & { opacity: 1; }
}

.world-body { padding: 6px 10px 10px; }

.world-edit-field {
  display: flex; flex-direction: column; gap: 3px; margin-bottom: 8px;
  label { font-size: 0.72rem; color: var(--amber-gold); letter-spacing: 0.05em; }
}

/* --- Center Panel: Editor --- */
.panel-center {
  flex: 1; min-width: 0;
  display: flex; flex-direction: column;
  background: rgba(11,17,32,0.3);
}

.editor-toolbar {
  display: flex; justify-content: space-between; align-items: center;
  padding: 6px 16px;
  border-bottom: 1px solid var(--border-subtle);
  background: rgba(11,17,32,0.5);
  flex-shrink: 0;
}

.toolbar-left { display: flex; align-items: center; gap: 4px; }

.tb-btn {
  padding: 4px 8px; background: none;
  border: 1px solid transparent; border-radius: 3px;
  font-size: 0.82rem; color: var(--text-secondary); cursor: pointer;
  transition: all 0.2s;
  &:hover {
    border-color: var(--border-subtle); background: rgba(201,169,110,0.05);
    color: var(--text-primary);
  }
  &:active { background: rgba(201,169,110,0.12); }
}

.tb-divider { width: 1px; height: 16px; background: var(--border-subtle); margin: 0 4px; }

.chapter-label {
  font-family: var(--font-heading); font-size: 0.82rem;
  color: var(--text-muted); letter-spacing: 0.1em;
}

.version-select {
  min-width: 168px;
  max-width: 240px;
  height: 28px;
  padding: 0 8px;
  background: rgba(11,17,32,0.6);
  color: var(--text-secondary);
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
  font-size: 0.74rem;
  outline: none;
  &:focus {
    border-color: var(--border-gold);
  }
}

.editor-area { flex: 1; overflow: hidden; padding: 0; }

.main-editor {
  width: 100%;
  max-width: 960px;
  height: 100%;
  margin: 0 auto;
  padding: 28px 56px;
  background: transparent; border: none; resize: none; outline: none;
  color: var(--text-primary); font-family: var(--font-body);
  font-size: 1rem; line-height: 1.95; letter-spacing: 0.02em;
  &::placeholder {
    color: var(--text-muted); font-family: var(--font-heading); letter-spacing: 0.1em;
  }
}

.editor-statusbar {
  display: flex; justify-content: space-between;
  padding: 4px 16px;
  border-top: 1px solid var(--border-subtle);
  font-size: 0.7rem; color: var(--text-muted); flex-shrink: 0;
}

/* --- Right Panel: Agent --- */
.panel-right {
  width: clamp(320px, 26vw, 420px); min-width: 0;
  border-left: 1px solid var(--border-subtle);
  background: rgba(11,17,32,0.5);
  &.collapsed { width: 0; border-left: none;
    .right-toggle { left: -16px; border-radius: 4px 0 0 4px; }
  }
  .right-toggle {
    left: 0; top: 50%;
    transform: translateY(-50%) translateX(-100%);
    border-radius: 4px 0 0 4px; border-right: none;
  }
}

.agent-header {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 14px; border-bottom: 1px solid var(--border-subtle); flex-shrink: 0;
}

.agent-icon { width: 24px; height: 24px; border-radius: 6px; object-fit: cover; }

.agent-title {
  flex: 1; font-family: var(--font-heading); font-size: 0.95rem;
  color: var(--xuan-paper); letter-spacing: 0.1em;
}

.agent-status {
  display: flex; align-items: center; gap: 4px; font-size: 0.7rem; color: var(--jade-green);
  .status-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--jade-green); box-shadow: 0 0 6px var(--jade-green); }

  &.busy {
    color: var(--amber-gold);
    .status-dot {
      background: var(--amber-gold);
      box-shadow: 0 0 8px rgba(201, 169, 110, 0.75);
      animation: statusBlink 1.1s ease-in-out infinite;
    }
  }

  &.failed {
    color: #e8a87c;
    .status-dot {
      background: #e8a87c;
      box-shadow: 0 0 8px rgba(232, 168, 124, 0.7);
      animation: none;
    }
  }
}

/* Chat messages */
.chat-messages {
  flex: 1; overflow-y: auto; padding: 12px;
  display: flex; flex-direction: column; gap: 12px;
}

.chat-msg {
  display: flex; flex-direction: column; gap: 8px;
  &.user { align-items: flex-end;
    .msg-bubble { background: rgba(90,158,111,0.1); border-color: rgba(90,158,111,0.2); margin-left: 24px; }
  }
  &.assistant {
    .msg-bubble { background: rgba(201,169,110,0.06); border-color: rgba(201,169,110,0.15); margin-right: 12px; }
  }
}

.msg-bubble {
  padding: 10px 14px; border: 1px solid; border-radius: 10px;
  font-size: 0.85rem; color: var(--text-primary); line-height: 1.7; white-space: pre-wrap;
  &.typing { display: flex; align-items: center; gap: 6px; color: var(--text-muted); font-size: 0.78rem; }
}

.msg-inline-typing {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-muted);
  font-size: 0.78rem;
}

.msg-actions {
  display: flex; gap: 8px; margin-top: 8px;
  padding-top: 8px; border-top: 1px solid var(--border-subtle);
}

.msg-btn {
  padding: 4px 10px; font-size: 0.72rem;
  color: var(--amber-gold); background: rgba(201,169,110,0.06);
  border: 1px solid rgba(201,169,110,0.15); border-radius: 4px;
  cursor: pointer; transition: all 0.2s;
  &:hover { background: rgba(201,169,110,0.12); border-color: var(--border-gold); }
}

.t-dot {
  width: 5px; height: 5px; border-radius: 50%; background: var(--amber-gold);
  animation: typingPulse 1.4s ease-in-out infinite;
  &:nth-child(2) { animation-delay: 0.2s; }
  &:nth-child(3) { animation-delay: 0.4s; }
}

@keyframes typingPulse {
  0%, 60%, 100% { opacity: 0.3; transform: scale(0.8); }
  30% { opacity: 1; transform: scale(1.2); }
}

@keyframes statusBlink {
  0%, 100% { opacity: 0.6; transform: scale(0.95); }
  50% { opacity: 1; transform: scale(1.15); }
}

.t-label { font-size: 0.75rem; }

/* Chat Input */
.chat-input-area { border-top: 1px solid var(--border-subtle); background: rgba(11,17,32,0.6); flex-shrink: 0; }

.input-plugins { display: flex; align-items: center; gap: 6px; padding: 6px 12px 0; flex-wrap: wrap; }
.ip-label { font-size: 0.68rem; color: var(--text-muted); }
.ip-tag {
  padding: 1px 8px; font-size: 0.65rem; color: var(--jade-green);
  background: rgba(90,158,111,0.08); border: 1px solid rgba(90,158,111,0.2); border-radius: 8px;
}

.input-wrap { display: flex; gap: 8px; padding: 8px 12px; align-items: flex-end; }

.chat-textarea {
  flex: 1; padding: 8px 12px;
  background: rgba(11,17,32,0.5); border: 1px solid var(--border-subtle);
  border-radius: 8px; color: var(--text-primary);
  font-family: var(--font-body); font-size: 0.85rem; line-height: 1.65;
  resize: none; outline: none; transition: border-color 0.3s;
  &:focus { border-color: var(--border-gold); }
  &::placeholder { color: var(--text-muted); }
}

.btn-send {
  padding: 8px 18px; font-family: var(--font-heading);
  font-size: 0.88rem; letter-spacing: 0.15em;
  color: var(--amber-gold);
  background: linear-gradient(135deg, rgba(201,169,110,0.15), rgba(201,169,110,0.05));
  border: 1px solid var(--border-gold); border-radius: 8px;
  cursor: pointer; transition: all 0.3s; flex-shrink: 0;
  &:hover:not(:disabled) { box-shadow: var(--shadow-gold); color: var(--xuan-paper); }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
}

.input-hint { padding: 0 12px 6px; font-size: 0.65rem; color: var(--text-muted); text-align: right; }

@media (max-width: 1360px) {
  .panel-left { width: 248px; }
  .panel-right { width: 320px; }
  .main-editor { padding: 20px 28px; }
}

@media (max-width: 1120px) {
  .panel-right { width: 300px; }
}
</style>
