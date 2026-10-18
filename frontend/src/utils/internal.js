// 内部端的展示与权限策略；服务端仍是最终权限、业务规则与字段校验的权威。
export const DOWNLOAD_CATEGORIES = [
  '开题',
  '中期',
  '答辩',
  '学位论文',
  '专利',
  '会议',
  '上级文件',
  '其他',
]
export const CONTENT_TYPES = {
  news: '动态消息',
  achievements: '研究成果',
  courses: '课程信息',
  projects: '科研项目',
  admissions: '招生信息',
  cooperations: '国际合作',
  about: '关于我们',
}
export const CONTENT_CATEGORIES = {
  news: ['新闻动态', '通知通告', '学术活动'],
  achievements: [
    '教学成果',
    '教学论文',
    '教材',
    '教改',
    '科研成果',
    '科研论文',
    '专利',
    '著作权',
  ],
  admissions: ['本科', '硕士', '博士', '留学生'],
}
export const isFullTeacher = (u) =>
  u?.identityType === 'teacher' && u?.teacherType === 'full'
export function canAccess(user, access) {
  if (!user) return false
  if (access === 'admin') return user.role === 'admin'
  if (access === 'librarian') return user.isLibrarian === true
  if (access === 'statistics')
    return user.isDirector === true || isFullTeacher(user)
  return true
}
export function safeRedirect(value, fallback = '/me') {
  return typeof value === 'string' &&
    value.startsWith('/') &&
    !value.startsWith('//') &&
    !/[\\\u0000-\u001f]/.test(value) &&
    !value.startsWith('/login')
    ? value
    : fallback
}
export const displayTime = (value) =>
  value ? String(value).replace('T', ' ').slice(0, 16) : '—'
export function fileSize(value) {
  if (value == null) return '—'
  return value < 1024
    ? `${value} B`
    : value < 1048576
      ? `${(value / 1024).toFixed(1)} KB`
      : `${(value / 1048576).toFixed(1)} MB`
}
export function downloadName(header, fallback) {
  let name = fallback
  const encoded = header?.match(/filename\*=UTF-8''([^;]+)/i)
  const plain = header?.match(/filename="([^"]+)"|filename=([^;]+)/i)
  try {
    name = encoded
      ? decodeURIComponent(encoded[1])
      : plain
        ? (plain[1] || plain[2]).trim()
        : fallback
  } catch {
    /* 使用调用方提供的文件名 */
  }
  return name.replace(/[\\/\u0000-\u001f]/g, '_')
}
export const requestStatus = (status) =>
  ({
    pending: '待审核',
    approved: '已批准',
    rejected: '已驳回',
    confirmed: '已确认',
  })[status] ||
  status ||
  '—'
