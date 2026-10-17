const API_BASE = import.meta.env.VITE_API_BASE || '/api/v1'

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: { Accept: 'application/json', ...(options.headers || {}) },
    ...options
  })
  let body
  try {
    body = await response.json()
  } catch {
    throw new Error(`服务器返回了无法识别的响应（HTTP ${response.status}）`)
  }
  if (!response.ok || body.code !== 0) {
    throw new Error(body.message || `请求失败（HTTP ${response.status}）`)
  }
  return body.data
}

export function listContent(type, params = {}) {
  const query = new URLSearchParams(params)
  return request(`/${type}?${query.toString()}`)
}

export function getNews(id) {
  return request(`/news/${encodeURIComponent(id)}`)
}

export function listPeople(kind, params = {}) {
  const query = new URLSearchParams(params)
  return request(`/${kind}?${query.toString()}`)
}

export function getPerson(kind, id) {
  return request(`/${kind}/${encodeURIComponent(id)}`)
}

export function getHomeSummary() {
  return request('/home/summary')
}

export function getSiteInfo() {
  return request('/site/info')
}

export function mediaUrl(url) {
  if (!url) return ''
  if (/^https?:\/\//.test(url)) return url
  return url.startsWith('/') ? url : `/${url}`
}
