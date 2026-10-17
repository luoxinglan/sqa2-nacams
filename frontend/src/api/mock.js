// Mock 层（开发期假数据）——按《接口规范》逐接口补示例响应；后端就绪后整体关闭（VITE_USE_MOCK=0）。
// 用法：在自己模块文件里 import { mockOn, demoUsers } from '@/api/mock' 注册 handler；
// handler 返回统一响应包 {code, message, data}；路径支持 :id 占位；未命中的请求走真实后端。
// ⚠️ mock 数据仅用于开发/演示（老师口径允许虚构演示数据，敏感字段按隐私边界处理）。

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
// 演示账号（密码即初始密码口径：管理员提供初始密码，用户登录后可修改）
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

// 站点元数据（真实环境=后端部署期配置，接口规范 §3.2 /site/info）
const SITE_INFO = {
  logoUrl: '/logo.svg',
  nameZh: '网络攻防对抗技术研究所',
  nameEn: 'Institute of Network Attack and Defense Technology',
  sysName: '网络攻防对抗技术研究所管理系统',
  copyright: '© 2026 网络攻防对抗技术研究所 · 北京理工大学 版权所有',
  links: [
    { name: '计算机学院', url: 'http://cs.bit.edu.cn' },
    { name: '研究生院', url: 'http://grd.bit.edu.cn' },
    { name: '研究生管理系统', url: 'http://gms.bit.edu.cn' },
  ],
}

const LATEST_NEWS = [
  { id: 1, category: '新闻动态', title: '研究所承办 2026 年网络空间安全青年学者论坛', author: '综合办公室', publishTime: '2026-10-16 16:30' },
  { id: 2, category: '通知通告', title: '关于组织 2026 年秋季研究生学位论文开题工作的通知', author: '教务办公室', publishTime: '2026-10-15 10:00' },
  { id: 3, category: '学术活动', title: '学术报告：大模型驱动的漏洞挖掘技术前沿', author: '复杂网络与安全研究实验室', publishTime: '2026-10-14 14:00' },
  { id: 4, category: '新闻动态', title: '研究所与行业单位签署网络安全联合研究合作协议', author: '综合办公室', publishTime: '2026-10-12 09:20' },
  { id: 5, category: '学术活动', title: '第九届网络攻防对抗技术研究生学术论坛征文通知', author: '研究生会', publishTime: '2026-10-10 15:45' },
]

const ABOUT = {
  intro: [
    '<p>北京理工大学网络攻防对抗技术研究所依托计算机学院建设，长期从事网络攻防对抗、网络靶场与安全评测、漏洞挖掘与利用、复杂网络安全等方向的科学研究与人才培养工作，承担多项国家级科研课题，并与行业单位保持密切合作。</p>',
    '<p>研究所前身复杂网络与安全研究实验室已建成对外宣传网站，积累了丰富的学术活动与宣传资料。在此基础上，《网络攻防对抗技术研究所管理系统》将师资队伍、学生信息、教学科研、研究成果等信息全面纳入数据库统一管理，为研究所对外宣传与内部管理提供一体化支撑。</p>',
    '<p>研究所现有全职教师、兼职教师与博士、硕士研究生若干，欢迎对网络空间安全感兴趣的同学报考。</p>',
  ].join(''),
  contact: [
    '<p><strong>地址：</strong>北京市海淀区中关村南大街 5 号（邮编 100081）</p>',
    '<p><strong>邮箱：</strong>nacams@bit.edu.cn</p>',
    '<p><strong>电话：</strong>010-68918482</p>',
    '<p><strong>办公时间：</strong>工作日 8:30-11:50，13:30-17:10</p>',
  ].join(''),
}

// —— 以下注册刘罡甫（首页/登录页/关于我们）所需的公开接口 mock ——

mockOn('GET', '/site/info', () => ({ code: 0, message: 'success', data: SITE_INFO }))

mockOn('GET', '/home/summary', () => ({
  code: 0,
  message: 'success',
  data: { noticeCount: 3, latestNews: LATEST_NEWS },
}))

mockOn('GET', '/about', () => ({ code: 0, message: 'success', data: ABOUT }))

mockOn('POST', '/auth/login', ({ body }) => {
  const u = demoUsers[body?.username]
  if (!u || u.password !== body?.password) {
    return { code: 401, message: '用户名或密码错误', data: null }
  }
  return {
    code: 0,
    message: 'success',
    data: { token: `mock.${body.username}.${Date.now()}`, user: u.user },
  }
})

mockOn('POST', '/auth/logout', () => ({ code: 0, message: 'success', data: null }))

mockOn('GET', '/auth/me', () => {
  const token = localStorage.getItem('sqa2.token') || ''
  const name = token.split('.')[1]
  const u = demoUsers[name]
  if (!u) return { code: 401, message: '未登录', data: null }
  return { code: 0, message: 'success', data: u.user }
})
