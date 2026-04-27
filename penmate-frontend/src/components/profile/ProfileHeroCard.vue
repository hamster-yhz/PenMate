<template>
  <div class="profile-card glass-panel" data-testid="profile-hero-card">
    <div class="pc-glow"></div>
    <div class="pc-header">
      <div class="avatar-large" data-testid="profile-hero-avatar">
        <span>{{ profile.name.charAt(0) }}</span>
      </div>
      <div class="pc-info">
        <div class="pc-name-row">
          <template v-if="isEditing">
            <input v-model="draftName" class="name-input" data-testid="profile-hero-name-input" />
            <button class="btn-save" type="button" data-testid="profile-hero-save" @click="handleSave">
              保存资料
            </button>
          </template>
          <template v-else>
            <h2 class="pc-name">{{ profile.name }}</h2>
            <button class="btn-edit-name" type="button" data-testid="profile-hero-edit" @click="startEditing">
              ✏️ 编辑资料
            </button>
          </template>
        </div>
        <p class="pc-email">{{ profile.email }}</p>
        <textarea
          v-if="isEditing"
          v-model="draftBio"
          class="bio-textarea"
          data-testid="profile-hero-bio-input"
          rows="2"
        ></textarea>
        <p v-else class="pc-bio">{{ profile.bio || '点击编辑个人简介...' }}</p>
        <p v-if="errorMessage" class="pc-error">{{ errorMessage }}</p>
      </div>
    </div>

    <div class="pc-stats">
      <div class="ps-item">
        <span class="ps-val">{{ profile.bookCount }}</span>
        <span class="ps-lbl">部作品</span>
      </div>
      <div class="ps-sep"></div>
      <div class="ps-item">
        <span class="ps-val">{{ profile.totalWords }}</span>
        <span class="ps-lbl">总字数</span>
      </div>
      <div class="ps-sep"></div>
      <div class="ps-item">
        <span class="ps-val">{{ profile.daysActive }}</span>
        <span class="ps-lbl">创作天数</span>
      </div>
      <div class="ps-sep"></div>
      <div class="ps-item">
        <span class="ps-val">{{ profile.streak }}</span>
        <span class="ps-lbl">连续创作</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { ProfileModel } from '@/composables/profile/useProfileSettings'

const props = defineProps<{
  profile: ProfileModel
}>()

const emit = defineEmits<{
  (event: 'save-profile', profile: ProfileModel): void
}>()

const isEditing = ref(false)
const draftName = ref('')
const draftBio = ref('')
const errorMessage = ref('')

const startEditing = () => {
  draftName.value = props.profile.name
  draftBio.value = props.profile.bio
  errorMessage.value = ''
  isEditing.value = true
}

const handleSave = () => {
  const name = draftName.value.trim()
  const bio = draftBio.value.trim()

  if (!name) {
    errorMessage.value = '请输入昵称'
    return
  }

  errorMessage.value = ''
  emit('save-profile', {
    ...props.profile,
    name,
    bio,
  })
  isEditing.value = false
}
</script>

<style lang="less" scoped>
.profile-card {
  position: relative;
  padding: 28px;
  background: rgba(17, 24, 39, 0.6);
  border: 1px solid var(--border-subtle);
  border-radius: 16px;
}

.pc-glow {
  position: absolute;
  top: 0;
  left: 15%;
  right: 15%;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--amber-gold), transparent);
  opacity: 0.4;
}

.pc-header {
  display: flex;
  gap: 20px;
  margin-bottom: 24px;
}

.avatar-large {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(201, 169, 110, 0.3), rgba(201, 169, 110, 0.1));
  border: 2px solid var(--border-gold);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-heading);
  font-size: 2rem;
  color: var(--amber-gold);
  flex-shrink: 0;
}

.pc-info {
  flex: 1;
}

.pc-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.pc-name {
  font-family: var(--font-heading);
  font-size: 1.4rem;
  color: var(--xuan-paper);
  letter-spacing: 0.15em;
}

.btn-edit-name {
  padding: 5px 14px;
  font-size: 0.78rem;
  color: var(--amber-gold);
  background: rgba(201, 169, 110, 0.06);
  border: 1px solid rgba(201, 169, 110, 0.15);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;

  &:hover {
    background: rgba(201, 169, 110, 0.12);
    border-color: var(--border-gold);
  }
}

.btn-save,
.name-input,
.bio-textarea {
  border: 1px solid var(--border-gold);
  border-radius: 6px;
}

.btn-save {
  padding: 5px 14px;
  font-size: 0.78rem;
  color: var(--amber-gold);
  background: rgba(201, 169, 110, 0.06);
  cursor: pointer;
}

.name-input {
  min-width: 180px;
  padding: 6px 10px;
  background: rgba(11, 17, 32, 0.6);
  color: var(--xuan-paper);
}

.pc-email {
  font-size: 0.82rem;
  color: var(--text-muted);
  margin-bottom: 8px;
}

.pc-bio {
  font-size: 0.88rem;
  color: var(--text-secondary);
  line-height: 1.6;
}

.bio-textarea {
  width: 100%;
  padding: 8px 10px;
  background: rgba(11, 17, 32, 0.5);
  color: var(--text-primary);
  font-family: var(--font-body);
  font-size: 0.88rem;
  resize: vertical;
}

.pc-error {
  margin-top: 8px;
  color: #e8a87c;
  font-size: 0.78rem;
}

.pc-stats {
  display: flex;
  align-items: center;
  gap: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--border-subtle);
}

.ps-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.ps-val {
  font-family: var(--font-heading);
  font-size: 1.2rem;
  color: var(--amber-gold);
}

.ps-lbl {
  font-size: 0.7rem;
  color: var(--text-muted);
}

.ps-sep {
  width: 1px;
  height: 28px;
  background: var(--border-subtle);
}

@media (max-width: 768px) {
  .pc-header,
  .pc-stats {
    flex-direction: column;
    align-items: flex-start;
  }

  .ps-sep {
    display: none;
  }
}
</style>
