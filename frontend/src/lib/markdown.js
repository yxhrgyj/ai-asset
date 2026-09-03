import { marked } from 'marked'
import DOMPurify from 'dompurify'

/**
 * Markdown 渲染。
 *
 * 必须过一遍 DOMPurify：正文由使用者自己写，marked 默认允许内联 HTML，
 * 不清洗等于让任何有编写权限的人在所有查看者的浏览器里执行脚本
 * （存储型 XSS）。这里是同源应用且带会话 Cookie，代价是会话被盗。
 *
 * 保留 HTML 的原因：提示词里常有 <thinking> 之类的标签示例，
 * 直接禁掉 HTML 会让这类内容显示错乱。清洗比禁用更合适。
 */
marked.setOptions({
  gfm: true,
  breaks: false
})

const PURIFY_CONFIG = {
  // 禁掉表单与嵌入类标签：正文里用不到，却是最常见的钓鱼与点击劫持载体。
  FORBID_TAGS: ['form', 'input', 'button', 'iframe', 'object', 'embed', 'style'],
  FORBID_ATTR: ['style', 'srcset'],
  // target="_blank" 需要，但必须补 rel 防 tabnabbing（见下面的钩子）。
  ADD_ATTR: ['target', 'rel']
}

let hookInstalled = false

function installHook() {
  if (hookInstalled) return
  DOMPurify.addHook('afterSanitizeAttributes', (node) => {
    if (node.tagName === 'A' && node.getAttribute('target') === '_blank') {
      node.setAttribute('rel', 'noopener noreferrer')
    }
  })
  hookInstalled = true
}

export function renderMarkdown(text) {
  if (!text) return ''
  installHook()
  return DOMPurify.sanitize(marked.parse(text), PURIFY_CONFIG)
}
