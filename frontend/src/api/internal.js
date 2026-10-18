import { get, post, put, del, request } from './http'
import { downloadName } from '@/utils/internal'

export const profile = () => get('/auth/me')
export const saveProfile = (data) => put('/me/profile', data)
export const changePassword = (data) => put('/me/password', data)
export const listBooks = (params) => get('/books', params)
export const borrowBook = (id) => post(`/books/${id}/borrow-requests`, {})
export const myBorrows = (params) => get('/me/borrows', params)
export const myViolations = (params) => get('/me/violations', params)
export const notifications = (params) => get('/notifications', params)
export const readNotification = (id) => put(`/notifications/${id}/read`)
export const downloads = (params) => get('/downloads', params)
export const help = (keyword) => get('/help', { keyword })
export const signinStats = (params) => get('/stats/signin', params)
export const users = (params) => get('/admin/users', params)
export const saveUser = (id, data) =>
  id ? put(`/admin/users/${id}`, data) : post('/admin/users', data)
export const deleteUser = (id) => del(`/admin/users/${id}`)
export const content = (type, params) => get(`/admin/content/${type}`, params)
export const saveContent = (type, id, data) =>
  id
    ? put(`/admin/content/${type}/${id}`, data)
    : post(`/admin/content/${type}`, data)
export const deleteContent = (type, id) => del(`/admin/content/${type}/${id}`)
export const saveBook = (id, data) =>
  id ? put(`/admin/books/${id}`, data) : post('/admin/books', data)
export const deleteBook = (id) => del(`/admin/books/${id}`)
export const borrowRequests = (params) => get('/admin/borrow-requests', params)
export const reviewRequest = (id, approved, reason = '') =>
  post(`/admin/borrow-requests/${id}/${approved ? 'approve' : 'reject'}`, {
    reason,
  })
export const borrows = (params) => get('/admin/borrows', params)
export const returnBook = (id) => post(`/admin/borrows/${id}/return`)
export const libraryConfig = () => get('/admin/library/config')
export const saveLibraryConfig = (data) => put('/admin/library/config', data)
export const violations = (params) => get('/admin/violations', params)
export const confirmViolation = (id) => post('/admin/violations', { id })
export const backups = () => get('/admin/backups')
export const createBackup = () => post('/admin/backup')
export const deleteDownload = (id) => del(`/admin/downloads/${id}`)

export function multipart(values) {
  const form = new FormData()
  for (const [key, value] of Object.entries(values)) {
    if (value !== undefined && value !== null && value !== '')
      form.append(key, value)
  }
  return form
}
export const upload = (path, file, extra = {}) =>
  post(path, multipart({ ...extra, file }))
export const checkPhoto = (file) => upload('/photos/check', file)
export const savePhoto = (file) => upload('/me/photo', file)
export const saveDownload = (id, data) =>
  request(
    id ? 'put' : 'post',
    `/admin/downloads${id ? `/${id}` : ''}`,
    multipart(data),
  )
export async function downloadFile(path, fallback) {
  const response = await request('get', path, undefined, {
    responseType: 'blob',
  })
  const url = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.download = downloadName(
    response.headers['content-disposition'],
    fallback,
  )
  document.body.append(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
