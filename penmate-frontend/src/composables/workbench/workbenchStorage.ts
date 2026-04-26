export const normalizeObjectStorageUrl = (rawUrl: string) => {
  const url = String(rawUrl || '').trim()
  if (!url) return ''
  if (url.startsWith('//')) return `${window.location.protocol}${url}`
  if (url.startsWith('/')) return url
  if (/^(localhost|127\.0\.0\.1|\[::1\]|[\w.-]+)(:\d+)?(\/|$)/i.test(url) && !/^[a-zA-Z][a-zA-Z\d+.-]*:\/\//.test(url)) {
    const defaultProtocol = String(import.meta.env.VITE_STORAGE_URL_PROTOCOL || 'https').replace(/:$/, '')
    return `${defaultProtocol}://${url}`
  }
  if (/^[a-zA-Z][a-zA-Z\d+.-]*:/.test(url)) return url
  return url
}

export const hasObjectKeyInStorageUrl = (rawUrl: string, marker: '/read/' | '/upload/') => {
  const url = String(rawUrl || '').trim()
  if (!url) return false
  try {
    const parsed = new URL(url, window.location.origin)
    const path = parsed.pathname || ''
    const idx = path.indexOf(marker)
    if (idx < 0) return true
    return path.slice(idx + marker.length).trim().length > 0
  } catch {
    if (url.endsWith(marker)) return false
    return true
  }
}

export const resolveDirectUploadTarget = (uploadResp: Record<string, unknown>) => {
  const uploadUrl = normalizeObjectStorageUrl(String(uploadResp.uploadUrl || uploadResp.url || uploadResp.putUrl || ''))
  const objectKey = String(uploadResp.objectKey || uploadResp.key || '').trim()
  const storageProvider = String(uploadResp.storageProvider || uploadResp.provider || '').trim() || 's3'

  if (!objectKey) {
    throw new Error('上传地址响应缺少 objectKey')
  }
  if (!uploadUrl) {
    throw new Error('上传地址响应缺少 uploadUrl')
  }
  if (!hasObjectKeyInStorageUrl(uploadUrl, '/upload/')) {
    throw new Error('上传地址响应缺少 uploadUrl 对象键')
  }

  return {
    uploadUrl,
    objectKey,
    storageProvider,
  }
}
