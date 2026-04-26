import { describe, expect, it } from 'vitest'

import {
  hasObjectKeyInStorageUrl,
  normalizeObjectStorageUrl,
  resolveDirectUploadTarget,
} from '../workbenchStorage'

describe('workbenchStorage', () => {
  it('should_return_empty_string_when_storage_url_is_blank', () => {
    expect(normalizeObjectStorageUrl('')).toBe('')
    expect(normalizeObjectStorageUrl('   ')).toBe('')
  })

  it('should_keep_absolute_storage_url_unchanged', () => {
    expect(normalizeObjectStorageUrl('https://oss.example.com/read/object-key')).toBe(
      'https://oss.example.com/read/object-key',
    )
  })

  it('should_prefix_protocol_relative_storage_url_with_window_protocol', () => {
    expect(normalizeObjectStorageUrl('//cdn.penmate.test/read/object-key')).toBe(
      `${window.location.protocol}//cdn.penmate.test/read/object-key`,
    )
  })

  it('should_keep_root_relative_storage_url_unchanged', () => {
    expect(normalizeObjectStorageUrl('/api/storage/read/object-key')).toBe('/api/storage/read/object-key')
  })

  it('should_prefix_bare_storage_host_with_default_protocol', () => {
    expect(normalizeObjectStorageUrl('localhost:9000/read/object-key')).toBe(
      'https://localhost:9000/read/object-key',
    )
  })

  it('should_detect_missing_object_key_after_read_marker', () => {
    expect(hasObjectKeyInStorageUrl('https://oss.example.com/read/', '/read/')).toBe(false)
    expect(hasObjectKeyInStorageUrl('https://oss.example.com/read/object-key', '/read/')).toBe(true)
  })

  it('should_detect_missing_object_key_after_upload_marker', () => {
    expect(hasObjectKeyInStorageUrl('https://oss.example.com/upload/', '/upload/')).toBe(false)
    expect(hasObjectKeyInStorageUrl('https://oss.example.com/upload/object-key', '/upload/')).toBe(true)
  })

  it('should_treat_urls_without_target_marker_as_usable', () => {
    expect(hasObjectKeyInStorageUrl('https://oss.example.com/other/path', '/read/')).toBe(true)
  })

  it('should_throw_when_upload_url_has_no_object_key_after_upload_marker', () => {
    expect(() => resolveDirectUploadTarget({
      uploadUrl: 'https://oss.example.com/upload/',
      objectKey: 'chapters/1001/content.txt',
      storageProvider: 's3',
    })).toThrow('上传地址响应缺少 uploadUrl 对象键')
  })

  it('should_return_normalized_direct_upload_target_when_upload_response_is_usable', () => {
    expect(resolveDirectUploadTarget({
      uploadUrl: 'localhost:9000/upload/chapters/1001/content.txt',
      objectKey: 'chapters/1001/content.txt',
    })).toEqual({
      uploadUrl: 'https://localhost:9000/upload/chapters/1001/content.txt',
      objectKey: 'chapters/1001/content.txt',
      storageProvider: 's3',
    })
  })
})
