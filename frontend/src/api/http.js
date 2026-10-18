// 统一 HTTP 请求封装（三人共用）——约定见《接口规范》§1：
// 基础路径 /api/v1；统一响应包 {code,message,data}；HTTP 状态码与 body code 同号镜像；
// code=0 → 直接返回 data；422 → 抛 ApiError（.fields 为 {字段名: 错误原因}，表单逐字段提示，不全局弹错）；
// 401 → 清登录态并跳登录页（带 redirect）；其余非 0 → ElMessage 全局提示并抛 ApiError。
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { mockRequest } from './mock'

export const TOKEN_KEY = 'sqa2.token'
export const USER_KEY = 'sqa2.user'

const instance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api/v1',
  timeout: 15000,
})

instance.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

export class ApiError extends Error {
  constructor(code, message, fields) {
    super(message || '请求失败')
    this.code = code
    this.fields = fields || {}
  }
}

function fail(body) {
  const err = new ApiError(body?.code, body?.message, body?.data?.fields)
  if (err.code === 401) {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    if (!window.location.pathname.startsWith('/login')) {
      window.location.href =
        '/login?redirect=' + encodeURIComponent(window.location.pathname + window.location.search)
    }
  } else if (err.code !== 422) {
    ElMessage.error(err.message)
  }
  return err
}

instance.interceptors.response.use(
  (resp) => {
    if (resp.config.responseType === 'blob') return resp
    const body = resp.data
    if (body && typeof body === 'object' && body.code === 0) return body.data
    return Promise.reject(fail(body))
  },
  async (err) => {
    let body = err.response?.data
    if (body instanceof Blob) {
      try { body = JSON.parse(await body.text()) } catch { body = null }
    }
    if (body && typeof body === 'object' && 'code' in body) return Promise.reject(fail(body))
    return Promise.reject(fail({ code: err.response?.status || -1, message: '网络异常或服务器无响应' }))
  }
)

// —— mock 开关：开发环境默认开启（后端未就绪时按接口规范 §4-1 对假数据并行开发）。
// 命中 mock 的请求不发网络；未命中的照常走真实后端。联调时把 .env.development 的
// VITE_USE_MOCK 改为 0 并重启 dev server。
export async function request(method, url, data, config = {}) {
  if (import.meta.env.VITE_USE_MOCK === '1' && config.responseType !== 'blob') {
    const mocked = mockRequest(method, url, { params: config.params, data })
    if (mocked) {
      await new Promise((r) => setTimeout(r, 120)) // 模拟网络延迟，避免界面假"零延迟"
      if (mocked.code === 0) return mocked.data
      throw fail(mocked)
    }
  }
  return instance.request({ method, url, data, ...config })
}

export const get = (url, params) => request('get', url, undefined, { params })
export const post = (url, data) => request('post', url, data)
export const put = (url, data) => request('put', url, data)
export const del = (url) => request('delete', url)
