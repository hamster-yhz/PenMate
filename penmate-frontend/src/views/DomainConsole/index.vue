<template>
  <div class="domain-console-page">
    <header class="dc-header">
      <div class="left">
        <button class="btn" @click="router.push('/mybooks')">← 返回书架</button>
        <h2>三域接口控台（Novel / RBAC / Style）</h2>
      </div>
      <div class="right">
        <span>projectId</span>
        <input v-model.number="projectId" type="number" class="ipt" />
        <span>operatorId</span>
        <input v-model.number="operatorId" type="number" class="ipt" />
      </div>
    </header>

    <main class="dc-main">
      <section class="panel io-panel">
        <h3>请求参数</h3>
        <div class="form-grid">
          <label>volumeId <input v-model.number="volumeId" type="number" class="ipt" /></label>
          <label>chapterId <input v-model.number="chapterId" type="number" class="ipt" /></label>
          <label>versionNo <input v-model.number="versionNo" type="number" class="ipt" /></label>
          <label>nodeId <input v-model.number="nodeId" type="number" class="ipt" /></label>
          <label>cardId <input v-model.number="cardId" type="number" class="ipt" /></label>
          <label>relationId <input v-model.number="relationId" type="number" class="ipt" /></label>
          <label>styleId <input v-model.number="styleId" type="number" class="ipt" /></label>
          <label>userId <input v-model.number="userId" type="number" class="ipt" /></label>
          <label>roleId <input v-model.number="roleId" type="number" class="ipt" /></label>
          <label>permissionId <input v-model.number="permissionId" type="number" class="ipt" /></label>
        </div>
      </section>

      <section class="panel">
        <h3>Novel 域</h3>
        <div class="grid">
          <button class="btn" @click="run(() => novelApi.listProjects())">listProjects</button>
          <button class="btn" @click="run(() => novelApi.createProject(parseJson(novelProjectPayload)))">createProject</button>
          <button class="btn" @click="run(() => novelApi.getProject(projectId))">getProject</button>
          <button class="btn" @click="run(() => novelApi.updateProject(projectId, parseJson(novelProjectPayload)))">updateProject</button>
          <button class="btn danger" @click="run(() => novelApi.deleteProject(projectId, operatorId))">deleteProject</button>

          <button class="btn" @click="run(() => novelApi.listVolumes(projectId))">listVolumes</button>
          <button class="btn" @click="run(() => novelApi.createVolume(projectId, operatorId, parseJson(volumePayload)))">createVolume</button>
          <button class="btn" @click="run(() => novelApi.updateVolume(projectId, volumeId, operatorId, parseJson(volumePayload)))">updateVolume</button>
          <button class="btn danger" @click="run(() => novelApi.deleteVolume(projectId, volumeId, operatorId))">deleteVolume</button>

          <button class="btn" @click="run(() => novelApi.listChapters(projectId))">listChapters</button>
          <button class="btn" @click="run(() => novelApi.createChapter(projectId, operatorId, parseJson(chapterPayload)))">createChapter</button>
          <button class="btn" @click="run(() => novelApi.getChapter(projectId, chapterId))">getChapter</button>
          <button class="btn" @click="run(() => novelApi.updateChapter(projectId, chapterId, operatorId, parseJson(chapterPayload)))">updateChapter</button>
          <button class="btn danger" @click="run(() => novelApi.deleteChapter(projectId, chapterId, operatorId))">deleteChapter</button>
          <button class="btn" @click="run(() => novelApi.publishChapter(projectId, chapterId, operatorId))">publishChapter</button>

          <button class="btn" @click="run(() => novelApi.listChapterVersions(projectId, chapterId))">listChapterVersions</button>
          <button class="btn" @click="run(() => novelApi.createChapterVersion(projectId, chapterId, parseJson(versionPayload)))">createChapterVersion</button>
          <button class="btn" @click="run(() => novelApi.getChapterVersion(projectId, chapterId, versionNo))">getChapterVersion</button>
          <button class="btn" @click="run(() => novelApi.restoreChapterVersion(projectId, chapterId, versionNo, operatorId))">restoreChapterVersion</button>
          <button class="btn" @click="run(() => novelApi.getChapterVersionSnapshotUrl(projectId, chapterId, versionNo))">getVersionSnapshotUrl</button>
          <button class="btn" @click="run(() => novelApi.getChapterContentUrl(projectId, chapterId))">getContentUrl</button>
          <button class="btn" @click="run(() => novelApi.getChapterContentUploadUrl(projectId, chapterId))">getContentUploadUrl</button>
          <button class="btn" @click="run(() => novelApi.commitChapterContent(projectId, chapterId, operatorId, parseJson(contentCommitPayload)))">commitContent</button>

          <button class="btn" @click="run(() => novelApi.listOutlineTree(projectId))">listOutlineTree</button>
          <button class="btn" @click="run(() => novelApi.createOutlineNode(projectId, operatorId, parseJson(outlinePayload)))">createOutlineNode</button>
          <button class="btn" @click="run(() => novelApi.updateOutlineNode(projectId, nodeId, operatorId, parseJson(outlinePayload)))">updateOutlineNode</button>
          <button class="btn" @click="run(() => novelApi.moveOutlineNode(projectId, nodeId, operatorId, parseJson(outlineMovePayload)))">moveOutlineNode</button>
          <button class="btn danger" @click="run(() => novelApi.deleteOutlineNode(projectId, nodeId, operatorId))">deleteOutlineNode</button>

          <button class="btn" @click="run(() => novelApi.listCards(projectId))">listCards</button>
          <button class="btn" @click="run(() => novelApi.createCard(projectId, operatorId, parseJson(cardPayload)))">createCard</button>
          <button class="btn" @click="run(() => novelApi.getCard(projectId, cardId))">getCard</button>
          <button class="btn" @click="run(() => novelApi.updateCard(projectId, cardId, operatorId, parseJson(cardPayload)))">updateCard</button>
          <button class="btn danger" @click="run(() => novelApi.deleteCard(projectId, cardId, operatorId))">deleteCard</button>
          <button class="btn" @click="run(() => novelApi.listCardRelations(projectId))">listCardRelations</button>
          <button class="btn" @click="run(() => novelApi.createCardRelation(projectId, operatorId, parseJson(cardRelationPayload)))">createCardRelation</button>
          <button class="btn danger" @click="run(() => novelApi.deleteCardRelation(projectId, relationId, operatorId))">deleteCardRelation</button>
        </div>
      </section>

      <section class="panel">
        <h3>RBAC 域</h3>
        <div class="grid">
          <button class="btn" @click="run(() => rbacApi.listUsers())">listUsers</button>
          <button class="btn" @click="run(() => rbacApi.getUser(userId))">getUser</button>
          <button class="btn" @click="run(() => rbacApi.createUser(parseJson(rbacUserPayload)))">createUser</button>
          <button class="btn" @click="run(() => rbacApi.updateUser(userId, parseJson(rbacUserPatchPayload)))">updateUser</button>
          <button class="btn danger" @click="run(() => rbacApi.deleteUser(userId))">deleteUser</button>

          <button class="btn" @click="run(() => rbacApi.listRoles())">listRoles</button>
          <button class="btn" @click="run(() => rbacApi.createRole(parseJson(rbacRolePayload)))">createRole</button>
          <button class="btn" @click="run(() => rbacApi.updateRole(roleId, parseJson(rbacRolePatchPayload)))">updateRole</button>
          <button class="btn danger" @click="run(() => rbacApi.deleteRole(roleId))">deleteRole</button>

          <button class="btn" @click="run(() => rbacApi.listPermissions())">listPermissions</button>
          <button class="btn" @click="run(() => rbacApi.assignUserRole(userId, roleId))">assignUserRole</button>
          <button class="btn danger" @click="run(() => rbacApi.removeUserRole(userId, roleId))">removeUserRole</button>
          <button class="btn" @click="run(() => rbacApi.assignRolePermission(roleId, permissionId))">assignRolePermission</button>
          <button class="btn danger" @click="run(() => rbacApi.removeRolePermission(roleId, permissionId))">removeRolePermission</button>
          <button class="btn" @click="run(() => rbacApi.listMenus())">listMenus</button>
          <button class="btn" @click="run(() => rbacApi.listProfileMenus(userId))">listProfileMenus</button>
        </div>
      </section>

      <section class="panel">
        <h3>Style 域</h3>
        <div class="grid">
          <button class="btn" @click="run(() => styleApi.listStyles(projectId))">listStyles</button>
          <button class="btn" @click="run(() => styleApi.getStyle(projectId, styleId))">getStyle</button>
          <button class="btn" @click="run(() => styleApi.createStyle(projectId, operatorId, parseJson(stylePayload)))">createStyle</button>
          <button class="btn" @click="run(() => styleApi.updateStyle(projectId, styleId, operatorId, parseJson(stylePayload)))">updateStyle</button>
          <button class="btn danger" @click="run(() => styleApi.deleteStyle(projectId, styleId, operatorId))">deleteStyle</button>
          <button class="btn" @click="run(() => styleApi.switchStyle(projectId, operatorId, parseJson(styleSwitchPayload)))">switchStyle</button>
          <button class="btn" @click="run(() => styleApi.analyzeSample(projectId, operatorId, parseJson(styleAnalyzePayload)))">analyzeSample</button>
        </div>
      </section>

      <section class="panel io-panel">
        <h3>JSON 载荷编辑区</h3>
        <div class="payload-grid">
          <label>novelProjectPayload <textarea v-model="novelProjectPayload" class="txt" rows="3" /></label>
          <label>volumePayload <textarea v-model="volumePayload" class="txt" rows="3" /></label>
          <label>chapterPayload <textarea v-model="chapterPayload" class="txt" rows="3" /></label>
          <label>versionPayload <textarea v-model="versionPayload" class="txt" rows="3" /></label>
          <label>contentCommitPayload <textarea v-model="contentCommitPayload" class="txt" rows="3" /></label>
          <label>outlinePayload <textarea v-model="outlinePayload" class="txt" rows="3" /></label>
          <label>outlineMovePayload <textarea v-model="outlineMovePayload" class="txt" rows="3" /></label>
          <label>cardPayload <textarea v-model="cardPayload" class="txt" rows="3" /></label>
          <label>cardRelationPayload <textarea v-model="cardRelationPayload" class="txt" rows="3" /></label>
          <label>rbacUserPayload <textarea v-model="rbacUserPayload" class="txt" rows="3" /></label>
          <label>rbacUserPatchPayload <textarea v-model="rbacUserPatchPayload" class="txt" rows="3" /></label>
          <label>rbacRolePayload <textarea v-model="rbacRolePayload" class="txt" rows="3" /></label>
          <label>rbacRolePatchPayload <textarea v-model="rbacRolePatchPayload" class="txt" rows="3" /></label>
          <label>stylePayload <textarea v-model="stylePayload" class="txt" rows="3" /></label>
          <label>styleSwitchPayload <textarea v-model="styleSwitchPayload" class="txt" rows="3" /></label>
          <label>styleAnalyzePayload <textarea v-model="styleAnalyzePayload" class="txt" rows="3" /></label>
        </div>
      </section>

      <section class="panel io-panel">
        <h3>调用结果</h3>
        <pre class="json-box">{{ output }}</pre>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { getSession } from '@/stores/session'
import { novelApi } from '@/api/modules/novel.api'
import { rbacApi } from '@/api/modules/rbac.api'
import { styleApi } from '@/api/modules/style.api'

const router = useRouter()
const session = getSession()

const projectId = ref(1)
const operatorId = ref(Number(session.userId || 1))

const volumeId = ref(1)
const chapterId = ref(1)
const versionNo = ref(1)
const nodeId = ref(1)
const cardId = ref(1)
const relationId = ref(1)
const styleId = ref(1)
const userId = ref(1)
const roleId = ref(1)
const permissionId = ref(1)

const novelProjectPayload = ref('{"ownerUserId":1,"title":"新小说","summary":"简介","status":1}')
const volumePayload = ref('{"title":"第一卷","sortOrder":1,"description":"卷简介"}')
const chapterPayload = ref('{"volumeId":1,"outlineNodeId":null,"title":"第一章","chapterNo":1,"status":1,"wordCount":0,"excerpt":""}')
const versionPayload = ref('{"changeType":"MANUAL_SAVE","changeReason":"保存","createdBy":1}')
const contentCommitPayload = ref('{"objectKey":"demo/ch1.txt","etag":"","size":128,"checksum":"","storageProvider":"oss"}')
const outlinePayload = ref('{"parentId":null,"title":"第一卷","nodeType":"VOLUME","sortOrder":1,"content":""}')
const outlineMovePayload = ref('{"parentId":null,"sortOrder":1}')
const cardPayload = ref('{"cardType":"CHARACTER","name":"主角","summary":"简介","detailJson":"{}"}')
const cardRelationPayload = ref('{"fromCardId":1,"toCardId":2,"relationType":"ALLY","description":""}')

const rbacUserPayload = ref('{"email":"demo@penmate.com","displayName":"Demo","status":1,"authMethod":"PASSWORD"}')
const rbacUserPatchPayload = ref('{"displayName":"Demo2","status":1}')
const rbacRolePayload = ref('{"name":"编辑","code":"EDITOR","description":"编辑角色","isSystem":false}')
const rbacRolePatchPayload = ref('{"name":"编辑(改)","description":"更新说明"}')

const stylePayload = ref('{"name":"古风默认","isDefault":false,"pace":"适中","tone":"古风文言化","narrativeFocus":"心理多","promptTemplate":"","sampleText":""}')
const styleSwitchPayload = ref('{"toStyleId":1,"warningConfirmed":true,"reason":"控制台切换"}')
const styleAnalyzePayload = ref('{"sampleText":"这是用于分析文风的示例文本，字数需要足够，至少五十字以上以便后端进行特征提取。"}')

const output = ref('等待调用...')

const parseJson = (text: string) => {
  try {
    return JSON.parse(text)
  } catch {
    throw new Error('JSON 解析失败，请检查输入')
  }
}

const run = async (fn: () => Promise<unknown>) => {
  try {
    const resp = await fn()
    output.value = JSON.stringify(resp, null, 2)
    message.success('调用成功')
  } catch (error: any) {
    output.value = JSON.stringify({ error: String(error?.message || error) }, null, 2)
    message.warning(error?.message || '调用失败')
  }
}
</script>

<style scoped lang="less">
.domain-console-page { min-height: 100vh; background: #0b1120; color: #e5e7eb; }
.dc-header { display: flex; justify-content: space-between; gap: 12px; padding: 12px 16px; border-bottom: 1px solid #2b3446; position: sticky; top: 0; background: #0f172a; z-index: 10; }
.dc-header .left { display: flex; align-items: center; gap: 12px; }
.dc-header .right { display: flex; align-items: center; gap: 8px; }
.ipt { width: 84px; background: #111827; color: #e5e7eb; border: 1px solid #334155; border-radius: 4px; padding: 4px 8px; }
.dc-main { padding: 12px; display: grid; gap: 12px; }
.panel { border: 1px solid #334155; border-radius: 8px; padding: 12px; background: rgba(15, 23, 42, 0.8); }
.panel h3 { margin: 0 0 8px; color: #f2d58b; }
.grid { display: flex; flex-wrap: wrap; gap: 8px; }
.form-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(170px, 1fr)); gap: 8px 12px; }
.form-grid label { display: flex; align-items: center; justify-content: space-between; gap: 8px; color: #cbd5e1; }
.payload-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 10px; }
.payload-grid label { display: grid; gap: 6px; color: #cbd5e1; font-size: 12px; }
.txt { width: 100%; background: #111827; color: #e5e7eb; border: 1px solid #334155; border-radius: 6px; padding: 8px; font-family: Consolas, monospace; }
.btn { background: #1f2937; color: #e5e7eb; border: 1px solid #475569; border-radius: 6px; padding: 6px 10px; cursor: pointer; }
.btn:hover { border-color: #f2d58b; color: #f2d58b; }
.btn.danger { border-color: #7f1d1d; color: #fecaca; }
.json-box { max-height: 360px; overflow: auto; background: #020617; border: 1px solid #1e293b; padding: 10px; border-radius: 6px; }
</style>

