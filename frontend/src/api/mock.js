// Mock 层（开发期假数据）——按《接口规范 v1.0》逐接口补示例响应；后端就绪后整体关闭（VITE_USE_MOCK=0）。
// 用法：在自己模块文件里 import { mockOn, demoUsers } from '@/api/mock' 注册 handler；
// handler 返回统一响应包 {code, message, data}；路径支持 :id 占位；未命中的请求走真实后端。
// ⚠️ 字段口径与已冻结的后端实现对齐（PortalController）：/site/info={name,englishName,logo,copyright,
// links{cs,grd,gms}}；/home/summary={news:[...],notificationCount}；列表={items,total,page,page_size}；
// 内容条目={id,type,category,title,body,author,publishedAt}。新增 mock 先对后端实现，不要自造字段。

const handlers = []

export function mockOn(method, path, handler) {
  handlers.push({ method: method.toUpperCase(), path, handler })
}

function matchPath(pattern, path) {
  if (pattern instanceof RegExp) return pattern.test(path) ? {} : null
  if (pattern === path) return {}
  const p = String(pattern).split('/')
  const q = path.split('/')
  if (p.length !== q.length) return null
  const params = {}
  for (let i = 0; i < p.length; i++) {
    if (p[i].startsWith(':')) params[p[i].slice(1)] = decodeURIComponent(q[i])
    else if (p[i] !== q[i]) return null
  }
  return params
}

export function mockRequest(method, url, { params, data } = {}) {
  const path = String(url).split('?')[0]
  for (const h of handlers) {
    if (h.method !== method.toUpperCase()) continue
    const m = matchPath(h.path, path)
    if (m) return h.handler({ params: params || {}, body: data, pathParams: m })
  }
  return null
}

// ---------------------------------------------------------------------------
// 演示账号（密码=初始密码口径：管理员提供初始密码，用户登录后可修改）
export const demoUsers = {
  admin: {
    password: 'Admin@123',
    user: { id: 1, username: 'admin', name: '系统管理员', role: 'admin', isDirector: false, isLibrarian: false },
  },
  director: {
    password: 'Bit@123456',
    user: { id: 2, username: 'director', name: '闫怀志', role: 'user', isDirector: true, isLibrarian: false },
  },
  librarian: {
    password: 'Bit@123456',
    user: { id: 3, username: 'librarian', name: '陈老师', role: 'user', isDirector: false, isLibrarian: true },
  },
  teacher: {
    password: 'Bit@123456',
    user: { id: 4, username: 'teacher', name: '李老师', role: 'user', isDirector: false, isLibrarian: false },
  },
  student: {
    password: 'Bit@123456',
    user: { id: 5, username: 'student', name: '张同学', role: 'user', isDirector: false, isLibrarian: false },
  },
}

// —— 站点元数据（真实环境=后端部署期配置 app.site.*）——
const SITE_INFO = {
  name: '网络攻防对抗技术研究所',
  englishName: 'Institute of Network Attack and Defense Technology',
  logo: '/logo.svg',
  copyright: '© 2026 网络攻防对抗技术研究所 · 北京理工大学 版权所有',
  links: { cs: 'https://cs.bit.edu.cn', grd: 'https://grd.bit.edu.cn', gms: 'https://gms.bit.edu.cn' },
}

// —— 动态消息（含正文/媒体，供列表与详情共用）——
const NEWS = [
  {
    id: 1, type: 'news', category: '新闻动态', title: '研究所承办 2026 年网络空间安全青年学者论坛',
    author: '综合办公室', publishedAt: '2026-10-16 16:30',
    body: '<p>2026 年网络空间安全青年学者论坛在我校成功举办。本届论坛以"智能时代的网络攻防"为主题，来自全国高校与科研院所的青年学者围绕人工智能安全、漏洞挖掘、网络靶场等方向进行了深入交流。</p>',
    images: [], videos: [], attachments: [],
  },
  {
    id: 2, type: 'news', category: '通知通告', title: '关于组织 2026 年秋季研究生学位论文开题工作的通知',
    author: '教务办公室', publishedAt: '2026-10-15 10:00',
    body: '<p>请各培养单位按学校研究生院安排，组织 2026 年秋季学期研究生学位论文开题工作，并于规定时间前在系统中提交开题材料。</p>',
    images: [], videos: [], attachments: [],
  },
  {
    id: 3, type: 'news', category: '学术活动', title: '学术报告：大模型驱动的漏洞挖掘技术前沿',
    author: '复杂网络与安全研究实验室', publishedAt: '2026-10-14 14:00',
    body: '<p>本期学术报告邀请领域专家介绍大语言模型辅助漏洞挖掘的最新进展，并讨论其在攻防对抗场景中的应用前景。</p>',
    images: [], videos: [], attachments: [],
  },
  {
    id: 4, type: 'news', category: '新闻动态', title: '研究所与行业单位签署网络安全联合研究合作协议',
    author: '综合办公室', publishedAt: '2026-10-12 09:20',
    body: '<p>研究所与多家行业单位签署联合研究合作协议，将在网络靶场建设与安全评测领域开展长期合作。</p>',
    images: [], videos: [], attachments: [],
  },
  {
    id: 5, type: 'news', category: '学术活动', title: '第九届网络攻防对抗技术研究生学术论坛征文通知',
    author: '研究生会', publishedAt: '2026-10-10 15:45',
    body: '<p>第九届网络攻防对抗技术研究生学术论坛现面向全体研究生征集论文，欢迎踊跃投稿。</p>',
    images: [], videos: [], attachments: [],
  },
]

const ABOUT_ITEMS = [
  {
    id: 101, type: 'about', category: '研究所简介', title: '研究所简介', author: '综合办公室', publishedAt: '2026-10-01 09:00',
    body: [
      '<p>北京理工大学网络攻防对抗技术研究所依托计算机学院建设，长期从事网络攻防对抗、网络靶场与安全评测、漏洞挖掘与利用、复杂网络安全等方向的科学研究与人才培养工作，承担多项国家级科研课题，并与行业单位保持密切合作。</p>',
      '<p>研究所前身复杂网络与安全研究实验室已建成对外宣传网站，积累了丰富的学术活动与宣传资料。在此基础上，《网络攻防对抗技术研究所管理系统》将师资队伍、学生信息、教学科研、研究成果等信息全面纳入数据库统一管理，为研究所对外宣传与内部管理提供一体化支撑。</p>',
      '<p>研究所现有全职教师、兼职教师与博士、硕士研究生若干，欢迎对网络空间安全感兴趣的同学报考。</p>',
    ].join(''),
  },
  {
    id: 102, type: 'about', category: '联系我们', title: '联系我们', author: '综合办公室', publishedAt: '2026-10-01 09:00',
    body: [
      '<p><strong>地址：</strong>北京市海淀区中关村南大街 5 号（邮编 100081）</p>',
      '<p><strong>邮箱：</strong>nacams@bit.edu.cn</p>',
      '<p><strong>电话：</strong>010-68918482</p>',
      '<p><strong>办公时间：</strong>工作日 8:30-11:50，13:30-17:10</p>',
    ].join(''),
  },
]

// —— 师生公开档案（B-07：只含可公开字段，敏感字段由服务端脱敏）——
const TEACHERS = [
  { id: 2, identityType: 'teacher', name: '闫怀志', teacherType: 'full', title: '正高', email: 'yanhz@bit.edu.cn', photo: '', 研究所职务: '所长', 导师资格: '博导' },
  { id: 4, identityType: 'teacher', name: '李老师', teacherType: 'full', title: '副高', email: 'liteach@bit.edu.cn', photo: '', 导师资格: '硕导' },
  { id: 6, identityType: 'teacher', name: '王教授', teacherType: 'external', title: '正高', email: 'wangprof@example.com', photo: '', 工作单位: '某网络安全企业' },
]
const STUDENTS = [
  { id: 5, identityType: 'student', name: '张同学', status: '在读', studentNo: '3220240001', category: '学术硕士', email: 'zhang@bit.edu.cn', photo: '' },
  { id: 7, identityType: 'student', name: '刘同学', status: '在读', studentNo: '3220250018', category: '专业硕士', email: 'liu@bit.edu.cn', photo: '' },
  { id: 8, identityType: 'student', name: '赵博士', status: '毕业', studentNo: '3120210066', category: '工学博士', email: 'zhao@bit.edu.cn', photo: '', 毕业时间: '2026-06', 初次就业单位: '某研究院' },
]

const CONTENT = {
  courses: [
    { id: 1, type: 'courses', category: '课程', title: '网络攻防对抗技术', author: '闫怀志', publishedAt: '2026-09-01 08:00', body: '面向硕士研究生的专业核心课，讲授网络攻防基本原理与对抗技术。', 课程编号: 'CS2610Z', 课程性质: '专业核心课', 学生层次: '硕士研究生', 主讲教师: '闫怀志' },
    { id: 2, type: 'courses', category: '课程', title: '网络安全评测与靶场技术', author: '李老师', publishedAt: '2026-09-01 08:00', body: '介绍网络安全评测方法与网络靶场的设计实现。', 课程编号: 'CS2611Z', 课程性质: '专业选修课', 学生层次: '硕士研究生', 主讲教师: '李老师' },
  ],
  projects: [
    { id: 1, type: 'projects', category: '科研项目', title: '网络攻防对抗关键技术研究', author: '闫怀志', publishedAt: '2026-06-01 08:00', body: '国家级科研项目，研究网络攻防对抗的关键技术与应用。', 项目性质: '国家级' },
  ],
  achievements: [
    { id: 1, type: 'achievements', category: '科研成果', title: '网络靶场安全评测平台', author: '研究所', publishedAt: '2026-05-01 08:00', body: '', 层次: '省部级' },
    { id: 2, type: 'achievements', category: '专利', title: '一种网络攻击路径生成方法', author: '闫怀志 等', publishedAt: '2026-04-01 08:00', body: '', applicationNo: '202610123456.7' },
    { id: 3, type: 'achievements', category: '教学论文', title: '攻防对抗实践课程教学改革探索', author: '李老师', publishedAt: '2026-03-01 08:00', body: '', 期刊名称: '计算机教育', 刊号: '1672-5913' },
  ],
  admissions: [
    { id: 1, type: 'admissions', category: '硕士', title: '硕士研究生招生', author: '研究生秘书', publishedAt: '2026-09-10 08:00', body: '', 技术要求: '具备计算机网络与程序设计基础', 联系人: '闫怀志', 联系方式: 'nacams@bit.edu.cn' },
    { id: 2, type: 'admissions', category: '博士', title: '博士研究生招生', author: '研究生秘书', publishedAt: '2026-09-10 08:00', body: '', 技术要求: '网络安全方向科研经历', 联系人: '闫怀志', 联系方式: 'nacams@bit.edu.cn' },
  ],
  cooperations: [
    { id: 1, type: 'cooperations', category: '国际合作', title: '某国外大学', author: '', publishedAt: '', body: '网络空间安全联合培养项目', country: '新加坡' },
    { id: 2, type: 'cooperations', category: '国际合作', title: '某网络安全企业', author: '', publishedAt: '', body: '联合实验室与实习基地建设项目', country: '中国' },
  ],
}

function page(items, params) {
  const p = Number(params.page) || 1
  const size = Number(params.page_size) || 20
  return { items: items.slice((p - 1) * size, p * size), total: items.length, page: p, page_size: size }
}

function filterByKeyword(items, params, ...fields) {
  const kw = String(params.keyword || '').trim()
  if (!kw) return items
  return items.filter((it) => fields.some((f) => String(it[f] || '').includes(kw)))
}

// —— 认证 ——
mockOn('POST', '/auth/login', ({ body }) => {
  const u = demoUsers[body?.username]
  if (!u || u.password !== body?.password) {
    return { code: 401, message: '用户名或密码错误', data: null }
  }
  return { code: 0, message: 'success', data: { token: `mock.${body.username}.${Date.now()}`, user: u.user } }
})
mockOn('POST', '/auth/logout', () => ({ code: 0, message: 'success', data: null }))
mockOn('GET', '/auth/me', () => {
  const token = localStorage.getItem('sqa2.token') || ''
  const u = demoUsers[token.split('.')[1]]
  if (!u) return { code: 401, message: '未登录', data: null }
  return { code: 0, message: 'success', data: u.user }
})

// —— 站点与首页（刘罡甫页面）——
mockOn('GET', '/site/info', () => ({ code: 0, message: 'success', data: SITE_INFO }))
mockOn('GET', '/home/summary', () => ({
  code: 0,
  message: 'success',
  data: { news: NEWS.slice(0, 5), notificationCount: NEWS.filter((n) => n.category === '通知通告').length },
}))
mockOn('GET', '/about', (ctx) => ({
  code: 0,
  message: 'success',
  data: page(filterByKeyword(ABOUT_ITEMS, ctx.params, 'title', 'body'), ctx.params),
}))

// —— 门户栏目（钟坤宇页面）——
mockOn('GET', '/news', (ctx) => {
  let items = NEWS
  if (ctx.params.category) items = items.filter((n) => n.category === ctx.params.category)
  return { code: 0, message: 'success', data: page(filterByKeyword(items, ctx.params, 'title', 'body'), ctx.params) }
})
mockOn('GET', '/news/:id', ({ pathParams }) => {
  const item = NEWS.find((n) => String(n.id) === String(pathParams.id))
  return item ? { code: 0, message: 'success', data: item } : { code: 404, message: '动态不存在', data: null }
})

mockOn('GET', '/teachers', (ctx) => {
  let items = TEACHERS
  if (ctx.params.type === '全职') items = items.filter((t) => t.teacherType === 'full')
  if (ctx.params.type === '兼职') items = items.filter((t) => t.teacherType === 'external')
  return { code: 0, message: 'success', data: page(filterByKeyword(items, ctx.params, 'name'), ctx.params) }
})
mockOn('GET', '/teachers/:id', ({ pathParams }) => {
  const item = TEACHERS.find((t) => String(t.id) === String(pathParams.id))
  return item ? { code: 0, message: 'success', data: item } : { code: 404, message: '公开档案不存在', data: null }
})
mockOn('GET', '/students', (ctx) => {
  let items = STUDENTS
  if (ctx.params.status) items = items.filter((s) => s.status === ctx.params.status)
  if (ctx.params.year) items = items.filter((s) => (s.studentNo || '').slice(3, 7) === String(ctx.params.year))
  return { code: 0, message: 'success', data: page(filterByKeyword(items, ctx.params, 'name', 'studentNo'), ctx.params) }
})
mockOn('GET', '/students/:id', ({ pathParams }) => {
  const item = STUDENTS.find((s) => String(s.id) === String(pathParams.id))
  return item ? { code: 0, message: 'success', data: item } : { code: 404, message: '公开档案不存在', data: null }
})

for (const type of ['courses', 'projects', 'achievements', 'admissions', 'cooperations']) {
  mockOn('GET', `/${type}`, (ctx) => {
    let items = CONTENT[type]
    const filterKey = type === 'achievements' ? 'type' : type === 'admissions' ? 'level' : 'category'
    if (ctx.params[filterKey]) items = items.filter((it) => it.category === ctx.params[filterKey])
    return { code: 0, message: 'success', data: page(filterByKeyword(items, ctx.params, 'title', 'body'), ctx.params) }
  })
}
