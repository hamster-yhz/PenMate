<template>
  <div class="home-container">
    <div class="header">
      <h1>PenMate - AI小说Copilot</h1>
      <a-button type="primary" @click="goToWorkbench">进入工作区</a-button>
    </div>
    <div class="content">
      <p>AI是主力写手，作者充当导演与编辑。</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const goToWorkbench = () => {
  router.push('/workbench')
}
</script>

<style lang="less" scoped>
.home-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background-color: #f0f2f5;

  .header {
    text-align: center;
    margin-bottom: 2rem;

    h1 {
      font-size: 3rem;
      color: #333;
    }
  }

  .content {
    font-size: 1.5rem;
    color: #666;
  }
}
</style>

