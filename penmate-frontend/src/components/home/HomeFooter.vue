<template>
  <footer class="home-footer" data-testid="home-footer">
    <div><strong>PenMate</strong><span>中文长篇创作工作台</span></div>
    <small>© 2026 PenMate</small>
  </footer>
</template>

<style scoped>
.home-footer { display: flex; width: min(1180px, calc(100% - 40px)); min-height: 78px; align-items: center; justify-content: space-between; gap: 20px; margin: 0 auto; color: var(--text-muted); border-top: 1px solid var(--border-subtle); }
.home-footer div { display: flex; align-items: baseline; gap: 12px; }
.home-footer strong { color: var(--text-primary); font-size: 14px; }
.home-footer span, .home-footer small { font-size: 11px; }
@media (max-width: 560px) {
  .home-footer { width: calc(100% - 24px); align-items: flex-start; flex-direction: column; justify-content: center; gap: 5px; }
}
</style>
