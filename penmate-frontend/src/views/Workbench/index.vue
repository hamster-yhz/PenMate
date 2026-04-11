<template>
  <a-layout class="workbench-layout">
    <a-layout-header class="workbench-header">
      <div class="logo">PenMate Workbench</div>
      <div class="actions">
        <span>当前小说：未命名</span>
        <a-button type="link">文风设置</a-button>
        <a-button type="link">模型设置</a-button>
      </div>
    </a-layout-header>
    <a-layout>
      <!-- 左侧：资源树面板 -->
      <a-layout-sider width="300" class="left-panel" theme="light">
        <a-tabs v-model:activeKey="activeLeftTab" class="left-tabs">
          <a-tab-pane key="outline" tab="大纲树">
            <a-tree
              :tree-data="outlineData"
              default-expand-all
            />
          </a-tab-pane>
          <a-tab-pane key="roles" tab="角色库">
            <div class="role-list">
              <a-card size="small" title="主角：林风">
                <p>性格：坚韧、机智</p>
              </a-card>
            </div>
          </a-tab-pane>
          <a-tab-pane key="world" tab="世界观">
            <p>暂无设定</p>
          </a-tab-pane>
        </a-tabs>
      </a-layout-sider>

      <!-- 中部：核心创作区 -->
      <a-layout-content class="center-editor">
        <div class="editor-toolbar">
          <a-space>
            <a-button size="small">保存</a-button>
            <a-button size="small">撤销</a-button>
          </a-space>
          <span class="word-count">字数: 0</span>
        </div>
        <textarea class="main-editor" placeholder="在这里开始创作..."></textarea>
      </a-layout-content>

      <!-- 右侧：AI Agent 会话与控制台 -->
      <a-layout-sider width="350" class="right-panel" theme="light">
        <div class="agent-container">
          <div class="chat-history">
            <div class="message assistant">
              <div class="bubble">你好，我是你的AI写作助手。请问今天我们要写点什么？</div>
            </div>
          </div>
          <div class="chat-input-area">
            <a-textarea
              v-model:value="chatInput"
              placeholder="输入指令，例如：开始写第三卷第二章..."
              :auto-size="{ minRows: 3, maxRows: 6 }"
            />
            <div class="input-actions">
              <a-button type="primary" @click="sendMessage">发送</a-button>
            </div>
          </div>
        </div>
      </a-layout-sider>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const activeLeftTab = ref('outline')
const chatInput = ref('')

const outlineData = ref([
  {
    title: '第一卷：初入江湖',
    key: '0-0',
    children: [
      { title: '第一章：神秘黑影', key: '0-0-0' },
      { title: '第二章：风起云涌', key: '0-0-1' },
    ],
  },
])

const sendMessage = () => {
  if (!chatInput.value.trim()) return
  // TODO: Send message to agent
  chatInput.value = ''
}
</script>

<style lang="less" scoped>
.workbench-layout {
  height: 100vh;
  width: 100vw;
  overflow: hidden;

  .workbench-header {
    background: #001529;
    color: #fff;
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0 20px;
    height: 50px;
    line-height: 50px;

    .logo {
      font-size: 18px;
      font-weight: bold;
    }

    .actions {
      display: flex;
      align-items: center;
      gap: 15px;
    }
  }

  .left-panel {
    border-right: 1px solid #f0f0f0;
    display: flex;
    flex-direction: column;

    .left-tabs {
      height: 100%;
      :deep(.ant-tabs-content-holder) {
        overflow-y: auto;
        padding: 0 10px;
      }
    }

    .role-list {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }
  }

  .center-editor {
    background: #fff;
    display: flex;
    flex-direction: column;
    padding: 20px;

    .editor-toolbar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 10px;
      padding-bottom: 10px;
      border-bottom: 1px solid #eee;

      .word-count {
        color: #888;
        font-size: 12px;
      }
    }

    .main-editor {
      flex: 1;
      width: 100%;
      border: none;
      resize: none;
      outline: none;
      font-size: 16px;
      line-height: 1.8;
      font-family: 'SimSun', 'Songti SC', serif;
      padding: 10px 0;
    }
  }

  .right-panel {
    border-left: 1px solid #f0f0f0;
    background: #fafafa;

    .agent-container {
      display: flex;
      flex-direction: column;
      height: 100%;

      .chat-history {
        flex: 1;
        overflow-y: auto;
        padding: 15px;
        display: flex;
        flex-direction: column;
        gap: 15px;

        .message {
          display: flex;
          margin-bottom: 10px;

          &.assistant .bubble {
            background: #fff;
            border: 1px solid #e8e8e8;
            border-radius: 4px;
            padding: 10px;
            max-width: 85%;
          }
        }
      }

      .chat-input-area {
        padding: 15px;
        background: #fff;
        border-top: 1px solid #f0f0f0;

        .input-actions {
          display: flex;
          justify-content: flex-end;
          margin-top: 10px;
        }
      }
    }
  }
}
</style>

