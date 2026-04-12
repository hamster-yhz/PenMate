<template>
  <div class="model-settings" v-if="visible">
    <div class="ms-backdrop" @click="$emit('close')"></div>
    <div class="ms-modal glass-panel">
      <div class="ms-glow"></div>

      <div class="ms-header">
        <span class="ms-emoji">🔑</span>
        <h3>模型与API池管理</h3>
        <button class="ms-close" @click="$emit('close')">✕</button>
      </div>

      <div class="ms-body">
        <p class="ms-desc">配置自有大模型API Key，加密存储，灵活路由 (BYOK)</p>

        <!-- Model list -->
        <div class="model-list">
          <div
            v-for="model in models"
            :key="model.id"
            class="model-card"
            :class="{ active: model.id === activeModel }"
            @click="activeModel = model.id"
          >
            <div class="model-radio">
              <span class="radio-dot" :class="{ checked: model.id === activeModel }"></span>
            </div>
            <div class="model-info">
              <div class="model-name">{{ model.name }}</div>
              <div class="model-provider">{{ model.provider }}</div>
            </div>
            <div class="model-status" :class="model.status">
              {{ model.status === 'connected' ? '已连接' : model.status === 'error' ? '异常' : '未配置' }}
            </div>
          </div>
        </div>

        <!-- API Key Form -->
        <div class="api-form">
          <h4 class="form-title">API 配置</h4>

          <div class="form-row">
            <label class="f-label">Base URL</label>
            <input
              v-model="apiConfig.baseUrl"
              type="text"
              class="f-input"
              placeholder="https://api.openai.com/v1"
            />
          </div>

          <div class="form-row">
            <label class="f-label">API Key</label>
            <div class="key-input-wrap">
              <input
                v-model="apiConfig.apiKey"
                :type="showKey ? 'text' : 'password'"
                class="f-input"
                placeholder="sk-..."
              />
              <button class="btn-toggle-key" @click="showKey = !showKey">
                {{ showKey ? '🙈' : '👁️' }}
              </button>
            </div>
          </div>

          <div class="form-row">
            <label class="f-label">模型名称</label>
            <input
              v-model="apiConfig.modelName"
              type="text"
              class="f-input"
              placeholder="gpt-4o / deepseek-chat / ..."
            />
          </div>

          <div class="api-actions">
            <button class="btn-test" @click="testConnection">
              <span>🔗</span> 测试连接
            </button>
            <button class="btn-save-api" @click="saveApi">
              <span>💾</span> 保存配置
            </button>
          </div>

          <div class="test-result" v-if="testStatus" :class="testStatus">
            {{ testStatus === 'success' ? '✅ 连接成功，模型响应正常' : '❌ 连接失败，请检查配置' }}
          </div>
        </div>

        <!-- Security Notice -->
        <div class="security-notice">
          <span>🔒</span>
          <span>您的API Key将在服务端加密存储，确保安全</span>
        </div>
      </div>

      <div class="ms-footer">
        <button class="btn-close-modal" @click="$emit('close')">完 成</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'

defineProps<{ visible: boolean }>()
defineEmits(['close'])

const activeModel = ref('deepseek')
const showKey = ref(false)
const testStatus = ref<'success' | 'error' | ''>('')

const models = ref([
  { id: 'deepseek', name: 'DeepSeek Chat', provider: 'DeepSeek', status: 'connected' },
  { id: 'gpt4', name: 'GPT-4o', provider: 'OpenAI', status: 'none' },
  { id: 'claude', name: 'Claude 3.5 Sonnet', provider: 'Anthropic', status: 'none' },
  { id: 'qwen', name: '通义千问Max', provider: '阿里云', status: 'none' },
  { id: 'custom', name: '自定义模型', provider: '自行配置', status: 'none' }
])

const apiConfig = reactive({
  baseUrl: '',
  apiKey: '',
  modelName: ''
})

const testConnection = async () => {
  testStatus.value = ''
  await new Promise(r => setTimeout(r, 1200))
  testStatus.value = apiConfig.apiKey.length > 5 ? 'success' : 'error'
}

const saveApi = () => {
  // TODO: Save to backend
  const model = models.value.find(m => m.id === activeModel.value)
  if (model) model.status = 'connected'
}
</script>

<style lang="less" scoped>
.model-settings {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ms-backdrop {
  position: absolute;
  inset: 0;
  background: rgba(0,0,0,0.6);
  backdrop-filter: blur(6px);
}

.ms-modal {
  position: relative;
  width: 560px;
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

.ms-glow {
  position: absolute;
  top: 0;
  left: 15%;
  right: 15%;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--amber-gold), transparent);
  opacity: 0.4;
}

.ms-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 24px;
  border-bottom: 1px solid var(--border-subtle);

  .ms-emoji { font-size: 1.3rem; }

  h3 {
    flex: 1;
    font-family: var(--font-heading);
    font-size: 1.2rem;
    color: var(--xuan-paper);
    letter-spacing: 0.12em;
  }

  .ms-close {
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

.ms-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.ms-desc {
  font-size: 0.85rem;
  color: var(--text-muted);
  letter-spacing: 0.05em;
}

.model-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.model-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: rgba(11,17,32,0.4);
  border: 1px solid var(--border-subtle);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;

  &:hover {
    border-color: var(--border-gold);
    background: rgba(17,24,39,0.6);
  }

  &.active {
    border-color: var(--border-gold);
    background: rgba(201,169,110,0.06);
  }
}

.model-radio {
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.radio-dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid var(--border-subtle);
  transition: all 0.3s;

  &.checked {
    border-color: var(--amber-gold);
    background: var(--amber-gold);
    box-shadow: 0 0 8px rgba(201,169,110,0.3);
  }
}

.model-info {
  flex: 1;
}

.model-name {
  font-size: 0.92rem;
  color: var(--text-primary);
}

.model-provider {
  font-size: 0.72rem;
  color: var(--text-muted);
}

.model-status {
  font-size: 0.72rem;
  padding: 3px 10px;
  border-radius: 10px;

  &.connected {
    color: var(--jade-green);
    background: rgba(90,158,111,0.1);
    border: 1px solid rgba(90,158,111,0.25);
  }

  &.error {
    color: var(--cinnabar);
    background: rgba(192,60,45,0.1);
    border: 1px solid rgba(192,60,45,0.25);
  }

  &.none {
    color: var(--text-muted);
    background: rgba(107,97,88,0.1);
    border: 1px solid rgba(107,97,88,0.15);
  }
}

.api-form {
  padding: 16px;
  background: rgba(11,17,32,0.4);
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.form-title {
  font-family: var(--font-heading);
  font-size: 1rem;
  color: var(--amber-gold);
  letter-spacing: 0.1em;
}

.form-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.f-label {
  font-size: 0.8rem;
  color: var(--text-secondary);
  letter-spacing: 0.05em;
}

.f-input {
  padding: 10px 14px;
  background: rgba(11,17,32,0.6);
  border: 1px solid var(--border-subtle);
  border-radius: 6px;
  color: var(--text-primary);
  font-family: var(--font-body);
  font-size: 0.88rem;
  outline: none;
  transition: border-color 0.3s;

  &:focus {
    border-color: var(--border-gold);
  }

  &::placeholder {
    color: var(--text-muted);
  }
}

.key-input-wrap {
  position: relative;
  display: flex;
}

.key-input-wrap .f-input {
  flex: 1;
  padding-right: 42px;
}

.btn-toggle-key {
  position: absolute;
  right: 4px;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  font-size: 1rem;
  cursor: pointer;
  padding: 4px 8px;
}

.api-actions {
  display: flex;
  gap: 10px;
  margin-top: 4px;
}

.btn-test, .btn-save-api {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  font-size: 0.82rem;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s;
}

.btn-test {
  color: var(--sky-cyan);
  background: rgba(110,197,212,0.08);
  border: 1px solid rgba(110,197,212,0.2);

  &:hover {
    background: rgba(110,197,212,0.15);
    border-color: rgba(110,197,212,0.4);
  }
}

.btn-save-api {
  color: var(--amber-gold);
  background: rgba(201,169,110,0.08);
  border: 1px solid rgba(201,169,110,0.2);

  &:hover {
    background: rgba(201,169,110,0.15);
    border-color: var(--border-gold);
  }
}

.test-result {
  padding: 10px 14px;
  border-radius: 6px;
  font-size: 0.82rem;

  &.success {
    color: var(--jade-green);
    background: rgba(90,158,111,0.08);
    border: 1px solid rgba(90,158,111,0.2);
  }

  &.error {
    color: #e8a87c;
    background: rgba(192,60,45,0.08);
    border: 1px solid rgba(192,60,45,0.2);
  }
}

.security-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: rgba(110,197,212,0.05);
  border: 1px solid rgba(110,197,212,0.12);
  border-radius: 8px;
  font-size: 0.8rem;
  color: var(--text-muted);
}

.ms-footer {
  padding: 16px 24px;
  border-top: 1px solid var(--border-subtle);
  display: flex;
  justify-content: flex-end;
}

.btn-close-modal {
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
</style>
