<template>
  <div class="style-manager" v-if="visible">
    <div class="sm-backdrop" @click="$emit('close')"></div>
    <div class="sm-drawer glass-panel">
      <div class="sm-glow"></div>

      <div class="sm-header">
        <img :src="iconStyle" alt="" class="sm-icon" />
        <h3>文风管控</h3>
        <button class="sm-close" @click="$emit('close')">✕</button>
      </div>

      <div class="sm-body">
        <!-- 当前文风显示 -->
        <div class="sm-section">
          <div class="sm-label">当前文风</div>
          <div class="style-badge">
            <span class="badge-glow"></span>
            {{ currentStyle }}
          </div>
        </div>

        <!-- 节奏 -->
        <div class="sm-section">
          <div class="sm-label">叙事节奏</div>
          <div class="option-group">
            <button
              v-for="opt in tempoOptions"
              :key="opt"
              class="opt-btn"
              :class="{ active: config.tempo === opt }"
              @click="config.tempo = opt"
            >{{ opt }}</button>
          </div>
        </div>

        <!-- 语气 -->
        <div class="sm-section">
          <div class="sm-label">语气风格</div>
          <div class="option-group">
            <button
              v-for="opt in toneOptions"
              :key="opt"
              class="opt-btn"
              :class="{ active: config.tone === opt }"
              @click="config.tone = opt"
            >{{ opt }}</button>
          </div>
        </div>

        <!-- 描写偏好 -->
        <div class="sm-section">
          <div class="sm-label">描写偏好</div>
          <div class="option-group">
            <button
              v-for="opt in descOptions"
              :key="opt"
              class="opt-btn"
              :class="{ active: config.descPreference === opt }"
              @click="config.descPreference = opt"
            >{{ opt }}</button>
          </div>
        </div>

        <!-- 范本学习 -->
        <div class="sm-section">
          <div class="sm-label">范本学习</div>
          <p class="sm-hint">粘贴500字参考样文，系统自动解析文风</p>
          <textarea
            v-model="sampleText"
            class="sm-textarea"
            placeholder="粘贴一段参考样文..."
            rows="5"
          ></textarea>
          <button class="btn-analyze" @click="analyzeSample">
            <span>🔍</span> 解析文风
          </button>
        </div>

        <!-- 切换警告 -->
        <div class="sm-warning" v-if="showWarning">
          <span>⚠️</span>
          <span>中途切换文风将导致前后风格脱节！确认更改？</span>
          <div class="warning-actions">
            <button class="btn-warn-confirm" @click="confirmChange">确认</button>
            <button class="btn-warn-cancel" @click="showWarning = false">取消</button>
          </div>
        </div>
      </div>

      <div class="sm-footer">
        <button class="btn-save" @click="saveStyle">保 存 文 风</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import iconStyle from '@/assets/images/icon-style.png'

defineProps<{ visible: boolean }>()
defineEmits(['close'])

const config = reactive({
  tempo: '适中',
  tone: '古风文言化',
  descPreference: '心理多'
})

const tempoOptions = ['快节奏', '适中', '慢节奏']
const toneOptions = ['诙谐', '严肃', '古风文言化', '克苏鲁神秘感', '轻松日常']
const descOptions = ['动作多', '心理多', '环境多', '对话多', '均衡']

const sampleText = ref('')
const showWarning = ref(false)

const currentStyle = computed(() => {
  return `${config.tone} · ${config.tempo} · ${config.descPreference}`
})

const analyzeSample = () => {
  if (sampleText.value.length < 50) return
  // Simulate analysis
  config.tone = '古风文言化'
  config.tempo = '慢节奏'
  config.descPreference = '环境多'
}

const saveStyle = () => {
  showWarning.value = true
}

const confirmChange = () => {
  showWarning.value = false
  // TODO: Save to backend
}
</script>

<style lang="less" scoped>
.style-manager {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  justify-content: flex-end;
}

.sm-backdrop {
  position: absolute;
  inset: 0;
  background: rgba(0,0,0,0.5);
  backdrop-filter: blur(4px);
}

.sm-drawer {
  position: relative;
  width: 400px;
  max-width: 90vw;
  height: 100vh;
  background: rgba(17, 24, 39, 0.92);
  backdrop-filter: blur(20px);
  border-left: 1px solid var(--border-gold);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sm-glow {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--amber-gold), transparent);
  opacity: 0.4;
}

.sm-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 24px;
  border-bottom: 1px solid var(--border-subtle);

  .sm-icon {
    width: 28px;
    height: 28px;
    border-radius: 6px;
    object-fit: cover;
  }

  h3 {
    flex: 1;
    font-family: var(--font-heading);
    font-size: 1.2rem;
    color: var(--xuan-paper);
    letter-spacing: 0.15em;
  }

  .sm-close {
    background: none;
    border: none;
    color: var(--text-muted);
    font-size: 1rem;
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

.sm-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.sm-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sm-label {
  font-family: var(--font-heading);
  font-size: 0.95rem;
  color: var(--amber-gold);
  letter-spacing: 0.12em;
}

.sm-hint {
  font-size: 0.78rem;
  color: var(--text-muted);
}

.style-badge {
  position: relative;
  display: inline-flex;
  align-items: center;
  padding: 8px 16px;
  background: rgba(201,169,110,0.06);
  border: 1px solid rgba(201,169,110,0.2);
  border-radius: 6px;
  font-size: 0.9rem;
  color: var(--text-primary);
  letter-spacing: 0.08em;

  .badge-glow {
    position: absolute;
    left: 8px;
    top: 50%;
    transform: translateY(-50%);
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--amber-gold);
    box-shadow: 0 0 8px var(--amber-gold);
  }

  padding-left: 24px;
}

.option-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.opt-btn {
  padding: 6px 16px;
  font-family: var(--font-body);
  font-size: 0.82rem;
  color: var(--text-secondary);
  background: rgba(11,17,32,0.5);
  border: 1px solid var(--border-subtle);
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.3s;
  letter-spacing: 0.05em;

  &:hover {
    border-color: var(--border-gold);
    color: var(--amber-gold);
  }

  &.active {
    color: var(--amber-gold);
    background: rgba(201,169,110,0.12);
    border-color: var(--border-gold);
    box-shadow: 0 0 8px rgba(201,169,110,0.1);
  }
}

.sm-textarea {
  width: 100%;
  padding: 12px;
  background: rgba(11,17,32,0.6);
  border: 1px solid var(--border-subtle);
  border-radius: 8px;
  color: var(--text-primary);
  font-family: var(--font-body);
  font-size: 0.88rem;
  resize: vertical;
  outline: none;
  transition: border-color 0.3s;

  &:focus {
    border-color: var(--border-gold);
  }

  &::placeholder {
    color: var(--text-muted);
  }
}

.btn-analyze {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: rgba(201,169,110,0.08);
  border: 1px solid rgba(201,169,110,0.2);
  border-radius: 6px;
  color: var(--amber-gold);
  font-size: 0.85rem;
  cursor: pointer;
  transition: all 0.3s;
  align-self: flex-start;

  &:hover {
    background: rgba(201,169,110,0.15);
    border-color: var(--border-gold);
  }
}

.sm-warning {
  padding: 12px 16px;
  background: rgba(192, 60, 45, 0.1);
  border: 1px solid rgba(192, 60, 45, 0.3);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  font-size: 0.85rem;
  color: #e8a87c;
  animation: fadeInUp 0.3s ease;
}

.warning-actions {
  display: flex;
  gap: 8px;
}

.btn-warn-confirm {
  padding: 6px 16px;
  background: rgba(192,60,45,0.2);
  border: 1px solid rgba(192,60,45,0.4);
  border-radius: 4px;
  color: #e8a87c;
  cursor: pointer;
  font-size: 0.82rem;

  &:hover { background: rgba(192,60,45,0.3); }
}

.btn-warn-cancel {
  padding: 6px 16px;
  background: none;
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
  color: var(--text-muted);
  cursor: pointer;
  font-size: 0.82rem;

  &:hover { border-color: var(--border-gold); color: var(--text-secondary); }
}

.sm-footer {
  padding: 16px 24px;
  border-top: 1px solid var(--border-subtle);
}

.btn-save {
  width: 100%;
  padding: 12px;
  font-family: var(--font-heading);
  font-size: 1rem;
  letter-spacing: 0.2em;
  color: var(--amber-gold);
  background: linear-gradient(135deg, rgba(201,169,110,0.15), rgba(201,169,110,0.05));
  border: 1px solid var(--border-gold);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;

  &:hover {
    box-shadow: var(--shadow-gold);
    border-color: var(--border-glow);
    color: var(--xuan-paper);
  }
}
</style>
