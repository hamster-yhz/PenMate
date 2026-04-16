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
              <button class="tree-btn" @click="addVolume">+ 新卷</button>
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
                    <button class="tree-act-btn danger" @click="deleteVolume(vIdx)" title="删除">✕</button>
                  </div>
                </div>
                <div v-if="vol.expanded" class="tree-children">
                  <div
                    v-for="(ch, cIdx) in vol.children"
                    :key="ch.key"
                    class="tree-item chapter"
                    :class="{ active: activeChapter === ch.key }"
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
                      <button class="tree-act-btn danger" @click="deleteChapter(vol, cIdx)" title="删除">✕</button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- ======== Character Library ======== -->
          <div class="tab-content" v-if="activeLeftTab === 'characters'">
            <div class="tree-actions">
              <button class="tree-btn" @click="addCharacter">+ 新角色</button>
            </div>
            <div class="char-list">
              <div
                v-for="(char, cIdx) in characters"
                :key="char.id"
                class="char-card"
                :class="{ expanded: char.expanded }"
              >
                <div class="char-header" @click="char.expanded = !char.expanded">
                  <span class="char-avatar">{{ char.name.charAt(0) }}</span>
                  <div class="char-meta">
                    <span class="char-name">{{ char.name }}</span>
                    <span class="char-role">{{ char.role }}</span>
                  </div>
                  <div class="char-actions" @click.stop>
                    <button class="tree-act-btn danger" @click="deleteCharacter(cIdx)" title="删除角色">✕</button>
                  </div>
                  <span class="char-toggle">{{ char.expanded ? '▾' : '▸' }}</span>
                </div>
                <div class="char-details" v-if="char.expanded">
                  <div class="char-field-edit">
                    <span class="cf-label">名字</span>
                    <input v-model="char.name" class="cf-input" />
                  </div>
                  <div class="char-field-edit">
                    <span class="cf-label">身份</span>
                    <input v-model="char.role" class="cf-input" />
                  </div>
                  <div class="char-field-edit">
                    <span class="cf-label">性格</span>
                    <input v-model="char.personality" class="cf-input" />
                  </div>
                  <div class="char-field-edit">
                    <span class="cf-label">背景</span>
                    <textarea v-model="char.background" class="cf-input cf-textarea" rows="2"></textarea>
                  </div>
                  <div class="char-field-edit">
                    <span class="cf-label">能力</span>
                    <input v-model="char.ability" class="cf-input" placeholder="可选" />
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- ======== World Settings ======== -->
          <div class="tab-content" v-if="activeLeftTab === 'world'">
            <div class="tree-actions">
              <button class="tree-btn" @click="addWorldEntry">+ 新设定</button>
            </div>
            <div class="world-list">
              <div v-for="(entry, wIdx) in worldSettings" :key="entry.id" class="world-card">
                <div class="world-header" @click="entry.expanded = !entry.expanded">
                  <span class="world-icon">{{ entry.icon }}</span>
                  <span class="world-name">{{ entry.name }}</span>
                  <div class="world-actions" @click.stop>
                    <button class="tree-act-btn danger" @click="deleteWorldEntry(wIdx)" title="删除">✕</button>
                  </div>
                  <span class="world-toggle">{{ entry.expanded ? '▾' : '▸' }}</span>
                </div>
                <div class="world-body" v-if="entry.expanded">
                  <div class="world-edit-field">
                    <label>名称</label>
                    <input v-model="entry.name" class="cf-input" />
                  </div>
                  <div class="world-edit-field">
                    <label>图标</label>
                    <input v-model="entry.icon" class="cf-input cf-icon" />
                  </div>
                  <div class="world-edit-field">
                    <label>详情</label>
                    <textarea v-model="entry.description" class="cf-input cf-textarea" rows="3"></textarea>
                  </div>
                </div>
              </div>
            </div>
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
          <span>行 {{ currentLine }} · 列 {{ currentCol }}</span>
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
            <div class="agent-status">
              <span class="status-dot"></span>
              <span>就绪</span>
            </div>
          </div>

          <!-- Chat Messages -->
          <div class="chat-messages" ref="chatRef">
            <div
              v-for="msg in messages"
              :key="msg.id"
              class="chat-msg"
              :class="msg.role"
            >
              <div class="msg-bubble">
                <div class="msg-text" v-html="msg.text"></div>
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
                @approve="handleApprove"
                @reject="handleReject"
              />
            </div>

            <!-- Typing indicator -->
            <div class="chat-msg assistant" v-if="isGenerating">
              <div class="msg-bubble typing">
                <span class="t-dot"></span>
                <span class="t-dot"></span>
                <span class="t-dot"></span>
                <span class="t-label">AI正在创作中...</span>
              </div>
            </div>
          </div>

          <!-- Chat Input -->
          <div class="chat-input-area">
            <div class="input-plugins" v-if="activePlugins.length">
              <span class="ip-label">已挂载：</span>
              <span class="ip-tag" v-for="p in activePlugins" :key="p">{{ p }}</span>
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
    <ModelSettings :visible="showModelSettings" @close="showModelSettings = false" />
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
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
import { getSession, clearSession } from '@/stores/session'
import { authApi } from '@/api/modules/auth.api'

const router = useRouter()
const route = useRoute()
const session = getSession()

// --- State ---
const username = ref('墨客')
const userEmail = ref('moke@penmate.com')
const userMenuOpen = ref(false)
const novelTitle = ref('苍穹剑仙传')
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
const chapterContents = ref<Record<string, string>>({
  '0-0-0': '夜色如墨，月华倾洒。\n\n林风立于山巅，长衫猎猎作响。远处传来一阵若有若无的箫声，仿佛在诉说着千年未了的故事。\n\n他抬眼望去，只见天际处有一柄长剑破空而来，剑身上萦绕着淡金色的灵气，如同一条游龙穿云而过。那是师门传承千年的"天问剑"——传说中唯有心怀大道之人，方能驾驭此剑。\n\n"你终于来了。"身后传来一道苍老却中气十足的声音。\n\n林风没有回头，嘴角微微上扬："师父，弟子等这一天，已经等了三年。"',
  '0-0-1': '风起青山，云涌四方。\n\n自从林风踏入江湖以来，从未见过如此气势磅礴的场面。千人齐聚于凌霄殿前，各门各派的弟子皆身着门派法袍，剑气纵横间，空气中弥漫着一股肃杀之意。',
  '0-0-2': '剑意初现，惊鸿一瞥。\n\n当林风第一次真正感受到天问剑意时，整个人仿佛被一道闪电贯穿——不是痛苦，而是一种前所未有的明悟。',
  '0-1-0': '暗夜之中，一个神秘的组织悄然浮出水面。\n\n他们自称"暗网"，行事低调却触手遍及修仙界每一个角落。',
  '0-1-1': '地下交易场，灯火幽暗。\n\n形形色色的修士聚集于此，有的遮掩面容，有的大大方方地展示自己的修为境界。'
})

// Editor
const editorRef = ref<HTMLTextAreaElement | null>(null)
const editorContent = ref('')
const currentChapterTitle = ref('第一章：神秘黑影')
const activeChapter = ref('0-0-0')

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
const outlineData = ref([
  {
    title: '第一卷：初入江湖',
    key: '0-0',
    expanded: true,
    children: [
      { title: '第一章：神秘黑影', key: '0-0-0' },
      { title: '第二章：风起云涌', key: '0-0-1' },
      { title: '第三章：剑意初现', key: '0-0-2' }
    ]
  },
  {
    title: '第二卷：暗流涌动',
    key: '0-1',
    expanded: false,
    children: [
      { title: '第一章：神秘组织', key: '0-1-0' },
      { title: '第二章：地下交易', key: '0-1-1' }
    ]
  }
])

// Characters
const characters = ref([
  {
    id: 'c1',
    name: '林风',
    role: '主角',
    personality: '坚韧、机智、重情重义',
    background: '出身寒门，自幼被师父收养于天问山，修习剑道十余年',
    ability: '天问剑法·第三重',
    expanded: true
  },
  {
    id: 'c2',
    name: '苏婉清',
    role: '女主',
    personality: '聪慧、独立、外冷内热',
    background: '天机阁阁主之女，精通阵法与炼丹术',
    ability: '天机推衍术',
    expanded: false
  },
  {
    id: 'c3',
    name: '萧逸',
    role: '亦敌亦友',
    personality: '狂傲、不羁、有底线',
    background: '魔道天才，被逐出正道宗门后自立门户',
    ability: '噬魂魔功',
    expanded: false
  }
])

// World settings
const worldSettings = ref([
  {
    id: 'w1',
    icon: '⚔️',
    name: '修炼体系',
    description: '分为炼气、筑基、金丹、元婴、化神五大境界。每个境界分初期、中期、后期三层。突破需感悟天地法则。',
    expanded: true
  },
  {
    id: 'w2',
    icon: '🏔️',
    name: '天问山',
    description: '正道首席宗门，坐落于东域最高峰。山中灵气充沛，有上古大能遗留的剑意结界。',
    expanded: false
  },
  {
    id: 'w3',
    icon: '🌑',
    name: '暗网',
    description: '隐藏在修仙界暗处的神秘组织，专门贩卖禁忌功法与天材地宝。首领身份不明。',
    expanded: false
  }
])

// Active plugins
const activePlugins = ref(['热网词', '起名助手'])

// Chat messages
interface ChatMessage {
  id: number
  role: 'user' | 'assistant' | 'system'
  text: string
  approval?: ApprovalCardData
}

const messages = ref<ChatMessage[]>([
  {
    id: 1,
    role: 'assistant',
    text: '你好，我是你的AI写作助手。当前小说<b>「苍穹剑仙传」</b>已载入，文风设定为<b>古风文言化 · 慢节奏</b>。请问需要我做什么？'
  },
  {
    id: 2,
    role: 'user',
    text: '继续写第一章第五段，林风接过天问剑后的描写，加入些震撼的场景感。'
  },
  {
    id: 3,
    role: 'assistant',
    text: '林风伸手接住天问剑，刹那间，一股浩荡无匹的剑意自剑身涌入体内经脉。天地为之变色，四周的云层仿佛被一只无形巨手撕裂，露出星河万里的壮阔景象。\n\n他的衣袍被剑气激得猎猎作响，脚下的山巅碎石纷纷悬浮而起，在月光下泛着幽蓝的冷光。\n\n"这便是……天问的力量吗？"林风感到一阵战栗，并非恐惧，而是一种从灵魂深处涌起的共鸣。',
    approval: {
      id: 'ap-1',
      message: '检测到新设定：「天问剑意共鸣」。是否创建并归档至"修炼体系"？',
      time: '刚才',
      preview: {
        '名称': '天问剑意共鸣',
        '类型': '特殊能力',
        '描述': '持剑者与天问剑产生灵魂共鸣，可激发星河异象'
      },
      resolved: false
    }
  }
])

const chatInput = ref('')
const chatRef = ref<HTMLElement | null>(null)
let msgIdCounter = 4

// ===================== METHODS =====================

const updateTitle = (e: Event) => {
  const target = e.target as HTMLElement
  novelTitle.value = target.textContent || '未命名小说'
}

// --- Editor Input / Cursor ---
const onEditorInput = () => {
  chapterContents.value[activeChapter.value] = editorContent.value
  wordCount.value = editorContent.value.replace(/\s/g, '').length
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
const selectChapter = (ch: { key: string; title: string }) => {
  chapterContents.value[activeChapter.value] = editorContent.value
  activeChapter.value = ch.key
  currentChapterTitle.value = ch.title
  editorContent.value = chapterContents.value[ch.key] || ''
  wordCount.value = editorContent.value.replace(/\s/g, '').length
  undoStack.value = []
  redoStack.value = []
  lastSnapshot = editorContent.value
  nextTick(() => editorRef.value?.focus())
}

// --- Outline CRUD ---
const addVolume = () => {
  const idx = outlineData.value.length
  const nums = ['一','二','三','四','五','六','七','八','九','十']
  outlineData.value.push({
    title: `第${nums[idx] || idx + 1}卷：新的篇章`,
    key: `0-${idx}`,
    expanded: true,
    children: []
  })
}

const addChapter = (vol: any) => {
  const idx = vol.children.length
  const newKey = `${vol.key}-${idx}`
  vol.children.push({ title: `第${idx + 1}章：未命名`, key: newKey })
  vol.expanded = true
  chapterContents.value[newKey] = ''
}

const deleteVolume = (vIdx: number) => {
  const vol = outlineData.value[vIdx]
  vol.children.forEach((ch: any) => delete chapterContents.value[ch.key])
  outlineData.value.splice(vIdx, 1)
}

const deleteChapter = (vol: any, cIdx: number) => {
  const ch = vol.children[cIdx]
  delete chapterContents.value[ch.key]
  if (activeChapter.value === ch.key) {
    editorContent.value = ''
    currentChapterTitle.value = ''
    activeChapter.value = ''
  }
  vol.children.splice(cIdx, 1)
}

// --- Inline node rename ---
const startEditNode = (node: { key: string; title: string }) => {
  editingNodeKey.value = node.key
  editingNodeValue.value = node.title
}

const finishEditNode = (node: { key: string; title: string }) => {
  if (editingNodeValue.value.trim()) {
    node.title = editingNodeValue.value.trim()
  }
  editingNodeKey.value = ''
  if (node.key === activeChapter.value) {
    currentChapterTitle.value = node.title
  }
}

// --- Character CRUD ---
const addCharacter = () => {
  characters.value.push({
    id: `c${Date.now()}`,
    name: '新角色',
    role: '配角',
    personality: '',
    background: '',
    ability: '',
    expanded: true
  })
}

const deleteCharacter = (idx: number) => {
  characters.value.splice(idx, 1)
}

// --- World CRUD ---
const addWorldEntry = () => {
  worldSettings.value.push({
    id: `w${Date.now()}`,
    icon: '🔮',
    name: '新设定',
    description: '',
    expanded: true
  })
}

const deleteWorldEntry = (idx: number) => {
  worldSettings.value.splice(idx, 1)
}

// --- Save ---
const saveContent = () => {
  chapterContents.value[activeChapter.value] = editorContent.value
  saveHint.value = '✓ 已保存'
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
const sendMessage = async () => {
  if (!chatInput.value.trim() || isGenerating.value) return
  const userMsg: ChatMessage = { id: msgIdCounter++, role: 'user', text: chatInput.value }
  messages.value.push(userMsg)
  chatInput.value = ''
  isGenerating.value = true
  await nextTick()
  scrollChat()
  await new Promise(r => setTimeout(r, 2500))
  const aiMsg: ChatMessage = {
    id: msgIdCounter++,
    role: 'assistant',
    text: '（AI根据您的指令、当前大纲、文风参数与RAG记忆检索，正在生成内容...）\n\n此处为模拟响应。在实际环境中，AI会调用LangChain4j Agent框架，结合已挂载插件与向量库检索结果，流式生成符合文风设定的长文本。'
  }
  messages.value.push(aiMsg)
  isGenerating.value = false
  await nextTick()
  scrollChat()
}

const scrollChat = () => {
  if (chatRef.value) chatRef.value.scrollTop = chatRef.value.scrollHeight
}

// --- Approval ---
const handleApprove = (id: string) => {
  const msg = messages.value.find(m => m.approval?.id === id)
  if (msg && msg.approval) { msg.approval.resolved = true; msg.approval.resolvedAction = 'approved' }
}

const handleReject = (id: string) => {
  const msg = messages.value.find(m => m.approval?.id === id)
  if (msg && msg.approval) { msg.approval.resolved = true; msg.approval.resolvedAction = 'rejected' }
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
const mapOutlineTree = (nodes: Array<Record<string, any>>) => {
  const volumeMap = new Map<string, { title: string; key: string; expanded: boolean; children: Array<{ title: string; key: string }> }>()
  nodes.forEach((node) => {
    const key = String(node.id ?? node.nodeId ?? node.key ?? '')
    const title = String(node.title ?? node.name ?? '未命名')
    const parentId = node.parentId ?? node.parentNodeId ?? null
    const nodeType = String(node.nodeType ?? node.type ?? '').toUpperCase()
    if (!parentId || nodeType.includes('VOLUME')) {
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
        parent.children.push({ title, key })
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
    const [project, outlines, chapters] = await Promise.all([
      novelApi.getProject(projectId),
      outlineApi.listOutlineTree(projectId),
      novelApi.listChapters(projectId)
    ])

    novelTitle.value = String((project as Record<string, any>)?.title ?? novelTitle.value)

    const chapterList = (chapters || []) as Array<Record<string, any>>
    chapterList.forEach((chapter) => {
      const key = String(chapter.chapterId ?? chapter.id ?? chapter.key ?? '')
      if (!key) return
      chapterContents.value[key] = String(chapter.content ?? chapter.summary ?? '')
    })

    outlineData.value = mapOutlineTree((outlines || []) as Array<Record<string, any>>)
    const first = outlineData.value[0]?.children?.[0]
    if (first) {
      activeChapter.value = first.key
      currentChapterTitle.value = first.title
      editorContent.value = chapterContents.value[first.key] || ''
      wordCount.value = editorContent.value.replace(/\s/g, '').length
      lastSnapshot = editorContent.value
    }
  } catch (error: any) {
    message.warning(error?.message || '工作台数据加载失败，已使用本地演示数据')
  }
}

onMounted(() => {
  if (session.userName) username.value = session.userName
  if (session.userEmail) userEmail.value = session.userEmail
  editorContent.value = chapterContents.value[activeChapter.value] || ''
  wordCount.value = editorContent.value.replace(/\s/g, '').length
  lastSnapshot = editorContent.value
  loadWorkbenchData()
})
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
  width: 280px; min-width: 0;
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

.editor-area { flex: 1; overflow: hidden; padding: 0; }

.main-editor {
  width: 100%; height: 100%; padding: 24px 48px;
  background: transparent; border: none; resize: none; outline: none;
  color: var(--text-primary); font-family: var(--font-body);
  font-size: 1rem; line-height: 2; letter-spacing: 0.02em;
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
  width: 340px; min-width: 0;
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
  font-family: var(--font-body); font-size: 0.85rem;
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
</style>
