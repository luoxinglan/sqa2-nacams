// 门户栏目公开接口（钟坤宇）——函数签名与最初 fetch 版一致；
// 2026-10-17 版本集成时改为走三人共用的 axios 封装（http.js）：
// 统一响应包解包/错误提示/mock 开关（VITE_USE_MOCK）均由共用层处理（分工卡 §1"三人共用一套请求封装"）。
import { get } from './http'

export function listContent(type, params = {}) {
  return get(`/${type}`, params)
}

export function getNews(id) {
  return get(`/news/${encodeURIComponent(id)}`)
}

export function listPeople(kind, params = {}) {
  return get(`/${kind}`, params)
}

export function getPerson(kind, id) {
  return get(`/${kind}/${encodeURIComponent(id)}`)
}

export function getHomeSummary() {
  return get('/home/summary')
}

export function getSiteInfo() {
  return get('/site/info')
}

export function mediaUrl(url) {
  if (!url) return ''
  if (/^https?:\/\//.test(url)) return url
  return url.startsWith('/') ? url : `/${url}`
}
