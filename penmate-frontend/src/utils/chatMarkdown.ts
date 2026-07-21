import DOMPurify from 'dompurify'
import MarkdownIt from 'markdown-it'

const markdown = new MarkdownIt({
  html: false,
  breaks: true,
  linkify: true,
  typographer: false,
})

const defaultLinkOpen = markdown.renderer.rules.link_open
markdown.renderer.rules.link_open = (tokens, index, options, env, self) => {
  tokens[index]?.attrSet('target', '_blank')
  tokens[index]?.attrSet('rel', 'noopener noreferrer nofollow')
  return defaultLinkOpen ? defaultLinkOpen(tokens, index, options, env, self) : self.renderToken(tokens, index, options)
}

markdown.renderer.rules.image = (tokens, index) => markdown.utils.escapeHtml(tokens[index]?.content || '')

const stableMarkdownBoundary = (source: string) => {
  let inFence = false
  let offset = 0
  let stableOffset = 0
  for (const line of source.split(/(?<=\n)/)) {
    const normalized = line.trimStart()
    if (normalized.startsWith('```') || normalized.startsWith('~~~')) inFence = !inFence
    offset += line.length
    if (!inFence && line.trim() === '') stableOffset = offset
  }
  return stableOffset
}

const renderPlainTail = (value: string) => {
  if (!value) return ''
  return `<span class="markdown-stream-tail">${markdown.utils.escapeHtml(value).replace(/\n/g, '<br>')}</span>`
}

export const renderChatMarkdown = (source: string, streaming = false) => {
  const normalized = String(source || '')
  const boundary = streaming ? stableMarkdownBoundary(normalized) : normalized.length
  const stable = normalized.slice(0, boundary)
  const tail = normalized.slice(boundary)
  const rendered = `${stable ? markdown.render(stable) : ''}${renderPlainTail(tail)}`
  return DOMPurify.sanitize(rendered, {
    USE_PROFILES: { html: true },
    FORBID_TAGS: ['img', 'style'],
    FORBID_ATTR: ['style'],
  })
}
