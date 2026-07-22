<template>
  <section class="home-cta" data-testid="home-cta">
    <div>
      <p>继续你的长篇故事</p>
      <h2>回到作品，而不是从配置开始。</h2>
    </div>
    <button type="button" data-testid="home-cta-button" @click="$emit('enter-workbench')">
      登录 PenMate <ArrowRightOutlined aria-hidden="true" />
    </button>
  </section>
</template>

<script setup lang="ts">
import { ArrowRightOutlined } from '@ant-design/icons-vue'
defineEmits<{ 'enter-workbench': [] }>()
</script>

<style scoped>
.home-cta { display: flex; width: min(1180px, calc(100% - 40px)); min-height: 220px; align-items: center; justify-content: space-between; gap: 40px; margin: 80px auto; padding: 48px 56px; color: #f3f8f5; background: #173e32; border-radius: var(--radius-lg); }
.home-cta p { margin: 0 0 10px; color: #9fd6c0; font-size: 12px; font-weight: 700; }
.home-cta h2 { max-width: 620px; margin: 0; color: #f3f8f5; font-size: 28px; line-height: 1.35; letter-spacing: 0; }
button { display: inline-flex; min-height: 42px; flex: 0 0 auto; align-items: center; gap: 8px; padding: 0 18px; color: #173e32; background: #f3f8f5; border: 1px solid #f3f8f5; border-radius: var(--radius-md); cursor: pointer; font-weight: 700; }
button:hover, button:focus-visible { color: #0e3026; background: #dcebe4; outline: 2px solid #9fd6c0; outline-offset: 3px; }
@media (max-width: 700px) {
  .home-cta { width: calc(100% - 24px); min-height: 0; align-items: flex-start; flex-direction: column; margin: 52px auto; padding: 34px 28px; }
  .home-cta h2 { font-size: 24px; }
}
</style>
