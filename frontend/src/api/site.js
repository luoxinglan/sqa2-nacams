// 站点与门户公开接口（后端：韦政治）——接口规范 §3.2
import { get } from './http'

// 站点元数据：LOGO、中英文名、版权、外链（部署期配置）
export const getSiteInfo = () => get('/site/info')

// 首页聚合：最新动态×5 + 通知数量（首页动态实时更新轮询用）
export const getHomeSummary = () => get('/home/summary')

// 研究所简介、联系我们（后台可更新的文字内容，HTML 字符串）
export const getAbout = () => get('/about')
