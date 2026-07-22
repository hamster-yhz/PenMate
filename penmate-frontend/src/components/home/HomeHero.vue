<template>
  <section class="home-hero" data-testid="home-hero">
    <img :src="workbenchImage" alt="" class="hero-workbench" aria-hidden="true" />
    <div class="hero-scrim" aria-hidden="true"></div>
    <div class="hero-inner">
      <p class="hero-eyebrow">中文长篇创作工作台</p>
      <h1>PenMate</h1>
      <p class="hero-lead">让正文写作、复杂设定与 AI 协作保持在同一个安静、可控的工作空间。</p>
      <div class="hero-actions">
        <button type="button" data-testid="home-hero-primary-cta" @click="$emit('enter-workbench')">
          登录工作台 <ArrowRightOutlined aria-hidden="true" />
        </button>
        <a href="#workspace">查看真实界面</a>
      </div>
      <div class="hero-facts" aria-label="产品能力">
        <span>纯文本正文</span>
        <span>Story Bible</span>
        <span>AI 章节编辑</span>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ArrowRightOutlined } from '@ant-design/icons-vue'

defineProps<{ workbenchImage: string }>()
defineEmits<{ 'enter-workbench': [] }>()
</script>

<style scoped>
.home-hero { position: relative; height: min(760px, calc(100svh - 108px)); min-height: 440px; overflow: hidden; background: var(--bg-editor); border-bottom: 1px solid var(--border-subtle); }
.hero-workbench { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; object-position: center top; }
.hero-scrim { position: absolute; inset: 0 auto 0 0; width: 56%; background: color-mix(in srgb, var(--bg-surface) 91%, transparent); clip-path: polygon(0 0, 100% 0, 78% 100%, 0 100%); }
.hero-inner { position: relative; z-index: 1; display: flex; width: min(1180px, calc(100% - 40px)); height: 100%; flex-direction: column; justify-content: center; align-items: flex-start; margin: 0 auto; }
.hero-eyebrow { margin: 0 0 14px; color: var(--accent); font-size: 12px; font-weight: 700; }
h1 { margin: 0; color: var(--text-primary); font-size: 64px; line-height: 1; letter-spacing: 0; }
.hero-lead { width: min(500px, 44vw); margin: 24px 0 0; color: var(--text-secondary); font-family: var(--font-writing); font-size: 19px; line-height: 1.8; }
.hero-actions { display: flex; align-items: center; gap: 20px; margin-top: 34px; }
.hero-actions button { display: inline-flex; min-height: 42px; align-items: center; gap: 8px; padding: 0 18px; color: var(--text-inverse); background: var(--accent); border: 1px solid var(--accent); border-radius: var(--radius-md); cursor: pointer; font-weight: 700; }
.hero-actions a { color: var(--text-primary); text-decoration: none; border-bottom: 1px solid var(--border-strong); font-size: 13px; font-weight: 650; }
.hero-actions button:hover, .hero-actions button:focus-visible { background: var(--accent-hover); outline: 2px solid var(--accent-border); outline-offset: 2px; }
.hero-actions a:hover, .hero-actions a:focus-visible { color: var(--accent); border-color: var(--accent); outline: 0; }
.hero-facts { display: flex; flex-wrap: wrap; gap: 0; margin-top: 44px; color: var(--text-muted); font-size: 12px; }
.hero-facts span { padding-right: 14px; }
.hero-facts span + span { padding-left: 14px; border-left: 1px solid var(--border-strong); }
@media (max-width: 760px) {
  .home-hero { height: calc(100svh - 92px); min-height: 440px; }
  .hero-workbench { object-position: 36% top; }
  .hero-scrim { width: 100%; clip-path: none; background: color-mix(in srgb, var(--bg-surface) 88%, transparent); }
  .hero-inner { width: calc(100% - 40px); justify-content: flex-end; padding-bottom: 54px; }
  h1 { font-size: 44px; }
  .hero-lead { width: 100%; max-width: 520px; font-size: 17px; }
  .hero-facts { margin-top: 30px; }
}
@media (prefers-reduced-motion: reduce) {
  * { scroll-behavior: auto !important; }
}
</style>
