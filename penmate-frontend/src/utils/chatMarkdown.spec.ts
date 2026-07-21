import { describe, expect, it } from 'vitest'
import { renderChatMarkdown } from './chatMarkdown'

describe('renderChatMarkdown', () => {
  it('renders completed chat markdown and strips unsafe html', () => {
    const html = renderChatMarkdown('# 标题\n\n- 条目\n\n<script>alert(1)</script>')
    expect(html).toContain('<h1>标题</h1>')
    expect(html).toContain('<li>条目</li>')
    expect(html).not.toContain('<script>')
  })

  it('keeps the unfinished streaming tail as plain text', () => {
    const html = renderChatMarkdown('**已完成**\n\n```ts\nconst value = 1', true)
    expect(html).toContain('<strong>已完成</strong>')
    expect(html).toContain('```ts')
    expect(html).not.toContain('<code class="language-ts">')
  })

  it('does not render model-provided images', () => {
    const html = renderChatMarkdown('![tracking](https://example.com/pixel.png)')
    expect(html).not.toContain('<img')
    expect(html).toContain('tracking')
  })
})
