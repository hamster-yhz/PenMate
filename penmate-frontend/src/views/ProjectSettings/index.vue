<template>
  <div class="app-page project-settings-page">
    <AppTopbar :context-title="currentTitle">
      <template #actions>
        <button class="back-workbench" type="button" @click="goBackToWorkbench"><ArrowLeftOutlined />返回工作台</button>
      </template>
    </AppTopbar>

    <main class="settings-layout">
      <ProjectSettingsNav
        :model-value="activeSection"
        :project-title="project.title"
        @update:model-value="requestSectionChange"
      />

      <section class="settings-content">
        <div v-if="loading" class="settings-loading" aria-label="正在加载作品设置">
          <span class="skeleton-line title"></span>
          <span v-for="item in 5" :key="item" class="skeleton-line"></span>
        </div>

        <div v-else-if="loadError" class="error-panel" role="alert">
          <WarningOutlined />
          <p>{{ loadError }}</p>
          <button type="button" @click="load"><ReloadOutlined />重试</button>
        </div>

        <template v-else>
          <ProjectGeneralSection
            v-if="activeSection === 'general'"
            :project="project"
            :genres="genres"
            :saving="savingSection === 'general'"
            :busy="savingSection !== null || rebuilding"
            :error="saveError"
            :success="saveSuccess"
            :cover-status="coverStatus"
            :cover-error="coverError"
            :cover-busy="coverBusy"
            :cover-crop="coverCrop"
            @save="saveGeneral"
            @change-cover="changeCover"
            @retry-cover="retryCover"
            @remove-cover="confirmRemoveCover"
          />
          <ProjectAiSection
            v-else-if="activeSection === 'ai'"
            :ai="ai"
            :chat-models="chatModels"
            :embedding-models="embeddingModels"
            :retrieval-available="retrievalAvailable"
            :saving="savingSection === 'ai'"
            :busy="savingSection !== null"
            :error="saveError"
            :success="saveSuccess"
            @save="saveAi"
          />
          <ProjectIndexSection
            v-else-if="activeSection === 'index'"
            :ai="ai"
            :index="index"
            :embedding-models="embeddingModels"
            :can-rebuild="canRebuildIndex"
            :rebuilding="rebuilding"
            :cancelling="cancellingRebuild"
            :error="saveError"
            :success="saveSuccess"
            @rebuild="rebuildIndex"
            @stop="stopRebuild"
          />
          <ProjectDataSection
            v-else-if="activeSection === 'data'"
            :exporting-format="exportingFormat"
            :error="saveError"
            :success="saveSuccess"
            @export="exportProject"
            @print="printProject"
          />
          <ProjectDangerSection v-else :error="saveError" @trash="confirmTrash" />
        </template>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ArrowLeftOutlined, ReloadOutlined, WarningOutlined } from '@ant-design/icons-vue'
import AppTopbar from '@/components/app/AppTopbar.vue'
import ProjectAiSection from '@/components/project-settings/ProjectAiSection.vue'
import ProjectDangerSection from '@/components/project-settings/ProjectDangerSection.vue'
import ProjectDataSection from '@/components/project-settings/ProjectDataSection.vue'
import ProjectGeneralSection from '@/components/project-settings/ProjectGeneralSection.vue'
import ProjectIndexSection from '@/components/project-settings/ProjectIndexSection.vue'
import ProjectSettingsNav from '@/components/project-settings/ProjectSettingsNav.vue'
import { useProjectSettingsPage } from '@/features/project-settings/useProjectSettingsPage'

const {
  activeSection,
  loading,
  loadError,
  savingSection,
  saveError,
  saveSuccess,
  rebuilding,
  cancellingRebuild,
  exportingFormat,
  coverStatus,
  coverError,
  coverCrop,
  coverBusy,
  project,
  ai,
  index,
  chatModels,
  embeddingModels,
  canRebuildIndex,
  retrievalAvailable,
  currentTitle,
  genres,
  load,
  saveGeneral,
  saveAi,
  rebuildIndex,
  stopRebuild,
  exportProject,
  printProject,
  changeCover,
  retryCover,
  goBackToWorkbench,
  confirmTrash,
  confirmRemoveCover,
  requestSectionChange,
} = useProjectSettingsPage()
</script>

<style src="./project-settings.css"></style>
