import DOMPurify from 'dompurify'
import { marked } from 'marked'

const renderer = new marked.Renderer()

interface RenderMarkdownOptions {
  streaming?: boolean
}

renderer.link = ({ href, title, tokens }) => {
  const text = tokens.map(token => token.raw).join('')
  const safeHref = href || '#'
  const titleAttr = title ? ` title="${title}"` : ''
  return `<a href="${safeHref}" target="_blank" rel="noopener noreferrer"${titleAttr}>${text}</a>`
}

marked.setOptions({
  gfm: true,
  breaks: true
})

function normalizeLooseMarkdownBlocks(content: string): string {
  const lines = content.split('\n')
  let openFence: string | null = null

  return lines.map(line => {
    const fenceMatch = line.match(/^\s{0,3}(`{3,}|~{3,})/)
    if (fenceMatch) {
      const fence = fenceMatch[1] || ''
      if (!openFence) {
        openFence = fence
      } else if (fence[0] === openFence[0] && fence.length >= openFence.length) {
        openFence = null
      }
      return line
    }

    if (openFence) return line

    let normalized = line.replace(
      /^(\s{0,3})(#{1,6})(?=[0-9A-Za-z\u4e00-\u9fff])(.+)$/,
      '$1$2 $3'
    )
    normalized = normalized.replace(
      /^(\s{0,3})(\d{1,3}[.)])(?=[^\s\d])(.+)$/,
      '$1$2 $3'
    )
    normalized = normalized.replace(
      /^(\s{0,3})([-+]\s+\[[ xX]\])(?=\S)(.+)$/,
      '$1$2 $3'
    )
    normalized = normalized.replace(
      /^(\s{0,3})([-+])(?=[\u4e00-\u9fff])(.+)$/,
      '$1$2 $3'
    )
    return normalized
  }).join('\n')
}

function findUnclosedFence(content: string): string | null {
  const fencePattern = /(^|\n)(`{3,}|~{3,})[^\n]*/g
  let openFence: string | null = null
  let match: RegExpExecArray | null

  while ((match = fencePattern.exec(content)) !== null) {
    const fence = match[2] || ''
    if (!openFence) {
      openFence = fence
      continue
    }

    if (fence[0] === openFence[0] && fence.length >= openFence.length) {
      openFence = null
    }
  }

  return openFence
}

function stabilizeStreamingMarkdown(content: string): string {
  const openFence = findUnclosedFence(content)
  if (!openFence) return content
  return `${content}${content.endsWith('\n') ? '' : '\n'}${openFence}`
}

function prepareMarkdownSource(content: string, options: RenderMarkdownOptions): string {
  const normalized = normalizeLooseMarkdownBlocks(content)
  return options.streaming ? stabilizeStreamingMarkdown(normalized) : normalized
}

export function renderMarkdown(content: string, options: RenderMarkdownOptions = {}): string {
  if (!content) {
    return ''
  }

  const source = prepareMarkdownSource(content, options)
  const html = marked.parse(source, {
    async: false,
    renderer
  })

  return DOMPurify.sanitize(html, {
    USE_PROFILES: { html: true }
  })
}
