<template>
  <footer class="ancient-footer" data-testid="home-footer">
    <div class="footer-inner">
      <div class="footer-brand">
        <img :src="logoImg" alt="PenMate" class="footer-logo" />
        <span>PenMate · 笔友</span>
      </div>
      <div class="footer-text">执笔问道 · AI小说Copilot写作平台</div>
      <div class="footer-copy">© 2026 PenMate. All rights reserved.</div>
    </div>
  </footer>
</template>

<script setup lang="ts">
defineProps<{
  logoImg: string
}>()
</script>

<style scoped lang="less">
.ancient-footer {
  position: relative;
  z-index: 1;
  border-top: 1px solid var(--border-subtle);
  padding: 48px 48px 32px;
  background: rgba(11, 17, 32, 0.8);
}

.footer-inner {
  max-width: var(--content-max);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.footer-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  font-family: var(--font-heading);
  font-size: 1.2rem;
  color: var(--amber-gold);
  letter-spacing: 0.15em;
}

.footer-logo {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
}

.footer-text {
  font-size: 0.85rem;
  color: var(--text-muted);
  letter-spacing: 0.15em;
}

.footer-copy {
  font-size: 0.75rem;
  color: var(--text-muted);
  opacity: 0.6;
  margin-top: 8px;
}
</style>
