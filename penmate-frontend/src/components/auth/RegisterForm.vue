<template>
  <form class="login-form" @submit.prevent="handleSubmit">
    <div class="form-group">
      <label class="form-label">
        <img :src="iconAgent" alt="" class="label-icon" />
        <span>用户名</span>
      </label>
      <input
        :value="username"
        type="text"
        class="form-input"
        placeholder="取一个笔名"
        autocomplete="username"
        @input="$emit('update:username', ($event.target as HTMLInputElement).value)"
      />
    </div>
    <div class="form-group">
      <label class="form-label">
        <span class="label-icon-text">📮</span>
        <span>邮箱</span>
      </label>
      <input
        :value="email"
        type="email"
        class="form-input"
        placeholder="请输入邮箱地址"
        autocomplete="email"
        @input="$emit('update:email', ($event.target as HTMLInputElement).value)"
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
        placeholder="设置密码（6位以上）"
        autocomplete="new-password"
        @input="$emit('update:password', ($event.target as HTMLInputElement).value)"
      />
    </div>
    <div class="form-group">
      <label class="form-label">
        <span class="label-icon-text">🔐</span>
        <span>确认密码</span>
      </label>
      <input
        :value="confirmPassword"
        type="password"
        class="form-input"
        placeholder="再次输入密码"
        autocomplete="new-password"
        @input="$emit('update:confirmPassword', ($event.target as HTMLInputElement).value)"
      />
    </div>
    <button type="submit" class="btn-submit btn-ancient" :disabled="loading">
      <span v-if="!loading">开 启 创 作 之 旅</span>
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

const props = defineProps<{
  username: string
  email: string
  password: string
  confirmPassword: string
  loading: boolean
}>()

const emit = defineEmits<{
  'update:username': [value: string]
  'update:email': [value: string]
  'update:password': [value: string]
  'update:confirmPassword': [value: string]
  submit: []
}>()

const handleSubmit = () => {
  if (props.loading) {
    return
  }

  emit('submit')
}
</script>

<style scoped lang="less">
@import './auth-form.less';
</style>
