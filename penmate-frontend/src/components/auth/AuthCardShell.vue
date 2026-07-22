<template>
  <section class="login-card" aria-labelledby="login-heading">
    <div class="login-header">
      <span class="brand-mark" aria-hidden="true">P</span>
      <div>
        <p class="brand-name">PenMate</p>
        <h1 id="login-heading">登录</h1>
      </div>
    </div>
    <slot />
  </section>
</template>

<style scoped>
.login-card {
  width: min(100%, 400px);
  padding: 30px;
  background: color-mix(in srgb, var(--bg-surface) 96%, transparent);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
}

.login-header {
  display: flex;
  align-items: center;
  gap: 13px;
  margin-bottom: 28px;
}

.brand-mark {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  color: var(--text-inverse);
  background: var(--accent);
  border-radius: var(--radius-md);
  font-size: 19px;
  font-weight: 750;
}

.brand-name {
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 650;
}

h1 {
  margin-top: 2px;
  font-size: 22px;
}

@media (max-width: 520px) {
  .login-card {
    padding: 24px;
  }
}
</style>
