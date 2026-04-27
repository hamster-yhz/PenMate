<template>
  <form class="login-form" data-testid="login-form" @submit.prevent="handleSubmit">
    <div class="form-group">
      <label class="form-label">
        <img :src="iconAgent" alt="" class="label-icon" />
        <span>账号</span>
      </label>
      <input
        :value="username"
        type="text"
        class="form-input"
        data-testid="login-username"
        placeholder="请输入用户名或邮箱"
        autocomplete="username"
        @input="emit('update:username', ($event.target as HTMLInputElement).value)"
      />
    </div>
    <div class="form-group">
      <label class="form-label">
        <span class="label-icon-text">🔐</span>
        <span>密码</span>
      </label>
      <input
        :value="password"
        type="password"
        class="form-input"
        data-testid="login-password"
        placeholder="请输入密码"
        autocomplete="current-password"
        @input="emit('update:password', ($event.target as HTMLInputElement).value)"
      />
    </div>
    <div class="form-extra">
      <label class="remember-me">
        <input
          :checked="remember"
          type="checkbox"
          data-testid="login-remember"
          @change="emit('update:remember', ($event.target as HTMLInputElement).checked)"
        />
        <span>记住我</span>
      </label>
      <a href="#" class="forgot-link">忘记密码？</a>
    </div>
    <button type="submit" class="btn-submit btn-ancient" data-testid="login-submit" :disabled="loading">
      <span v-if="!loading">踏 入 书 阁</span>
      <span v-else class="loading-text">
        <span class="loading-dot"></span>
        <span class="loading-dot"></span>
        <span class="loading-dot"></span>
      </span>
    </button>
  </form>
</template>

<script setup lang="ts">
import iconAgent from '@/assets/images/icon-agent.png'

export interface LoginFormSubmitPayload {
  username: string
  password: string
  remember: boolean
}

const props = defineProps<{
  username: string
  password: string
  remember: boolean
  loading: boolean
}>()

const emit = defineEmits<{
  'update:username': [value: string]
  'update:password': [value: string]
  'update:remember': [value: boolean]
  submit: [payload: LoginFormSubmitPayload]
}>()

const handleSubmit = () => {
  if (props.loading) {
    return
  }

  emit('submit', {
    username: props.username,
    password: props.password,
    remember: props.remember,
  })
}
</script>

<style scoped lang="less">
@import './auth-form.less';
</style>
