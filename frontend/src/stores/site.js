// 站点元数据（LOGO/中英文名/版权/外链）——全站头部、登录页、页脚共用，缓存一份。
// 后端 GET /site/info 返回 {name, englishName, logo, copyright, links:{cs,grd,gms}}（部署期配置），
// 此处归一化为组件友好结构；接口失败时用兜底配置，页面不白屏。
import { defineStore } from 'pinia'
import { getSiteInfo } from '@/api/site'

const FALLBACK = {
  name: '网络攻防对抗技术研究所',
  englishName: 'Institute of Network Attack and Defense Technology',
  logo: '/logo.svg',
  copyright: '© 2026 网络攻防对抗技术研究所 · 北京理工大学 版权所有',
  links: { cs: 'https://cs.bit.edu.cn', grd: 'https://grd.bit.edu.cn', gms: 'https://gms.bit.edu.cn' },
}

const LINK_LABELS = { cs: '计算机学院', grd: '研究生院', gms: '研究生管理系统' }

function normalize(info) {
  const raw = { ...FALLBACK, ...(info || {}) }
  return {
    logoUrl: raw.logo || FALLBACK.logo,
    nameZh: raw.name || FALLBACK.name,
    nameEn: raw.englishName || FALLBACK.englishName,
    sysName: `${raw.name || FALLBACK.name}管理系统`, // 登录页 C-03"系统名"
    copyright: raw.copyright || FALLBACK.copyright,
    links: Object.entries(raw.links || FALLBACK.links).map(([key, url]) => ({
      key,
      name: LINK_LABELS[key] || key,
      url,
    })),
  }
}

export const useSiteStore = defineStore('site', {
  state: () => ({ info: null }),
  getters: {
    site: (s) => normalize(s.info),
  },
  actions: {
    async load(force = false) {
      if (this.info && !force) return this.site
      try {
        this.info = await getSiteInfo()
      } catch {
        this.info = null
      }
      return this.site
    },
  },
})
