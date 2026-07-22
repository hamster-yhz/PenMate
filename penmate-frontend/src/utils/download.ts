const decodeFileName = (contentDisposition: string) => {
  const encoded = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  if (encoded) {
    try {
      return decodeURIComponent(encoded.replace(/^"|"$/g, ''))
    } catch {
      // Fall through to the basic filename form.
    }
  }
  return contentDisposition.match(/filename="([^"]+)"/i)?.[1] || ''
}

export const saveDownload = (blob: Blob, contentDisposition: string, fallbackFileName: string) => {
  const fileName = decodeFileName(contentDisposition) || fallbackFileName
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  anchor.hidden = true
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 0)
}
