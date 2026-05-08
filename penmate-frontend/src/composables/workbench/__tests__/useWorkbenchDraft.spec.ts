import { afterEach, describe, expect, it, vi } from 'vitest'

import { createChapterLoadGuard, useWorkbenchDraft } from '../useWorkbenchDraft'
import {
  getDraftStorageKey,
  readChapterDraftLocal,
  saveChapterDraftLocal,
} from '../workbenchDraft'

describe('workbenchDraft', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    window.localStorage.clear()
  })

  it('should_build_draft_storage_key_from_project_id_and_chapter_id', () => {
    expect(getDraftStorageKey('12', '34')).toBe('penmate.chapterDraft.12.34')
    expect(getDraftStorageKey('7', 'chapter-A')).toBe('penmate.chapterDraft.7.chapter-A')
  })

  it('should_save_and_read_chapter_draft_from_local_storage', () => {
    saveChapterDraftLocal('101', '202', '第一章草稿内容')

    expect(readChapterDraftLocal('101', '202')).toBe('第一章草稿内容')
  })

  it('should_return_empty_string_when_local_storage_has_no_draft', () => {
    expect(readChapterDraftLocal('404', '505')).toBe('')
  })

  it('should_swallow_local_storage_write_errors', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('quota exceeded')
    })

    expect(() => saveChapterDraftLocal('1', '2', 'draft')).not.toThrow()
  })

  it('should_return_empty_string_when_local_storage_read_throws', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('storage disabled')
    })

    expect(readChapterDraftLocal('1', '2')).toBe('')
  })

  it('should_mark_previous_chapter_load_as_stale_after_switching_to_next_chapter', () => {
    const guard = createChapterLoadGuard()

    const chapterARequest = guard.begin('101')
    const chapterBRequest = guard.begin('102')

    expect(guard.isCurrent('101', chapterARequest)).toBe(false)
    expect(guard.isCurrent('102', chapterBRequest)).toBe(true)
  })

  it('should_prefer_existing_local_draft_over_remote_content', () => {
    saveChapterDraftLocal('9', '99', '本地未保存草稿')

    const { resolveChapterContent } = useWorkbenchDraft()

    expect(resolveChapterContent('9', '99', '远端已发布正文')).toBe('本地未保存草稿')
    expect(readChapterDraftLocal('9', '99')).toBe('本地未保存草稿')
  })

  it('should_preserve_explicit_empty_local_draft_over_remote_content', () => {
    saveChapterDraftLocal('10', '100', '')

    const { resolveChapterContent } = useWorkbenchDraft()

    expect(resolveChapterContent('10', '100', '远端已发布正文')).toBe('')
    expect(readChapterDraftLocal('10', '100')).toBe('')
  })

  it('should_not_persist_remote_content_as_local_draft_when_no_draft_exists', () => {
    const { resolveChapterContent } = useWorkbenchDraft()

    expect(resolveChapterContent('11', '101', '远端已发布正文')).toBe('远端已发布正文')
    expect(window.localStorage.getItem(getDraftStorageKey('11', '101'))).toBeNull()
  })

  it('should_return_null_when_stored_draft_does_not_exist', () => {
    const { resolveStoredDraft } = useWorkbenchDraft()

    expect(resolveStoredDraft('12', '101')).toBeNull()
  })

  it('should_return_explicit_empty_string_when_stored_draft_exists_but_is_empty', () => {
    saveChapterDraftLocal('12', '102', '')

    const { resolveStoredDraft } = useWorkbenchDraft()

    expect(resolveStoredDraft('12', '102')).toBe('')
  })

  it('should_preserve_defined_empty_chapter_content_when_resolving_editor_seed_content', () => {
    const { resolveEditorSeedContent } = useWorkbenchDraft()

    expect(resolveEditorSeedContent('', '本地草稿')).toBe('')
  })

  it('should_fallback_to_stored_draft_or_empty_string_when_editor_seed_content_is_undefined', () => {
    const { resolveEditorSeedContent } = useWorkbenchDraft()

    expect(resolveEditorSeedContent(undefined, '本地草稿')).toBe('本地草稿')
    expect(resolveEditorSeedContent(undefined, '')).toBe('')
    expect(resolveEditorSeedContent(undefined, null)).toBe('')
  })

  it('should_clear_local_draft_override_after_restore_flow', () => {
    saveChapterDraftLocal('12', '102', '恢复前的本地草稿')

    const { resolveChapterContent } = useWorkbenchDraft()

    expect(resolveChapterContent('12', '102', '恢复后的远端正文', { preferRemote: true })).toBe('恢复后的远端正文')
    expect(window.localStorage.getItem(getDraftStorageKey('12', '102'))).toBeNull()
  })
})
