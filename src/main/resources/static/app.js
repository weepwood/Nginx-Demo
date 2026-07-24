const state = {
  token: sessionStorage.getItem('nginx-demo-token') || '',
  currentPath: '',
  objectUrls: new Set(),
}

const elements = {
  loginPanel: document.querySelector('#loginPanel'),
  browserPanel: document.querySelector('#browserPanel'),
  loginForm: document.querySelector('#loginForm'),
  loginMessage: document.querySelector('#loginMessage'),
  statusMessage: document.querySelector('#statusMessage'),
  connectionBadge: document.querySelector('#connectionBadge'),
  breadcrumbs: document.querySelector('#breadcrumbs'),
  resourceGrid: document.querySelector('#resourceGrid'),
  refreshButton: document.querySelector('#refreshButton'),
  logoutButton: document.querySelector('#logoutButton'),
  directoryTemplate: document.querySelector('#directoryTemplate'),
  fileTemplate: document.querySelector('#fileTemplate'),
}

function setConnection(status, label) {
  elements.connectionBadge.textContent = label
  elements.connectionBadge.className = `badge badge-${status}`
}

function setMessage(element, message, error = false) {
  element.textContent = message
  element.classList.toggle('error', error)
}

function authorizedHeaders(extra = {}) {
  return { ...extra, Authorization: `Bearer ${state.token}` }
}

async function parseError(response) {
  try {
    const payload = await response.json()
    return payload.message || payload.error || `请求失败：${response.status}`
  }
  catch {
    return `请求失败：${response.status}`
  }
}

async function login(username, password) {
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  if (!response.ok) throw new Error(await parseError(response))
  const payload = await response.json()
  state.token = payload.token
  sessionStorage.setItem('nginx-demo-token', state.token)
}

async function fetchDirectory(path) {
  const url = new URL('/api/images', window.location.origin)
  url.searchParams.set('path', path)
  const response = await fetch(url, { headers: authorizedHeaders() })
  if (response.status === 401) {
    logout(false)
    throw new Error('登录状态已失效，请重新登录')
  }
  if (!response.ok) throw new Error(await parseError(response))
  return response.json()
}

async function fetchFileBlob(path) {
  const url = new URL('/api/images/content', window.location.origin)
  url.searchParams.set('path', path)
  const response = await fetch(url, { headers: authorizedHeaders() })
  if (!response.ok) throw new Error(await parseError(response))
  return response.blob()
}

function revokeObjectUrls() {
  for (const url of state.objectUrls) URL.revokeObjectURL(url)
  state.objectUrls.clear()
}

function formatBytes(value) {
  const bytes = Number(value || 0)
  if (!Number.isFinite(bytes) || bytes <= 0) return '未知大小'
  const units = ['B', 'KB', 'MB', 'GB']
  let amount = bytes
  let unitIndex = 0
  while (amount >= 1024 && unitIndex < units.length - 1) {
    amount /= 1024
    unitIndex += 1
  }
  return `${amount.toFixed(unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`
}

function isPreviewable(name) {
  return /\.(png|jpe?g|gif|webp|svg|bmp)$/i.test(name)
}

function renderBreadcrumbs(path) {
  elements.breadcrumbs.replaceChildren()
  const rootButton = document.createElement('button')
  rootButton.type = 'button'
  rootButton.textContent = '根目录'
  rootButton.addEventListener('click', () => loadDirectory(''))
  elements.breadcrumbs.append(rootButton)

  const segments = path.split('/').filter(Boolean)
  let accumulated = ''
  segments.forEach((segment) => {
    const separator = document.createElement('span')
    separator.className = 'breadcrumb-separator'
    separator.textContent = '/'
    elements.breadcrumbs.append(separator)
    accumulated += `${segment}/`
    const targetPath = accumulated
    const button = document.createElement('button')
    button.type = 'button'
    button.textContent = segment
    button.addEventListener('click', () => loadDirectory(targetPath))
    elements.breadcrumbs.append(button)
  })
}

async function hydrateFileCard(card, entry) {
  const placeholder = card.querySelector('.preview-placeholder')
  const image = card.querySelector('.preview-image')
  const download = card.querySelector('.download-link')
  try {
    const blob = await fetchFileBlob(entry.path)
    const objectUrl = URL.createObjectURL(blob)
    state.objectUrls.add(objectUrl)
    download.href = objectUrl
    download.download = entry.name
    if (isPreviewable(entry.name)) {
      image.alt = entry.name
      image.src = objectUrl
      image.addEventListener('load', () => {
        image.classList.add('loaded')
        placeholder.classList.add('hidden')
      }, { once: true })
    }
    else {
      placeholder.textContent = '不可预览'
    }
  }
  catch (error) {
    placeholder.textContent = error instanceof Error ? error.message : '加载失败'
  }
}

function renderEntries(entries) {
  revokeObjectUrls()
  elements.resourceGrid.replaceChildren()
  if (!Array.isArray(entries) || entries.length === 0) {
    const empty = document.createElement('p')
    empty.textContent = '当前目录为空。'
    elements.resourceGrid.append(empty)
    return
  }

  const fragment = document.createDocumentFragment()
  const filesToHydrate = []
  for (const entry of entries) {
    if (entry.directory) {
      const card = elements.directoryTemplate.content.firstElementChild.cloneNode(true)
      card.querySelector('.resource-name').textContent = entry.name
      card.addEventListener('click', () => loadDirectory(entry.path))
      fragment.append(card)
      continue
    }
    const card = elements.fileTemplate.content.firstElementChild.cloneNode(true)
    card.querySelector('.resource-name').textContent = entry.name
    card.querySelector('.resource-meta').textContent = formatBytes(entry.size)
    fragment.append(card)
    filesToHydrate.push([card, entry])
  }
  elements.resourceGrid.append(fragment)
  for (const [card, entry] of filesToHydrate) void hydrateFileCard(card, entry)
}

async function loadDirectory(path) {
  state.currentPath = path || ''
  renderBreadcrumbs(state.currentPath)
  setMessage(elements.statusMessage, '正在通过后端代理读取 Nginx 目录…')
  try {
    const entries = await fetchDirectory(state.currentPath)
    renderEntries(entries)
    setMessage(elements.statusMessage, `已加载 ${entries.length} 个项目。`)
    setConnection('success', '代理连接正常')
  }
  catch (error) {
    renderEntries([])
    setMessage(elements.statusMessage, error instanceof Error ? error.message : '目录读取失败', true)
    setConnection('error', '连接失败')
  }
}

function showBrowser() {
  elements.loginPanel.classList.add('hidden')
  elements.browserPanel.classList.remove('hidden')
}

function showLogin() {
  elements.loginPanel.classList.remove('hidden')
  elements.browserPanel.classList.add('hidden')
}

function logout(showMessage = true) {
  state.token = ''
  sessionStorage.removeItem('nginx-demo-token')
  revokeObjectUrls()
  showLogin()
  setConnection('muted', '未连接')
  if (showMessage) setMessage(elements.loginMessage, '已退出。')
}

elements.loginForm.addEventListener('submit', async (event) => {
  event.preventDefault()
  const form = new FormData(elements.loginForm)
  setMessage(elements.loginMessage, '正在登录…')
  try {
    await login(String(form.get('username') || ''), String(form.get('password') || ''))
    setMessage(elements.loginMessage, '')
    showBrowser()
    await loadDirectory('')
  }
  catch (error) {
    setMessage(elements.loginMessage, error instanceof Error ? error.message : '登录失败', true)
  }
})

elements.refreshButton.addEventListener('click', () => loadDirectory(state.currentPath))
elements.logoutButton.addEventListener('click', () => logout())
window.addEventListener('beforeunload', revokeObjectUrls)

if (state.token) {
  showBrowser()
  void loadDirectory('')
}
