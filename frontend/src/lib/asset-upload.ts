export const MAX_UPLOAD_BYTES = 10 * 1024 * 1024

const textExtensions = 'md txt csv json yaml yml toml ini conf xml properties editorconfig sh bash zsh ps1 bat cmd py rb pl lua js mjs cjs ts tsx jsx vue java kt go rs c h cpp hpp cs php scala swift sql r tpl tmpl j2 mustache hbs patch diff'
const extensions = new Set([...textExtensions.split(' '), 'zip', 'png', 'jpg', 'jpeg', 'gif', 'webp', 'pdf'])
export const UPLOAD_ACCEPT = [...extensions].map(extension => `.${extension}`).concat('.example').join(',')
export const UPLOAD_HINT = 'ZIP、PNG/JPG/GIF/WebP、PDF、文本及源码；单个文件不超过 10 MB。'

export interface PendingUpload { file: File; relativePath: string }

export function validateUpload(file: Pick<File, 'name' | 'size'>, relativePath = file.name): string {
  if (!file.size) return `${file.name}：文件为空`
  if (file.size > MAX_UPLOAD_BYTES) return `${file.name}：单个文件不能超过 10 MB`
  const path = relativePath.trim() ? relativePath.replace(/\\/g, '/') : file.name
  const parts = path.split('/')
  if (path.length > 400 || parts.length > 4 || parts.some(part =>
    !part.trim() || part.startsWith('.') || /[. ]$/.test(part) || part.length > 200 ||
    /[\x00-\x1f\x7f<>:"|?*\u202a-\u202e\u2066-\u2069]/.test(part) ||
    /^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\..*)?$/i.test(part))) {
    return `${file.name}：请使用不超过 4 层的有效相对路径`
  }
  const name = parts[parts.length - 1].toLowerCase()
  const extension = name.includes('.') ? name.slice(name.lastIndexOf('.') + 1) : ''
  if (name !== 'env.example' && !extensions.has(extension)) return `${file.name}：不支持此文件类型`
  return ''
}

export function validateUploads(items: PendingUpload[]): string {
  const paths = new Set<string>()
  for (const item of items) {
    const error = validateUpload(item.file, item.relativePath)
    if (error) return error
    const path = (item.relativePath.trim() ? item.relativePath : item.file.name).replace(/\\/g, '/').toLowerCase()
    if (paths.has(path)) return `${item.file.name}：存在重复的附件路径`
    paths.add(path)
  }
  return ''
}
