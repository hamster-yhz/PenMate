<template>
  <div class="profile-page">
    <!-- Background particles -->
    <div class="particles" aria-hidden="true">
      <span v-for="n in 10" :key="n" class="p-dot" :style="pStyle(n)"></span>
    </div>

    <!-- Top Nav -->
    <nav class="page-nav">
      <div class="nav-left">
        <img :src="logoImg" alt="PenMate" class="nav-logo" @click="$router.push('/')" />
        <span class="nav-brand">笔友 · 个人中心</span>
      </div>
      <div class="nav-right">
        <button class="nav-btn" @click="$router.push('/mybooks')">📚 我的书架</button>
      </div>
    </nav>

    <div class="page-body">
      <!-- Profile Card -->
      <div class="profile-card glass-panel">
        <div class="pc-glow"></div>
        <div class="pc-header">
          <div class="avatar-large" @click="editingAvatar = true">
            <span>{{ user.name.charAt(0) }}</span>
            <div class="avatar-edit">✏️</div>
          </div>
          <div class="pc-info">
            <div class="pc-name-row">
              <h2 v-if="!editingName" class="pc-name" @dblclick="editingName = true">{{ user.name }}</h2>
              <input
                v-else
                v-model="user.name"
                class="name-input"
                @blur="editingName = false"
                @keydown.enter="editingName = false"
                ref="nameInputRef"
              />
              <button class="btn-edit-name" @click="startEditName" v-if="!editingName">✏️</button>
            </div>
            <p class="pc-email">{{ user.email }}</p>
            <p class="pc-bio" v-if="!editingBio" @dblclick="editingBio = true">
              {{ user.bio || '点击编辑个人简介...' }}
            </p>
            <textarea
              v-else
              v-model="user.bio"
              class="bio-textarea"
              @blur="editingBio = false"
              rows="2"
              placeholder="写几句介绍自己..."
            ></textarea>
          </div>
        </div>

        <div class="pc-stats">
          <div class="ps-item">
            <span class="ps-val">{{ user.bookCount }}</span>
            <span class="ps-lbl">部作品</span>
          </div>
          <div class="ps-sep"></div>
          <div class="ps-item">
            <span class="ps-val">{{ user.totalWords }}</span>
            <span class="ps-lbl">总字数</span>
          </div>
          <div class="ps-sep"></div>
          <div class="ps-item">
            <span class="ps-val">{{ user.daysActive }}</span>
            <span class="ps-lbl">创作天数</span>
          </div>
          <div class="ps-sep"></div>
          <div class="ps-item">
            <span class="ps-val">{{ user.streak }}</span>
            <span class="ps-lbl">连续创作</span>
          </div>
        </div>
      </div>

      <!-- Settings Sections -->
      <div class="settings-grid">
        <!-- Account Settings -->
        <div class="settings-section glass-panel">
          <h3 class="section-title">🔐 账号安全</h3>
          <div class="setting-row">
            <div class="sr-info">
              <span class="sr-label">登录邮箱</span>
              <span class="sr-value">{{ user.email }}</span>
            </div>
            <button class="sr-btn" @click="editingEmail = !editingEmail">修改</button>
          </div>
          <div class="setting-row-expand" v-if="editingEmail">
            <input v-model="newEmail" class="f-input" placeholder="新邮箱地址" type="email" />
            <button class="btn-sm" @click="saveEmail">保存</button>
          </div>

          <div class="setting-row">
            <div class="sr-info">
              <span class="sr-label">登录密码</span>
              <span class="sr-value">••••••••</span>
            </div>
            <button class="sr-btn" @click="editingPassword = !editingPassword">修改</button>
          </div>
          <div class="setting-row-expand" v-if="editingPassword">
            <input v-model="passwords.old" class="f-input" placeholder="当前密码" type="password" />
            <input v-model="passwords.new1" class="f-input" placeholder="新密码" type="password" />
            <input v-model="passwords.new2" class="f-input" placeholder="确认新密码" type="password" />
            <button class="btn-sm" @click="savePassword">保存</button>
          </div>
        </div>

        <!-- Preference Settings -->
        <div class="settings-section glass-panel">
          <h3 class="section-title">⚙️ 偏好设置</h3>
          <div class="setting-row">
            <div class="sr-info">
              <span class="sr-label">默认文风</span>
              <span class="sr-value">{{ user.defaultStyle }}</span>
            </div>
            <button class="sr-btn" @click="$router.push('/workbench')">前往设置</button>
          </div>
          <div class="setting-row">
            <div class="sr-info">
              <span class="sr-label">自动保存间隔</span>
              <span class="sr-value">{{ user.autoSaveInterval }}秒</span>
            </div>
            <select v-model="user.autoSaveInterval" class="sr-select">
              <option :value="15">15秒</option>
              <option :value="30">30秒</option>
              <option :value="60">60秒</option>
              <option :value="120">120秒</option>
            </select>
          </div>
          <div class="setting-row">
            <div class="sr-info">
              <span class="sr-label">编辑器字体大小</span>
              <span class="sr-value">{{ user.fontSize }}px</span>
            </div>
            <select v-model="user.fontSize" class="sr-select">
              <option :value="14">14px</option>
              <option :value="16">16px</option>
              <option :value="18">18px</option>
              <option :value="20">20px</option>
            </select>
          </div>
        </div>

        <!-- API Keys -->
        <div class="settings-section glass-panel">
          <h3 class="section-title">🔑 API密钥管理</h3>
          <div class="setting-row" v-for="key in apiKeys" :key="key.id">
            <div class="sr-info">
              <span class="sr-label">{{ key.name }}</span>
              <span class="sr-value key-mask">{{ key.maskedKey }}</span>
            </div>
            <div class="sr-actions">
              <span class="key-status" :class="key.status">{{ key.status === 'active' ? '✓ 有效' : '✗ 未配置' }}</span>
            </div>
          </div>
          <button class="btn-manage-keys" @click="$router.push('/workbench')">管理API池 →</button>
        </div>
      </div>

      <!-- Danger Zone -->
      <div class="danger-zone glass-panel">
        <h3 class="section-title danger">⚠️ 危险操作</h3>
        <div class="setting-row">
          <div class="sr-info">
            <span class="sr-label">退出登录</span>
            <span class="sr-value">退出当前账号</span>
          </div>
          <button class="sr-btn logout" @click="handleLogout">退出</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import logoImg from '@/assets/images/logo.png'

const router = useRouter()
const nameInputRef = ref<HTMLInputElement | null>(null)

const user = reactive({
  name: '墨客',
  email: 'moke@penmate.com',
  bio: '执笔问道，以墨寄情。热爱仙侠与悬疑交织的故事。',
  bookCount: 3,
  totalWords: 46370,
  daysActive: 42,
  streak: 7,
  defaultStyle: '古风文言化 · 慢节奏',
  autoSaveInterval: 30,
  fontSize: 16
})

const editingName = ref(false)
const editingBio = ref(false)
const editingEmail = ref(false)
const editingPassword = ref(false)
const editingAvatar = ref(false)
const newEmail = ref('')
const passwords = reactive({ old: '', new1: '', new2: '' })

const apiKeys = ref([
  { id: 'k1', name: 'DeepSeek', maskedKey: 'sk-****...7a2f', status: 'active' },
  { id: 'k2', name: 'OpenAI', maskedKey: '未配置', status: 'none' },
  { id: 'k3', name: 'Anthropic', maskedKey: '未配置', status: 'none' }
])

const startEditName = async () => {
  editingName.value = true
  await nextTick()
  nameInputRef.value?.focus()
}

const saveEmail = () => {
  if (newEmail.value.includes('@')) {
    user.email = newEmail.value
    editingEmail.value = false
    newEmail.value = ''
  }
}

const savePassword = () => {
  if (passwords.new1 && passwords.new1 === passwords.new2) {
    editingPassword.value = false
    passwords.old = ''
    passwords.new1 = ''
    passwords.new2 = ''
  }
}

const handleLogout = () => {
  router.push('/login')
}

const pStyle = (n: number) => ({
  width: `${Math.random() * 3 + 1}px`,
  height: `${Math.random() * 3 + 1}px`,
  left: `${Math.random() * 100}%`,
  bottom: '-5px',
  animationDuration: `${Math.random() * 12 + 12}s`,
  animationDelay: `${Math.random() * 15}s`,
  opacity: Math.random() * 0.3 + 0.1
})
</script>

<style lang="less" scoped>
.profile-page {
  min-height: 100vh;
  background: var(--bg-primary);
  position: relative;
}

.particles { position: fixed; inset: 0; pointer-events: none; z-index: 0; }
.p-dot {
  position: absolute; border-radius: 50%;
  background: radial-gradient(circle, var(--amber-gold), transparent);
  animation: particleDrift linear infinite;
}

/* Nav */
.page-nav {
  position: sticky; top: 0; z-index: 50;
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 32px;
  background: rgba(11,17,32,0.9);
  backdrop-filter: blur(16px);
  border-bottom: 1px solid var(--border-subtle);
}
.nav-left { display: flex; align-items: center; gap: 12px; }
.nav-logo { width: 32px; height: 32px; border-radius: 50%; object-fit: cover; cursor: pointer; }
.nav-brand { font-family: var(--font-heading); font-size: 1.1rem; color: var(--amber-gold); letter-spacing: 0.2em; }
.nav-right { display: flex; align-items: center; }
.nav-btn {
  padding: 6px 14px; background: none;
  border: 1px solid var(--border-subtle);
  border-radius: 6px; color: var(--text-secondary);
  font-size: 0.82rem; cursor: pointer; transition: all 0.3s;
  &:hover { border-color: var(--border-gold); color: var(--amber-gold); }
}

/* Body */
.page-body {
  position: relative; z-index: 1;
  max-width: 800px; margin: 0 auto;
  padding: 32px 24px 64px;
  display: flex; flex-direction: column; gap: 24px;
}

/* Profile Card */
.profile-card {
  position: relative; padding: 28px;
  background: rgba(17,24,39,0.6);
  border: 1px solid var(--border-subtle);
  border-radius: 16px;
}

.pc-glow {
  position: absolute; top: 0; left: 15%; right: 15%; height: 1px;
  background: linear-gradient(90deg, transparent, var(--amber-gold), transparent);
  opacity: 0.4;
}

.pc-header { display: flex; gap: 20px; margin-bottom: 24px; }

.avatar-large {
  width: 72px; height: 72px; border-radius: 50%;
  background: linear-gradient(135deg, rgba(201,169,110,0.3), rgba(201,169,110,0.1));
  border: 2px solid var(--border-gold);
  display: flex; align-items: center; justify-content: center;
  font-family: var(--font-heading);
  font-size: 2rem; color: var(--amber-gold);
  position: relative; cursor: pointer; flex-shrink: 0;
  transition: all 0.3s;
  &:hover { box-shadow: 0 0 20px rgba(201,169,110,0.2); .avatar-edit { opacity: 1; } }
}

.avatar-edit {
  position: absolute; bottom: -2px; right: -2px;
  width: 22px; height: 22px; border-radius: 50%;
  background: var(--bg-primary);
  border: 1px solid var(--border-gold);
  display: flex; align-items: center; justify-content: center;
  font-size: 0.6rem; opacity: 0; transition: opacity 0.3s;
}

.pc-info { flex: 1; }

.pc-name-row { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.pc-name {
  font-family: var(--font-heading);
  font-size: 1.4rem; color: var(--xuan-paper);
  letter-spacing: 0.15em; cursor: pointer;
}

.name-input {
  padding: 4px 10px;
  background: rgba(11,17,32,0.6);
  border: 1px solid var(--border-gold);
  border-radius: 6px;
  color: var(--xuan-paper);
  font-family: var(--font-heading);
  font-size: 1.2rem; outline: none;
}

.btn-edit-name {
  background: none; border: none; font-size: 0.75rem;
  cursor: pointer; opacity: 0.5; transition: opacity 0.2s;
  &:hover { opacity: 1; }
}

.pc-email { font-size: 0.82rem; color: var(--text-muted); margin-bottom: 8px; }

.pc-bio {
  font-size: 0.88rem; color: var(--text-secondary); line-height: 1.6;
  cursor: pointer; padding: 4px 0;
  &:hover { color: var(--text-primary); }
}

.bio-textarea {
  width: 100%; padding: 8px 10px;
  background: rgba(11,17,32,0.5);
  border: 1px solid var(--border-gold);
  border-radius: 6px;
  color: var(--text-primary);
  font-family: var(--font-body);
  font-size: 0.88rem; resize: vertical; outline: none;
}

.pc-stats {
  display: flex; align-items: center; gap: 24px;
  padding-top: 20px; border-top: 1px solid var(--border-subtle);
}

.ps-item { display: flex; flex-direction: column; align-items: center; gap: 2px; }
.ps-val { font-family: var(--font-heading); font-size: 1.2rem; color: var(--amber-gold); }
.ps-lbl { font-size: 0.7rem; color: var(--text-muted); }
.ps-sep { width: 1px; height: 28px; background: var(--border-subtle); }

/* Settings */
.settings-grid {
  display: flex; flex-direction: column; gap: 20px;
}

.settings-section {
  padding: 20px 24px;
  background: rgba(17,24,39,0.5);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
}

.section-title {
  font-family: var(--font-heading);
  font-size: 1rem; color: var(--xuan-paper);
  letter-spacing: 0.12em; margin-bottom: 16px;
  &.danger { color: #e8a87c; }
}

.setting-row {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid rgba(201,169,110,0.06);
  &:last-child { border-bottom: none; }
}

.sr-info { display: flex; flex-direction: column; gap: 2px; }
.sr-label { font-size: 0.85rem; color: var(--text-primary); }
.sr-value { font-size: 0.75rem; color: var(--text-muted); }
.key-mask { font-family: monospace; letter-spacing: 0.05em; }

.sr-btn {
  padding: 5px 14px; font-size: 0.78rem;
  color: var(--amber-gold);
  background: rgba(201,169,110,0.06);
  border: 1px solid rgba(201,169,110,0.15);
  border-radius: 4px; cursor: pointer;
  transition: all 0.3s;
  &:hover { background: rgba(201,169,110,0.12); border-color: var(--border-gold); }
  &.logout { color: #e8a87c; background: rgba(192,60,45,0.08); border-color: rgba(192,60,45,0.2);
    &:hover { background: rgba(192,60,45,0.15); } }
}

.sr-select {
  padding: 4px 10px; font-size: 0.78rem;
  background: rgba(11,17,32,0.6);
  border: 1px solid var(--border-subtle);
  border-radius: 4px; color: var(--text-primary);
  outline: none;
  &:focus { border-color: var(--border-gold); }
}

.key-status {
  font-size: 0.72rem; padding: 3px 10px; border-radius: 10px;
  &.active { color: var(--jade-green); background: rgba(90,158,111,0.1); }
  &.none { color: var(--text-muted); background: rgba(107,97,88,0.1); }
}

.sr-actions { display: flex; align-items: center; gap: 8px; }

.setting-row-expand {
  display: flex; flex-direction: column; gap: 8px;
  padding: 10px 0 16px; animation: fadeInUp 0.2s ease;
}

.f-input {
  padding: 8px 12px;
  background: rgba(11,17,32,0.6);
  border: 1px solid var(--border-subtle);
  border-radius: 6px;
  color: var(--text-primary);
  font-size: 0.85rem; outline: none;
  &:focus { border-color: var(--border-gold); }
  &::placeholder { color: var(--text-muted); }
}

.btn-sm {
  align-self: flex-start;
  padding: 6px 16px; font-size: 0.8rem;
  color: var(--amber-gold);
  background: rgba(201,169,110,0.08);
  border: 1px solid rgba(201,169,110,0.2);
  border-radius: 4px; cursor: pointer;
  transition: all 0.3s;
  &:hover { background: rgba(201,169,110,0.15); border-color: var(--border-gold); }
}

.btn-manage-keys {
  margin-top: 12px; padding: 8px 16px;
  font-size: 0.82rem; color: var(--amber-gold);
  background: none; border: 1px solid rgba(201,169,110,0.15);
  border-radius: 6px; cursor: pointer;
  transition: all 0.3s; width: 100%;
  &:hover { background: rgba(201,169,110,0.06); border-color: var(--border-gold); }
}

.danger-zone {
  padding: 20px 24px;
  background: rgba(17,24,39,0.5);
  border: 1px solid rgba(192,60,45,0.15);
  border-radius: 12px;
}
</style>
