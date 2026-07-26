<template>
  <div class="home-page">
    <header class="home-nav">
      <div class="nav-inner">
        <a class="home-brand" href="#top" aria-label="PenMate 首页">
          <span class="brand-mark" aria-hidden="true">P</span>
          <span>PenMate</span>
        </a>
        <nav class="nav-links" aria-label="首页导航">
          <a href="#workspace" data-testid="home-nav-link-workspace">工作台</a>
          <a href="#features" data-testid="home-nav-link-features">核心能力</a>
          <ThemeToggleButton />
          <button type="button" data-testid="home-nav-enter" @click="goToLogin">登录</button>
        </nav>
      </div>
    </header>

    <main id="top">
      <HomeHero :workbench-image="workbenchPreview" @enter-workbench="goToLogin" />
      <HomePreview :workbench-image="workbenchPreview" />
      <HomeFeatures />
      <HomeCta @enter-workbench="goToLogin" />
    </main>
    <HomeFooter />
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import HomeCta from '@/components/home/HomeCta.vue'
import HomeFeatures from '@/components/home/HomeFeatures.vue'
import HomeFooter from '@/components/home/HomeFooter.vue'
import HomeHero from '@/components/home/HomeHero.vue'
import HomePreview from '@/components/home/HomePreview.vue'
import ThemeToggleButton from '@/components/app/ThemeToggleButton.vue'
import workbenchPreview from '@/assets/images/workbench-preview.webp'

const router = useRouter()
const goToLogin = () => router.push('/login')
</script>

<style scoped>
.home-page { min-height: 100vh; overflow-x: hidden; color: var(--text-primary); background: var(--bg-canvas); }
.home-nav { position: sticky; top: 0; z-index: 100; height: 58px; background: color-mix(in srgb, var(--bg-surface) 96%, transparent); border-bottom: 1px solid var(--border-subtle); backdrop-filter: blur(10px); }
.nav-inner { display: flex; width: min(1180px, calc(100% - 40px)); height: 100%; align-items: center; justify-content: space-between; margin: 0 auto; }
.home-brand { display: inline-flex; align-items: center; gap: 10px; color: var(--text-primary); font-size: 15px; font-weight: 750; text-decoration: none; }
.brand-mark { display: grid; width: 30px; height: 30px; place-items: center; color: var(--text-inverse); background: var(--accent); border-radius: var(--radius-md); font-size: 14px; }
.nav-links { display: flex; align-items: center; gap: 18px; }
.nav-links a { color: var(--text-secondary); font-size: 13px; text-decoration: none; }
.nav-links a:hover, .nav-links a:focus-visible { color: var(--accent); outline: 0; }
.nav-links [data-testid="home-nav-enter"] { min-height: 34px; padding: 0 15px; color: var(--text-inverse); background: var(--accent); border: 1px solid var(--accent); border-radius: var(--radius-md); cursor: pointer; font-size: 13px; font-weight: 650; }
.nav-links [data-testid="home-nav-enter"]:hover, .nav-links [data-testid="home-nav-enter"]:focus-visible { background: var(--accent-hover); outline: 2px solid var(--accent-border); outline-offset: 2px; }
@media (max-width: 640px) {
  .nav-inner { width: calc(100% - 24px); }
  .nav-links { gap: 12px; }
  .nav-links a { display: none; }
}
</style>
