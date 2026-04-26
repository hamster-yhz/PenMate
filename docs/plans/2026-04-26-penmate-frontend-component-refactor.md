# PenMate Frontend 组件拆分与重构 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use [executing-plans] mode to implement this plan task-by-task.

**Goal:** 将 [`penmate-frontend`](penmate-frontend) 中当前高度耦合的页面与界面逻辑拆分为细粒度、可测试、可复用的 Vue 组件与组合式逻辑单元，优先降低 [`Workbench/index.vue`](penmate-frontend/src/views/Workbench/index.vue) 的复杂度并建立统一的页面壳层、状态边界与样式边界。

**Architecture:** 采用“页面容器 + 场景组件 + 纯展示组件 + composables”的分层重构方式。页面容器仅负责路由上下文、数据编排与副作用触发；场景组件负责一个完整业务片段；展示组件只负责 props / emits 驱动渲染；跨组件可复用的异步流程、草稿缓存、工作台上下文、消息流等下沉到 composables 或 utils。样式边界按组件 scoped 样式 + 少量全局 design tokens 组织，避免继续在单个视图内堆叠数百行样式。

**Tech Stack:** [Vue 3](penmate-frontend/package.json), [`script setup`](penmate-frontend/src/views/Workbench/index.vue), [TypeScript](penmate-frontend/package.json), [Vue Router](penmate-frontend/src/router/index.ts), [Vitest](penmate-frontend/package.json), [Vue Test Utils](penmate-frontend/package.json), [Ant Design Vue](penmate-frontend/package.json), [Less](penmate-frontend/package.json)

---

## 0. 现状诊断与拆分优先级

### 最臃肿视图文件排序

1. [`Workbench/index.vue`](penmate-frontend/src/views/Workbench/index.vue) — 2299 行视图文件 / 2538 行总文件内容，当前是绝对优先级 P0
2. [`Home/index.vue`](penmate-frontend/src/views/Home/index.vue) — 1149 行，营销页结构与视觉样式严重耦合
3. [`MyBooks/index.vue`](penmate-frontend/src/views/MyBooks/index.vue) — 603 行，列表/统计/弹窗/表单堆叠
4. [`Login/index.vue`](penmate-frontend/src/views/Login/index.vue) — 540 行，登录与注册 UI 共存，状态与样式耦合
5. [`Profile/index.vue`](penmate-frontend/src/views/Profile/index.vue) — 436 行，资料卡、偏好、API key、安全设置耦合
6. [`DomainConsole/index.vue`](penmate-frontend/src/views/DomainConsole/index.vue) — 209 行，业务密度高但体量可控，可后置

### 当前已存在组件的职责问题

- [`ApprovalCard.vue`](penmate-frontend/src/components/workbench/ApprovalCard.vue)：展示职责清晰，可保留为纯展示组件。
- [`StyleManager.vue`](penmate-frontend/src/components/workbench/StyleManager.vue)：内部同时持有列表加载、切换默认、删除、解析样文、保存文风，适合继续拆分为容器 + 表单子组件。
- [`PluginWorkshop.vue`](penmate-frontend/src/components/workbench/PluginWorkshop.vue)：同时承担目录加载、安装启停、卡片渲染。
- [`ModelSettings.vue`](penmate-frontend/src/components/workbench/ModelSettings.vue)：内部包含官方模型列表、用户模型列表、表单状态、测试连接、删除、保存多个子场景，复杂度已接近一个页面。

### 关键耦合事实

[`Workbench/index.vue`](penmate-frontend/src/views/Workbench/index.vue) 当前混合以下职责：

- 顶部 Header / 用户菜单渲染
- 左侧大纲树、角色卡、世界观卡、关系维护 UI
- 编辑器工具栏、正文输入、撤销重做、格式化
- 章节切换、版本查看、版本恢复、章节发布
- 本地草稿缓存与远端内容获取
- 右侧 AI 会话、SSE 流处理、轮询兜底、审批流程
- 模型/插件/文风弹层开关状态
- 项目上下文解析（query/localStorage/session）
- 大量 scoped less 样式

这说明目标不能只是“拆 template”，必须同时拆状态与副作用，否则只是把复杂度横向搬家。

---

## 1. 目标目录结构

建议先建立以下结构，再逐步迁移：

```text
penmate-frontend/src/
  components/
    app/
      AppBrand.vue
      AppUserMenu.vue
      AppPageNav.vue
      AppStatsBar.vue
      AppModalShell.vue
    home/
      HomeHero.vue
      HomeFeatures.vue
      HomeWorkflow.vue
      HomePreview.vue
      HomeCta.vue
      HomeFooter.vue
    auth/
      AuthCardShell.vue
      LoginForm.vue
      RegisterForm.vue
      AuthModeTabs.vue
    bookshelf/
      BookStatsBar.vue
      BookActionBar.vue
      BookCard.vue
      BookEditorModal.vue
      DeleteBookDialog.vue
    profile/
      ProfileHeroCard.vue
      ProfileSecurityPanel.vue
      ProfilePreferencePanel.vue
      ProfileApiKeyPanel.vue
      ProfileDangerZone.vue
    workbench/
      WorkbenchHeader.vue
      WorkbenchLeftPanel.vue
      WorkbenchEditorPanel.vue
      WorkbenchRightPanel.vue
      WorkbenchUserDropdown.vue
      outline/
        OutlineTree.vue
        OutlineVolumeNode.vue
        OutlineChapterNode.vue
      cards/
        CharacterCardList.vue
        CharacterCardItem.vue
        WorldCardList.vue
        WorldCardItem.vue
        CardRelationPanel.vue
      editor/
        EditorToolbar.vue
        EditorTextarea.vue
        EditorStatusbar.vue
        VersionPreviewPane.vue
      chat/
        AgentSessionHeader.vue
        ConversationHistoryPanel.vue
        ChatMessageList.vue
        ChatMessageItem.vue
        ChatComposer.vue
        ModelWarningBanner.vue
      modals/
        StyleManagerDrawer.vue
        StyleSelector.vue
        StyleConfigForm.vue
        StyleSampleAnalyzer.vue
        PluginWorkshopModal.vue
        PluginCatalogGrid.vue
        PluginCard.vue
        ModelSettingsModal.vue
        OfficialModelGrid.vue
        UserModelGrid.vue
        ModelConfigForm.vue
  composables/
    app/
      useSessionProfile.ts
      useWorkbenchContext.ts
    workbench/
      useWorkbenchOutline.ts
      useWorkbenchCards.ts
      useWorkbenchEditor.ts
      useWorkbenchVersions.ts
      useWorkbenchDraft.ts
      useWorkbenchChat.ts
      useWorkbenchApprovals.ts
      useWorkbenchModel.ts
      useWorkbenchPlugins.ts
    home/
      useHomeEffects.ts
    auth/
      useLoginSubmit.ts
    bookshelf/
      useBookshelf.ts
    profile/
      useProfileSettings.ts
  views/
    Home/index.vue
    Login/index.vue
    MyBooks/index.vue
    Profile/index.vue
    Workbench/index.vue
```

---

## 2. 状态边界设计

### 2.1 Workbench 状态拆分

#### A. 页面级容器状态：仅保留在 [`Workbench/index.vue`](penmate-frontend/src/views/Workbench/index.vue)

只保留以下最外层状态：

- `showStyleManager`
- `showPluginWorkshop`
- `showModelSettings`
- `leftCollapsed`
- `rightCollapsed`
- `activeLeftTab`
- 路由派生上下文注入（projectId/operatorId）

#### B. [`useWorkbenchContext.ts`](penmate-frontend/src/composables/workbench/useWorkbenchContext.ts)

负责：

- 解析 [`route.query`](penmate-frontend/src/router/index.ts)
- 读写 `LAST_PROJECT_ID_KEY`
- 读写 `LAST_OPERATOR_ID_KEY`
- 从 [`getSession()`](penmate-frontend/src/stores/session.ts:41) 派生 `userId/userName/userEmail`
- 对外提供：
  - `projectId`
  - `operatorId`
  - `username`
  - `userEmail`
  - `ensureContext()`

#### C. [`useWorkbenchOutline.ts`](penmate-frontend/src/composables/workbench/useWorkbenchOutline.ts)

负责：

- `outlineData`
- `activeChapter`
- `currentChapterTitle`
- `editingNodeKey/editingNodeValue`
- `loadOutline()`
- `addVolume()`
- `addChapter()`
- `deleteVolume()`
- `deleteChapter()`
- `moveVolume()`
- `moveChapter()`
- `renameNode()`
- `mapOutlineTree()`

#### D. [`useWorkbenchDraft.ts`](penmate-frontend/src/composables/workbench/useWorkbenchDraft.ts)

负责：

- `getDraftStorageKey()`
- `saveChapterDraftLocal()`
- `readChapterDraftLocal()`

#### E. [`useWorkbenchEditor.ts`](penmate-frontend/src/composables/workbench/useWorkbenchEditor.ts)

负责：

- `editorContent`
- `wordCount`
- `currentLine/currentCol`
- `selectedText`
- `undoStack/redoStack`
- `onEditorInput()`
- `wrapSelection()`
- `insertPrefix()`
- `editorUndo()/editorRedo()`
- `mergeToEditor()`
- `replaceSelected()`
- `selectChapterDraft()`

#### F. [`useWorkbenchVersions.ts`](penmate-frontend/src/composables/workbench/useWorkbenchVersions.ts)

负责：

- `chapterVersions`
- `selectedVersionNo`
- `selectedVersionContent`
- `versionDiffSummary`
- `versionBusy`
- `loadChapterVersions()`
- `viewSelectedVersion()`
- `restoreSelectedVersion()`
- `publishCurrentChapter()`
- `refreshEditorFromRemote()`
- `uploadAndCommitContent()`

#### G. [`useWorkbenchCards.ts`](penmate-frontend/src/composables/workbench/useWorkbenchCards.ts)

负责：

- `projectCards`
- `cardRelations`
- `relationFromId/relationToId/relationType`
- `loadCardsAndRelations()`
- `createCardQuick()`
- `saveCard()`
- `deleteCardById()`
- `createRelation()`
- `deleteRelationById()`
- `cardNameById()`

#### H. [`useWorkbenchChat.ts`](penmate-frontend/src/composables/workbench/useWorkbenchChat.ts)

负责：

- `messages`
- `chatInput`
- `isGenerating`
- `generationPhase`
- `generationTaskStatus`
- `streamingAssistantMsgId`
- `conversationList/currentConversationId/conversationLoading`
- `sendMessage()`
- `consumeGenerationStream()`
- `pollGenerationAsFallback()`
- `loadConversationHistory()`
- `loadConversationList()`
- `selectConversation()`
- `scrollChat()`

#### I. [`useWorkbenchApprovals.ts`](penmate-frontend/src/composables/workbench/useWorkbenchApprovals.ts)

负责：

- `approvalBusyIds`
- `handleApprove()`
- `handleReject()`
- `isApprovalBusy()`

#### J. [`useWorkbenchModel.ts`](penmate-frontend/src/composables/workbench/useWorkbenchModel.ts)

负责：

- `activeModelConfigId`
- `currentModelName`
- `refreshActiveModelInfo()`
- `ensureModelConfigId()`

#### K. [`useWorkbenchPlugins.ts`](penmate-frontend/src/composables/workbench/useWorkbenchPlugins.ts)

负责：

- `activePlugins`
- `loadActivePlugins()`

### 2.2 其他页面状态边界

- [`Home/index.vue`](penmate-frontend/src/views/Home/index.vue)：只保留 section 数据源与导航动作；滚动监听、粒子 style 生成下沉到 [`useHomeEffects.ts`](penmate-frontend/src/composables/home/useHomeEffects.ts)
- [`Login/index.vue`](penmate-frontend/src/views/Login/index.vue)：提交逻辑下沉到 [`useLoginSubmit.ts`](penmate-frontend/src/composables/auth/useLoginSubmit.ts)，表单 UI 拆成 [`LoginForm.vue`](penmate-frontend/src/components/auth/LoginForm.vue) 与 [`RegisterForm.vue`](penmate-frontend/src/components/auth/RegisterForm.vue)
- [`MyBooks/index.vue`](penmate-frontend/src/views/MyBooks/index.vue)：书架加载、表单、删除状态下沉到 [`useBookshelf.ts`](penmate-frontend/src/composables/bookshelf/useBookshelf.ts)
- [`Profile/index.vue`](penmate-frontend/src/views/Profile/index.vue)：资料编辑、安全设置、偏好设置拆到 [`useProfileSettings.ts`](penmate-frontend/src/composables/profile/useProfileSettings.ts)

---

## 3. 样式边界设计

### 原则

1. 每个新组件拥有独立 `scoped` 样式。
2. 重复视觉 token 继续放在 [`src/style.css`](penmate-frontend/src/style.css)；不把业务选择器继续堆入全局。
3. 页面容器只保留 layout 类，不保留业务块样式。
4. 任何超过 150 行样式的组件都要再评估是否继续拆分。

### 应抽取的通用样式壳层

- [`AppPageNav.vue`](penmate-frontend/src/components/app/AppPageNav.vue)：复用 [`Home/index.vue`](penmate-frontend/src/views/Home/index.vue)、[`MyBooks/index.vue`](penmate-frontend/src/views/MyBooks/index.vue)、[`Profile/index.vue`](penmate-frontend/src/views/Profile/index.vue) 顶部导航结构
- [`AppModalShell.vue`](penmate-frontend/src/components/app/AppModalShell.vue)：复用工作台弹层的遮罩、面板、头尾布局
- [`glass-panel`](penmate-frontend/src/views/Login/index.vue) 视觉风格应收敛到统一容器类或壳层组件
- 统一按钮族：树操作按钮、顶部工具按钮、危险按钮、主按钮，避免每个页面重复定义

### Workbench 样式拆分边界

- [`WorkbenchHeader.vue`](penmate-frontend/src/components/workbench/WorkbenchHeader.vue)：header + user dropdown 样式
- [`WorkbenchLeftPanel.vue`](penmate-frontend/src/components/workbench/WorkbenchLeftPanel.vue)：tab 容器与左侧布局
- [`OutlineTree.vue`](penmate-frontend/src/components/workbench/outline/OutlineTree.vue)：大纲树节点样式
- [`CharacterCardItem.vue`](penmate-frontend/src/components/workbench/cards/CharacterCardItem.vue)：角色卡样式
- [`WorldCardItem.vue`](penmate-frontend/src/components/workbench/cards/WorldCardItem.vue)：世界卡样式
- [`CardRelationPanel.vue`](penmate-frontend/src/components/workbench/cards/CardRelationPanel.vue)：关系维护区
- [`WorkbenchEditorPanel.vue`](penmate-frontend/src/components/workbench/WorkbenchEditorPanel.vue)：编辑区布局
- [`WorkbenchRightPanel.vue`](penmate-frontend/src/components/workbench/WorkbenchRightPanel.vue)：聊天区布局

---

## 4. 可复用机会识别

### 高价值复用

1. 顶部导航：[`MyBooks/index.vue`](penmate-frontend/src/views/MyBooks/index.vue) 与 [`Profile/index.vue`](penmate-frontend/src/views/Profile/index.vue) 结构非常接近。
2. 粒子背景：[`Home/index.vue`](penmate-frontend/src/views/Home/index.vue)、[`Login/index.vue`](penmate-frontend/src/views/Login/index.vue)、[`MyBooks/index.vue`](penmate-frontend/src/views/MyBooks/index.vue)、[`Profile/index.vue`](penmate-frontend/src/views/Profile/index.vue) 都有粒子装饰。
3. 统计条：[`Home/index.vue`](penmate-frontend/src/views/Home/index.vue) hero stats、[`MyBooks/index.vue`](penmate-frontend/src/views/MyBooks/index.vue) stats bar、[`Profile/index.vue`](penmate-frontend/src/views/Profile/index.vue) stats card 可统一出展示模式。
4. 模态框：书架创建/删除弹窗与工作台设置弹层可统一外壳。
5. 卡片列表：工作台插件卡片、模型卡片、书架卡片都可复用基础 card shell。

### 中价值复用

- 空状态组件
- section header 组件（尤其 [`Home/index.vue`](penmate-frontend/src/views/Home/index.vue) 多段重复）
- 轻量表单行组件（label + input/textarea/select）
- 行内状态提示条（warning/success/info）

---

## 5. 风险点与规避策略

### 风险 1：Workbench 拆分后事件链断裂

**根因：** 当前 [`sendMessage()`](penmate-frontend/src/views/Workbench/index.vue:1668) 同时依赖 `projectId`、`operatorId`、`activeChapter`、`activePlugins`、`currentModelName`、`messages`。

**策略：**
- 先抽 composable，再抽视觉组件。
- 先用现有模板绑定新 composable，确认行为不变，再切模板。

### 风险 2：大纲树与章节正文映射丢失

**根因：** [`mapOutlineTree()`](penmate-frontend/src/views/Workbench/index.vue:1866) 通过 `outlineNodeId -> chapterId` 做桥接，拆分时容易把两个域割裂。

**策略：**
- 新建 `OutlineVolumeNode` / `OutlineChapterNode` 类型文件。
- 单测覆盖 `mapOutlineTree()` 的映射结果。

### 风险 3：版本/草稿/远端正文三套数据源相互覆盖

**根因：** [`refreshEditorFromRemote()`](penmate-frontend/src/views/Workbench/index.vue:1211)、[`saveChapterDraftLocal()`](penmate-frontend/src/views/Workbench/index.vue:1195)、[`selectChapter()`](penmate-frontend/src/views/Workbench/index.vue:1444) 有状态竞争。

**策略：**
- 将“本地草稿优先级”和“远端回填时机”写成明确测试。
- 把本地草稿 API 抽出独立 composable，不允许组件直接操作 localStorage。

### 风险 4：SSE 与轮询兜底逻辑回归

**根因：** [`consumeGenerationStream()`](penmate-frontend/src/views/Workbench/index.vue:724) 与 [`pollGenerationAsFallback()`](penmate-frontend/src/views/Workbench/index.vue:712) 是复杂时序逻辑。

**策略：**
- 优先为纯逻辑分支写单测。
- UI 组件只消费 `messages`, `isGenerating`, `generationStatusText`。

### 风险 5：样式拆分引发视觉偏差

**根因：** 当前大量样式依赖单文件内部级联选择器。

**策略：**
- 每次只拆一个版块并进行截图/手工对比。
- 保留旧 class 名一段时间，逐步替换。

### 风险 6：Model / Plugin / Style 三个弹层内部再度长胖

**策略：**
- 每个弹层强制拆为 `容器组件 + Grid/List + Form`。
- 每个单组件限制一个主要交互场景。

---

## 6. 分阶段实施顺序

### Phase 1 — 建立测试与安全网（P0）

目标：在不改 UI 的前提下，为最危险的工作台逻辑建立可回归测试。

优先测试对象：
- `mapOutlineTree()`
- `normalizeObjectStorageUrl()`
- `hasObjectKeyInStorageUrl()`
- `normalizeDetailJsonInput()`
- `saveChapterDraftLocal()/readChapterDraftLocal()`
- `generationStatusText` 计算逻辑

### Phase 2 — 先拆 composables，不拆页面布局（P0）

目标：把 [`Workbench/index.vue`](penmate-frontend/src/views/Workbench/index.vue) 的业务逻辑移出页面文件，但模板先尽量不变。

顺序：
1. `useWorkbenchContext`
2. `useWorkbenchDraft`
3. `useWorkbenchOutline`
4. `useWorkbenchCards`
5. `useWorkbenchEditor`
6. `useWorkbenchVersions`
7. `useWorkbenchModel`
8. `useWorkbenchPlugins`
9. `useWorkbenchApprovals`
10. `useWorkbenchChat`

### Phase 3 — 拆 Workbench 视觉骨架（P0）

顺序：
1. `WorkbenchHeader`
2. `WorkbenchLeftPanel`
3. `WorkbenchEditorPanel`
4. `WorkbenchRightPanel`
5. `OutlineTree`
6. `CharacterCardList` / `WorldCardList`
7. `CardRelationPanel`
8. `ChatMessageList` / `ChatComposer`

### Phase 4 — 继续拆三个工作台弹层（P1）

- [`StyleManager.vue`](penmate-frontend/src/components/workbench/StyleManager.vue)
- [`PluginWorkshop.vue`](penmate-frontend/src/components/workbench/PluginWorkshop.vue)
- [`ModelSettings.vue`](penmate-frontend/src/components/workbench/ModelSettings.vue)

### Phase 5 — 清理其他页面（P1）

优先顺序：
1. [`MyBooks/index.vue`](penmate-frontend/src/views/MyBooks/index.vue)
2. [`Login/index.vue`](penmate-frontend/src/views/Login/index.vue)
3. [`Profile/index.vue`](penmate-frontend/src/views/Profile/index.vue)
4. [`Home/index.vue`](penmate-frontend/src/views/Home/index.vue)
5. [`DomainConsole/index.vue`](penmate-frontend/src/views/DomainConsole/index.vue)

理由：工作流页比营销页更影响长期维护成本。

---

## 7. 逐任务执行计划（2–5 分钟颗粒度）

### Task 1: 为 Workbench 纯逻辑建立测试安全网

Use [test-driven-development] mode for this task.

**Files:**
- Create: [`penmate-frontend/src/composables/workbench/__tests__/useWorkbenchDraft.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchDraft.spec.ts)
- Create: [`penmate-frontend/src/composables/workbench/__tests__/workbenchOutline.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/workbenchOutline.spec.ts)
- Create: [`penmate-frontend/src/composables/workbench/__tests__/workbenchStorage.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/workbenchStorage.spec.ts)
- Modify: [`penmate-frontend/src/views/Workbench/index.vue`](penmate-frontend/src/views/Workbench/index.vue)
- Test: [`penmate-frontend/src/composables/workbench/__tests__/`](penmate-frontend/src/composables/workbench/__tests__)

**Step 1: Write the failing test**

先提取并测试这些函数行为：
- `getDraftStorageKey`
- `saveChapterDraftLocal`
- `readChapterDraftLocal`
- `normalizeObjectStorageUrl`
- `hasObjectKeyInStorageUrl`
- `mapOutlineTree`

**Step 2: Run test to verify it fails**
Run: [`npm run test:run -- src/composables/workbench/__tests__/useWorkbenchDraft.spec.ts src/composables/workbench/__tests__/workbenchOutline.spec.ts src/composables/workbench/__tests__/workbenchStorage.spec.ts`](penmate-frontend/package.json)
Expected: 至少出现模块不存在或导出缺失失败。

**Step 3: Write minimal implementation**
创建：
- [`penmate-frontend/src/composables/workbench/workbenchDraft.ts`](penmate-frontend/src/composables/workbench/workbenchDraft.ts)
- [`penmate-frontend/src/composables/workbench/workbenchOutline.ts`](penmate-frontend/src/composables/workbench/workbenchOutline.ts)
- [`penmate-frontend/src/composables/workbench/workbenchStorage.ts`](penmate-frontend/src/composables/workbench/workbenchStorage.ts)

**Step 4: Run test to verify it passes**
Run: [`npm run test:run -- src/composables/workbench/__tests__/useWorkbenchDraft.spec.ts src/composables/workbench/__tests__/workbenchOutline.spec.ts src/composables/workbench/__tests__/workbenchStorage.spec.ts`](penmate-frontend/package.json)
Expected: 所有新增 spec 通过。

**Step 5: Commit**
Run: `git add penmate-frontend/src/composables/workbench docs/plans/2026-04-26-penmate-frontend-component-refactor.md && git commit -m "test(frontend): cover workbench helper logic"`

### Task 2: 抽离 Workbench 上下文与本地草稿逻辑

Use [test-driven-development] mode for this task.

**Files:**
- Create: [`penmate-frontend/src/composables/workbench/useWorkbenchContext.ts`](penmate-frontend/src/composables/workbench/useWorkbenchContext.ts)
- Create: [`penmate-frontend/src/composables/workbench/useWorkbenchDraft.ts`](penmate-frontend/src/composables/workbench/useWorkbenchDraft.ts)
- Modify: [`penmate-frontend/src/views/Workbench/index.vue`](penmate-frontend/src/views/Workbench/index.vue)
- Test: [`penmate-frontend/src/composables/workbench/__tests__/useWorkbenchContext.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchContext.spec.ts)

**Step 1: Write the failing test**
测试 `query -> session -> localStorage` 的上下文优先级。

**Step 2: Run test to verify it fails**
Run: [`npm run test:run -- src/composables/workbench/__tests__/useWorkbenchContext.spec.ts`](penmate-frontend/package.json)
Expected: 模块不存在或行为不符。

**Step 3: Write minimal implementation**
让 [`Workbench/index.vue`](penmate-frontend/src/views/Workbench/index.vue) 停止直接读写 localStorage/session 派生值。

**Step 4: Run test to verify it passes**
Run: [`npm run test:run -- src/composables/workbench/__tests__/useWorkbenchContext.spec.ts`](penmate-frontend/package.json)
Expected: spec 全绿。

**Step 5: Commit**
Run: `git add penmate-frontend/src/composables/workbench penmate-frontend/src/views/Workbench/index.vue && git commit -m "refactor(frontend): extract workbench context and draft state"`

### Task 3: 抽离大纲树与章节切换逻辑

Use [test-driven-development] mode for this task.

**Files:**
- Create: [`penmate-frontend/src/composables/workbench/useWorkbenchOutline.ts`](penmate-frontend/src/composables/workbench/useWorkbenchOutline.ts)
- Create: [`penmate-frontend/src/components/workbench/outline/OutlineTree.vue`](penmate-frontend/src/components/workbench/outline/OutlineTree.vue)
- Create: [`penmate-frontend/src/components/workbench/outline/OutlineVolumeNode.vue`](penmate-frontend/src/components/workbench/outline/OutlineVolumeNode.vue)
- Create: [`penmate-frontend/src/components/workbench/outline/OutlineChapterNode.vue`](penmate-frontend/src/components/workbench/outline/OutlineChapterNode.vue)
- Modify: [`penmate-frontend/src/views/Workbench/index.vue`](penmate-frontend/src/views/Workbench/index.vue)
- Test: [`penmate-frontend/src/components/workbench/outline/OutlineTree.spec.ts`](penmate-frontend/src/components/workbench/outline/OutlineTree.spec.ts)

**Step 1: Write the failing test**
验证节点渲染、选中章节、重命名 emit、移动 emit。

**Step 2: Run test to verify it fails**
Run: [`npm run test:run -- src/components/workbench/outline/OutlineTree.spec.ts`](penmate-frontend/package.json)
Expected: 组件缺失失败。

**Step 3: Write minimal implementation**
让大纲面板只负责发射事件，不再直接持有 API 调用。

**Step 4: Run test to verify it passes**
Run: [`npm run test:run -- src/components/workbench/outline/OutlineTree.spec.ts`](penmate-frontend/package.json)
Expected: spec 通过。

**Step 5: Commit**
Run: `git add penmate-frontend/src/components/workbench/outline penmate-frontend/src/composables/workbench penmate-frontend/src/views/Workbench/index.vue && git commit -m "refactor(frontend): split workbench outline tree"`

### Task 4: 抽离角色卡 / 世界卡 / 关系维护区

**Files:**
- Create: [`penmate-frontend/src/composables/workbench/useWorkbenchCards.ts`](penmate-frontend/src/composables/workbench/useWorkbenchCards.ts)
- Create: [`penmate-frontend/src/components/workbench/cards/CharacterCardList.vue`](penmate-frontend/src/components/workbench/cards/CharacterCardList.vue)
- Create: [`penmate-frontend/src/components/workbench/cards/CharacterCardItem.vue`](penmate-frontend/src/components/workbench/cards/CharacterCardItem.vue)
- Create: [`penmate-frontend/src/components/workbench/cards/WorldCardList.vue`](penmate-frontend/src/components/workbench/cards/WorldCardList.vue)
- Create: [`penmate-frontend/src/components/workbench/cards/WorldCardItem.vue`](penmate-frontend/src/components/workbench/cards/WorldCardItem.vue)
- Create: [`penmate-frontend/src/components/workbench/cards/CardRelationPanel.vue`](penmate-frontend/src/components/workbench/cards/CardRelationPanel.vue)
- Modify: [`penmate-frontend/src/views/Workbench/index.vue`](penmate-frontend/src/views/Workbench/index.vue)
- Test: [`penmate-frontend/src/components/workbench/cards/CharacterCardItem.spec.ts`](penmate-frontend/src/components/workbench/cards/CharacterCardItem.spec.ts)

**Step 1: Write the failing test**
覆盖展开/收起、编辑输入、保存 emit、删除 emit。

**Step 2: Run test to verify it fails**
Run: [`npm run test:run -- src/components/workbench/cards/CharacterCardItem.spec.ts`](penmate-frontend/package.json)
Expected: 失败。

**Step 3: Write minimal implementation**
按展示组件模式输出 props/emits。

**Step 4: Run test to verify it passes**
Run: [`npm run test:run -- src/components/workbench/cards/CharacterCardItem.spec.ts`](penmate-frontend/package.json)
Expected: 通过。

**Step 5: Commit**
Run: `git add penmate-frontend/src/components/workbench/cards penmate-frontend/src/composables/workbench penmate-frontend/src/views/Workbench/index.vue && git commit -m "refactor(frontend): split workbench card panels"`

### Task 5: 抽离编辑器逻辑与版本面板

**Files:**
- Create: [`penmate-frontend/src/composables/workbench/useWorkbenchEditor.ts`](penmate-frontend/src/composables/workbench/useWorkbenchEditor.ts)
- Create: [`penmate-frontend/src/composables/workbench/useWorkbenchVersions.ts`](penmate-frontend/src/composables/workbench/useWorkbenchVersions.ts)
- Create: [`penmate-frontend/src/components/workbench/editor/EditorToolbar.vue`](penmate-frontend/src/components/workbench/editor/EditorToolbar.vue)
- Create: [`penmate-frontend/src/components/workbench/editor/EditorTextarea.vue`](penmate-frontend/src/components/workbench/editor/EditorTextarea.vue)
- Create: [`penmate-frontend/src/components/workbench/editor/EditorStatusbar.vue`](penmate-frontend/src/components/workbench/editor/EditorStatusbar.vue)
- Create: [`penmate-frontend/src/components/workbench/editor/VersionPreviewPane.vue`](penmate-frontend/src/components/workbench/editor/VersionPreviewPane.vue)
- Modify: [`penmate-frontend/src/views/Workbench/index.vue`](penmate-frontend/src/views/Workbench/index.vue)
- Test: [`penmate-frontend/src/components/workbench/editor/EditorToolbar.spec.ts`](penmate-frontend/src/components/workbench/editor/EditorToolbar.spec.ts)

**Step 1: Write the failing test**
覆盖 toolbar 按钮 emit 与版本按钮禁用态。

**Step 2: Run test to verify it fails**
Run: [`npm run test:run -- src/components/workbench/editor/EditorToolbar.spec.ts`](penmate-frontend/package.json)
Expected: 失败。

**Step 3: Write minimal implementation**
确保组件只发命令，不直接写 API。

**Step 4: Run test to verify it passes**
Run: [`npm run test:run -- src/components/workbench/editor/EditorToolbar.spec.ts`](penmate-frontend/package.json)
Expected: 通过。

**Step 5: Commit**
Run: `git add penmate-frontend/src/components/workbench/editor penmate-frontend/src/composables/workbench penmate-frontend/src/views/Workbench/index.vue && git commit -m "refactor(frontend): split workbench editor and version panel"`

### Task 6: 抽离聊天、SSE 与审批逻辑

Use [test-driven-development] mode for this task.

**Files:**
- Create: [`penmate-frontend/src/composables/workbench/useWorkbenchChat.ts`](penmate-frontend/src/composables/workbench/useWorkbenchChat.ts)
- Create: [`penmate-frontend/src/composables/workbench/useWorkbenchApprovals.ts`](penmate-frontend/src/composables/workbench/useWorkbenchApprovals.ts)
- Create: [`penmate-frontend/src/components/workbench/chat/AgentSessionHeader.vue`](penmate-frontend/src/components/workbench/chat/AgentSessionHeader.vue)
- Create: [`penmate-frontend/src/components/workbench/chat/ConversationHistoryPanel.vue`](penmate-frontend/src/components/workbench/chat/ConversationHistoryPanel.vue)
- Create: [`penmate-frontend/src/components/workbench/chat/ChatMessageList.vue`](penmate-frontend/src/components/workbench/chat/ChatMessageList.vue)
- Create: [`penmate-frontend/src/components/workbench/chat/ChatMessageItem.vue`](penmate-frontend/src/components/workbench/chat/ChatMessageItem.vue)
- Create: [`penmate-frontend/src/components/workbench/chat/ChatComposer.vue`](penmate-frontend/src/components/workbench/chat/ChatComposer.vue)
- Modify: [`penmate-frontend/src/views/Workbench/index.vue`](penmate-frontend/src/views/Workbench/index.vue)
- Test: [`penmate-frontend/src/components/workbench/chat/ChatComposer.spec.ts`](penmate-frontend/src/components/workbench/chat/ChatComposer.spec.ts)

**Step 1: Write the failing test**
覆盖发送禁用条件、回车发送、模型未选提示展示。

**Step 2: Run test to verify it fails**
Run: [`npm run test:run -- src/components/workbench/chat/ChatComposer.spec.ts`](penmate-frontend/package.json)
Expected: 失败。

**Step 3: Write minimal implementation**
SSE 副作用留在 composable，UI 组件只接状态和回调。

**Step 4: Run test to verify it passes**
Run: [`npm run test:run -- src/components/workbench/chat/ChatComposer.spec.ts`](penmate-frontend/package.json)
Expected: 通过。

**Step 5: Commit**
Run: `git add penmate-frontend/src/components/workbench/chat penmate-frontend/src/composables/workbench penmate-frontend/src/views/Workbench/index.vue && git commit -m "refactor(frontend): split workbench chat flow"`

### Task 7: Workbench 页面壳层收口

**Files:**
- Create: [`penmate-frontend/src/components/workbench/WorkbenchHeader.vue`](penmate-frontend/src/components/workbench/WorkbenchHeader.vue)
- Create: [`penmate-frontend/src/components/workbench/WorkbenchLeftPanel.vue`](penmate-frontend/src/components/workbench/WorkbenchLeftPanel.vue)
- Create: [`penmate-frontend/src/components/workbench/WorkbenchEditorPanel.vue`](penmate-frontend/src/components/workbench/WorkbenchEditorPanel.vue)
- Create: [`penmate-frontend/src/components/workbench/WorkbenchRightPanel.vue`](penmate-frontend/src/components/workbench/WorkbenchRightPanel.vue)
- Modify: [`penmate-frontend/src/views/Workbench/index.vue`](penmate-frontend/src/views/Workbench/index.vue)
- Test: [`penmate-frontend/src/views/Workbench/index.vue`](penmate-frontend/src/views/Workbench/index.vue)

**Step 1: Write the failing test**
为 [`Workbench/index.vue`](penmate-frontend/src/views/Workbench/index.vue) 写浅渲染测试，验证页面已只组合壳层组件。

**Step 2: Run test to verify it fails**
Run: [`npm run test:run -- src/views/Workbench/index.spec.ts`](penmate-frontend/package.json)
Expected: 失败。

**Step 3: Write minimal implementation**
将页面收口为 orchestrator 容器。

**Step 4: Run test to verify it passes**
Run: [`npm run test:run -- src/views/Workbench/index.spec.ts`](penmate-frontend/package.json)
Expected: 通过。

**Step 5: Commit**
Run: `git add penmate-frontend/src/components/workbench penmate-frontend/src/views/Workbench/index.vue && git commit -m "refactor(frontend): reduce workbench page to container"`

### Task 8: 拆 StyleManager / PluginWorkshop / ModelSettings

**Files:**
- Modify: [`penmate-frontend/src/components/workbench/StyleManager.vue`](penmate-frontend/src/components/workbench/StyleManager.vue)
- Modify: [`penmate-frontend/src/components/workbench/PluginWorkshop.vue`](penmate-frontend/src/components/workbench/PluginWorkshop.vue)
- Modify: [`penmate-frontend/src/components/workbench/ModelSettings.vue`](penmate-frontend/src/components/workbench/ModelSettings.vue)
- Create: [`penmate-frontend/src/components/workbench/modals/StyleManagerDrawer.vue`](penmate-frontend/src/components/workbench/modals/StyleManagerDrawer.vue)
- Create: [`penmate-frontend/src/components/workbench/modals/PluginWorkshopModal.vue`](penmate-frontend/src/components/workbench/modals/PluginWorkshopModal.vue)
- Create: [`penmate-frontend/src/components/workbench/modals/ModelSettingsModal.vue`](penmate-frontend/src/components/workbench/modals/ModelSettingsModal.vue)
- Create: [`penmate-frontend/src/components/workbench/modals/StyleSelector.vue`](penmate-frontend/src/components/workbench/modals/StyleSelector.vue)
- Create: [`penmate-frontend/src/components/workbench/modals/StyleConfigForm.vue`](penmate-frontend/src/components/workbench/modals/StyleConfigForm.vue)
- Create: [`penmate-frontend/src/components/workbench/modals/PluginCatalogGrid.vue`](penmate-frontend/src/components/workbench/modals/PluginCatalogGrid.vue)
- Create: [`penmate-frontend/src/components/workbench/modals/PluginCard.vue`](penmate-frontend/src/components/workbench/modals/PluginCard.vue)
- Create: [`penmate-frontend/src/components/workbench/modals/OfficialModelGrid.vue`](penmate-frontend/src/components/workbench/modals/OfficialModelGrid.vue)
- Create: [`penmate-frontend/src/components/workbench/modals/UserModelGrid.vue`](penmate-frontend/src/components/workbench/modals/UserModelGrid.vue)
- Create: [`penmate-frontend/src/components/workbench/modals/ModelConfigForm.vue`](penmate-frontend/src/components/workbench/modals/ModelConfigForm.vue)

**Step 1: Write the failing test**
先测卡片 grid 与 form 的 props/emits。

**Step 2: Run test to verify it fails**
Run: [`npm run test:run -- src/components/workbench/modals`](penmate-frontend/package.json)
Expected: 缺失失败。

**Step 3: Write minimal implementation**
弹层容器保留 API 调度，子组件仅接收数据。

**Step 4: Run test to verify it passes**
Run: [`npm run test:run -- src/components/workbench/modals`](penmate-frontend/package.json)
Expected: 通过。

**Step 5: Commit**
Run: `git add penmate-frontend/src/components/workbench && git commit -m "refactor(frontend): split workbench settings modals"`

### Task 9: 拆 MyBooks 页面

**Files:**
- Create: [`penmate-frontend/src/composables/bookshelf/useBookshelf.ts`](penmate-frontend/src/composables/bookshelf/useBookshelf.ts)
- Create: [`penmate-frontend/src/components/bookshelf/BookStatsBar.vue`](penmate-frontend/src/components/bookshelf/BookStatsBar.vue)
- Create: [`penmate-frontend/src/components/bookshelf/BookActionBar.vue`](penmate-frontend/src/components/bookshelf/BookActionBar.vue)
- Create: [`penmate-frontend/src/components/bookshelf/BookCard.vue`](penmate-frontend/src/components/bookshelf/BookCard.vue)
- Create: [`penmate-frontend/src/components/bookshelf/BookEditorModal.vue`](penmate-frontend/src/components/bookshelf/BookEditorModal.vue)
- Create: [`penmate-frontend/src/components/bookshelf/DeleteBookDialog.vue`](penmate-frontend/src/components/bookshelf/DeleteBookDialog.vue)
- Modify: [`penmate-frontend/src/views/MyBooks/index.vue`](penmate-frontend/src/views/MyBooks/index.vue)

**Step 1: Write the failing test**
先测 [`BookCard.vue`](penmate-frontend/src/components/bookshelf/BookCard.vue) 的点击、编辑、删除 emit。

**Step 2: Run test to verify it fails**
Run: [`npm run test:run -- src/components/bookshelf/BookCard.spec.ts`](penmate-frontend/package.json)
Expected: 失败。

**Step 3: Write minimal implementation**
完成拆分。

**Step 4: Run test to verify it passes**
Run: [`npm run test:run -- src/components/bookshelf/BookCard.spec.ts`](penmate-frontend/package.json)
Expected: 通过。

**Step 5: Commit**
Run: `git add penmate-frontend/src/components/bookshelf penmate-frontend/src/composables/bookshelf penmate-frontend/src/views/MyBooks/index.vue && git commit -m "refactor(frontend): split bookshelf page"`

### Task 10: 拆 Login 页面

**Files:**
- Create: [`penmate-frontend/src/composables/auth/useLoginSubmit.ts`](penmate-frontend/src/composables/auth/useLoginSubmit.ts)
- Create: [`penmate-frontend/src/components/auth/AuthCardShell.vue`](penmate-frontend/src/components/auth/AuthCardShell.vue)
- Create: [`penmate-frontend/src/components/auth/AuthModeTabs.vue`](penmate-frontend/src/components/auth/AuthModeTabs.vue)
- Create: [`penmate-frontend/src/components/auth/LoginForm.vue`](penmate-frontend/src/components/auth/LoginForm.vue)
- Create: [`penmate-frontend/src/components/auth/RegisterForm.vue`](penmate-frontend/src/components/auth/RegisterForm.vue)
- Modify: [`penmate-frontend/src/views/Login/index.vue`](penmate-frontend/src/views/Login/index.vue)

**Step 1: Write the failing test**
覆盖登录按钮 loading/disabled 与提交事件。

**Step 2: Run test to verify it fails**
Run: [`npm run test:run -- src/components/auth/LoginForm.spec.ts`](penmate-frontend/package.json)
Expected: 失败。

**Step 3: Write minimal implementation**
将 API 提交与 session 写入迁移至 composable。

**Step 4: Run test to verify it passes**
Run: [`npm run test:run -- src/components/auth/LoginForm.spec.ts`](penmate-frontend/package.json)
Expected: 通过。

**Step 5: Commit**
Run: `git add penmate-frontend/src/components/auth penmate-frontend/src/composables/auth penmate-frontend/src/views/Login/index.vue && git commit -m "refactor(frontend): split login page"`

### Task 11: 拆 Profile 页面

**Files:**
- Create: [`penmate-frontend/src/composables/profile/useProfileSettings.ts`](penmate-frontend/src/composables/profile/useProfileSettings.ts)
- Create: [`penmate-frontend/src/components/profile/ProfileHeroCard.vue`](penmate-frontend/src/components/profile/ProfileHeroCard.vue)
- Create: [`penmate-frontend/src/components/profile/ProfileSecurityPanel.vue`](penmate-frontend/src/components/profile/ProfileSecurityPanel.vue)
- Create: [`penmate-frontend/src/components/profile/ProfilePreferencePanel.vue`](penmate-frontend/src/components/profile/ProfilePreferencePanel.vue)
- Create: [`penmate-frontend/src/components/profile/ProfileApiKeyPanel.vue`](penmate-frontend/src/components/profile/ProfileApiKeyPanel.vue)
- Create: [`penmate-frontend/src/components/profile/ProfileDangerZone.vue`](penmate-frontend/src/components/profile/ProfileDangerZone.vue)
- Modify: [`penmate-frontend/src/views/Profile/index.vue`](penmate-frontend/src/views/Profile/index.vue)

**Step 1: Write the failing test**
覆盖资料卡编辑 emit 与安全面板展开逻辑。

**Step 2: Run test to verify it fails**
Run: [`npm run test:run -- src/components/profile/ProfileHeroCard.spec.ts`](penmate-frontend/package.json)
Expected: 失败。

**Step 3: Write minimal implementation**
完成拆分。

**Step 4: Run test to verify it passes**
Run: [`npm run test:run -- src/components/profile/ProfileHeroCard.spec.ts`](penmate-frontend/package.json)
Expected: 通过。

**Step 5: Commit**
Run: `git add penmate-frontend/src/components/profile penmate-frontend/src/composables/profile penmate-frontend/src/views/Profile/index.vue && git commit -m "refactor(frontend): split profile page"`

### Task 12: 拆 Home 页面并收敛视觉复用

**Files:**
- Create: [`penmate-frontend/src/composables/home/useHomeEffects.ts`](penmate-frontend/src/composables/home/useHomeEffects.ts)
- Create: [`penmate-frontend/src/components/home/HomeHero.vue`](penmate-frontend/src/components/home/HomeHero.vue)
- Create: [`penmate-frontend/src/components/home/HomeFeatures.vue`](penmate-frontend/src/components/home/HomeFeatures.vue)
- Create: [`penmate-frontend/src/components/home/HomeWorkflow.vue`](penmate-frontend/src/components/home/HomeWorkflow.vue)
- Create: [`penmate-frontend/src/components/home/HomePreview.vue`](penmate-frontend/src/components/home/HomePreview.vue)
- Create: [`penmate-frontend/src/components/home/HomeCta.vue`](penmate-frontend/src/components/home/HomeCta.vue)
- Create: [`penmate-frontend/src/components/home/HomeFooter.vue`](penmate-frontend/src/components/home/HomeFooter.vue)
- Modify: [`penmate-frontend/src/views/Home/index.vue`](penmate-frontend/src/views/Home/index.vue)

**Step 1: Write the failing test**
先为 [`HomeHero.vue`](penmate-frontend/src/components/home/HomeHero.vue) 写渲染与按钮点击测试。

**Step 2: Run test to verify it fails**
Run: [`npm run test:run -- src/components/home/HomeHero.spec.ts`](penmate-frontend/package.json)
Expected: 失败。

**Step 3: Write minimal implementation**
保留营销页文案与视觉一致。

**Step 4: Run test to verify it passes**
Run: [`npm run test:run -- src/components/home/HomeHero.spec.ts`](penmate-frontend/package.json)
Expected: 通过。

**Step 5: Commit**
Run: `git add penmate-frontend/src/components/home penmate-frontend/src/composables/home penmate-frontend/src/views/Home/index.vue && git commit -m "refactor(frontend): split home landing page"`

---

## 8. 验证策略

### 每个任务完成后必须执行

Run: [`npm run test:run`](penmate-frontend/package.json)
Expected: 全量已有单测 + 新增单测通过。

Run: [`npm run build`](penmate-frontend/package.json)
Expected: Vite 构建成功，无 TypeScript 编译错误。

### 每个阶段结束后执行人工验证清单

#### Workbench 阶段
- 能进入 [`/workbench`](penmate-frontend/src/router/index.ts:25)
- 能切换左侧 tab
- 能切换章节
- 能保存本地草稿
- 能查看版本、恢复版本、发布章节
- 能发送 AI 指令
- SSE 正常时能流式显示
- SSE 失败时轮询兜底正常
- 审批卡 approve/reject 正常
- 模型切换后右侧状态正常更新

#### Bookshelf / Login / Profile / Home 阶段
- 页面导航不变
- 所有 CTA 点击路径不变
- 弹窗开启/关闭正常
- 空态/加载/错误态保留

---

## 9. 完成定义

重构完成后应满足：

- [`Workbench/index.vue`](penmate-frontend/src/views/Workbench/index.vue) 控制在 300–450 行内
- [`Home/index.vue`](penmate-frontend/src/views/Home/index.vue) 控制在 200–300 行内
- [`MyBooks/index.vue`](penmate-frontend/src/views/MyBooks/index.vue)、[`Login/index.vue`](penmate-frontend/src/views/Login/index.vue)、[`Profile/index.vue`](penmate-frontend/src/views/Profile/index.vue) 都降到 200–280 行级别
- 不再有任何单文件承担 4 个以上业务子域
- 核心异步逻辑均有单测
- 页面组件主要承担组合职责

---

## 10. 推荐先做的最小落地批次

如果要尽快开始、且降低回归风险，建议首批只执行以下四个任务：

1. Task 1 — Workbench 纯逻辑测试安全网
2. Task 2 — 上下文与草稿抽离
3. Task 3 — 大纲树抽离
4. Task 5 — 编辑器与版本面板抽离

这四步能最大幅度降低 [`Workbench/index.vue`](penmate-frontend/src/views/Workbench/index.vue) 的复杂度，同时不会过早触碰最脆弱的 SSE 聊天时序。

---

## 11. 执行选项

Plan complete. Execute now?

1. Execute in this session ([executing-plans])
2. Execute later (user will run /execute-plan)
3. Manual implementation (just use plan as guide)
