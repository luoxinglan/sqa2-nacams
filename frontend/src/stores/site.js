// 站点元数据（LOGO/中英文名/版权/外链）——全站头部、登录页、页脚共用，缓存一份
import { defineStore } from 'pinia'
import { getSiteInfo } from '@/api/site'

const FALLBACK = {
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

export const useSiteStore = defineStore('site', {
  state: () => ({ info: null }),
  getters: {
    site: (s) => s.info || FALLBACK,
  },
  actions: {
    async load(force = false) {
      if (this.info && !force) return this.info
      try {
        this.info = await getSiteInfo()
      } catch {
        this.info = null // 接口失败时用兜底配置，页面不白屏
      }
      return this.site
    },
  },
})
