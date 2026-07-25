import { reactive, ref } from 'vue'
import { profileApi } from '@/api/modules/profile.api'
import type { AuthorProfile } from '@/entities/author/model'
import { getErrorMessage } from '@/utils/errors'

const defaults = (): AuthorProfile => ({
  defaultLanguage: 'zh-CN', collaborationMode: 'COLLABORATIVE', defaultPov: 'PROJECT_DEFAULT',
  defaultTense: 'PROJECT_DEFAULT', descriptionDensity: 'MEDIUM', dialoguePreference: '',
  bannedExpressions: '', longTermMemory: '',
})

export const useAuthorProfileSettings = () => {
  const authorProfile = reactive<AuthorProfile>(defaults())
  const authorProfileLoading = ref(false)
  const authorProfileSaving = ref(false)
  const authorProfileError = ref('')
  const authorProfileSaved = ref(false)
  const authorProfileLoaded = ref(false)
  const apply = (value: AuthorProfile) => Object.assign(authorProfile, defaults(), value)

  const loadAuthorProfile = async () => {
    authorProfileLoading.value = true
    authorProfileError.value = ''
    try { apply(await profileApi.getAuthorProfile()); authorProfileLoaded.value = true }
    catch (reason: unknown) { authorProfileError.value = getErrorMessage(reason, '作者偏好加载失败') }
    finally { authorProfileLoading.value = false }
  }

  const saveAuthorProfile = async (value: AuthorProfile) => {
    authorProfileSaving.value = true
    authorProfileError.value = ''
    authorProfileSaved.value = false
    try { apply(await profileApi.saveAuthorProfile(value)); authorProfileSaved.value = true }
    catch (reason: unknown) { authorProfileError.value = getErrorMessage(reason, '作者偏好保存失败') }
    finally { authorProfileSaving.value = false }
  }

  return {
    authorProfile, authorProfileLoading, authorProfileSaving, authorProfileError, authorProfileSaved, authorProfileLoaded,
    loadAuthorProfile, saveAuthorProfile,
  }
}
